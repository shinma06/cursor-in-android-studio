#!/usr/bin/env python3
"""Issue #547 offline checks and exportable probe; Python never starts a real SDK agent."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess

SDK_VERSION = '1.0.31'

# One module keeps checks and the dormant approved-only driver identical. The Python/offline
# path never imports the SDK; no installer, ambient credential lookup or product adapter.
NODE_PROBE = r'''
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import {createHash, randomUUID} from 'node:crypto';
import {pathToFileURL} from 'node:url';

export const initialText = 'Output integers from 1 to 100, one per line, then FIXTURE_INITIAL.';
export function textOnlyOptions({apiKey, model, cwd, store}) {
  if (!apiKey?.trim() || !model?.id || !cwd || !store) throw Error('Explicit probe options required');
  return {apiKey, model, tools: [], mcpServers: {}, agents: {}, local: {
    cwd, store, settingSources: [], customTools: {}, enableAgentRetries: false,
    sandboxOptions: {enabled: true},
  }};
}
// Only an explicitly authorized future caller may supply the real SDK here.
export async function startTextOnly(Agent, options) {
  const agent = await Agent.create(textOnlyOptions(options));
  try { return {agent, run: await agent.send(initialText)}; }
  catch { await agent[Symbol.asyncDispose](); throw Error('Initial submission outcome unknown'); }
}
export function outbox(owner, run, text = 'Finish with FIXTURE_STEER instead.') {
  return {owner, run, draft: {text, revision: 0}, rows: [], pending: false, blocked: false, closed: false};
}
export async function steerOnce(state, run, ticket, timeoutMs = 1000) {
  const captured = {...ticket};
  const ownsInput = () => state.run === run && state.owner === captured.owner &&
    run.agentId === captured.agentId && run.id === captured.runId &&
    state.draft.text === captured.text && state.draft.revision === captured.revision;
  if (state.closed || state.blocked || state.pending || !ownsInput() ||
      typeof ticket.id !== 'string' || !ticket.id || state.rows.some(row => row.id === ticket.id) ||
      typeof ticket.text !== 'string' || !ticket.text.trim() || Buffer.byteLength(ticket.text) > 8192 ||
      !Number.isSafeInteger(ticket.revision) || ticket.revision < 0 ||
      !Number.isInteger(timeoutMs) || timeoutMs < 1 || timeoutMs > 60000) {
    throw Error('Submission refused before dispatch');
  }
  const row = {...captured, status: 'pending'};
  state.rows.push(row);
  if (run.status !== 'running' || typeof run.steer !== 'function') {
    row.status = 'not-dispatched';
    return row;
  }
  state.pending = true;
  const unknown = () => { row.status = 'unknown'; state.blocked = true; };
  const ack = Promise.resolve().then(() => {
    if (state.closed || state.blocked || !state.pending || !ownsInput() ||
        run.status !== 'running' || typeof run.steer !== 'function') {
      row.status = 'not-dispatched';
      return;
    }
    return run.steer(captured.text);
  }).then(outcome => {
    if (row.status === 'not-dispatched') return;
    row.status = outcome === 'complete_delivered' ? 'delivered' :
      outcome === 'revert_to_followup' ? 'not-delivered' : 'unknown';
    if (row.status === 'unknown') state.blocked = true;
    // Delivery belongs to the captured owner even after close; never clear a newer draft.
    if (!state.closed && row.status === 'delivered' && ownsInput()) {
      state.draft = {text: '', revision: captured.revision + 1};
    }
  }, unknown);
  let timer;
  try {
    await Promise.race([ack, new Promise(resolve => {
      timer = setTimeout(() => { unknown(); resolve(); }, timeoutMs);
    })]);
  } finally { clearTimeout(timer); state.pending = false; }
  // A timed-out ack can settle later. The conservative blocked flag stays set.
  return row;
}
export async function closeProbe(state) {
  state.closed = true;
  state.cancel = 'requested';
  try { await state.run.cancel(); state.cancel = 'acknowledged'; }
  catch { state.cancel = 'unknown'; state.blocked = true; }
  // A cancel acknowledgement is not proof of tool/process/physical quiescence.
}
export function privateSnapshot(state) {
  return {schema: 1, owner: state.owner, draft: {...state.draft}, rows: state.rows.map(row => ({...row}))};
}
export function reopenSnapshot(snapshot, run) {
  if (snapshot.schema !== 1) throw Error('Unknown probe snapshot');
  const state = outbox(snapshot.owner, run);
  state.draft = {...snapshot.draft};
  state.rows = snapshot.rows.map(row => ({...row, status: row.status === 'pending' ? 'unknown' : row.status}));
  state.closed = state.blocked = true; // Detached/resumed data never authorizes a new injection.
  return state;
}

const cases = ['S1', 'S2', 'S3', 'S4', 'S5', 'S6'];
export function validateLiveConfig(c) {
  if (c.schema !== 1 || !cases.includes(c.case) || typeof c.approval !== 'string' || !c.approval.trim() ||
      c.approved !== true || c.acceptNoHardCostCap !== true || !c.model?.id?.trim() ||
      typeof c.sdkEntrySha256 !== 'string' || !/^[a-f0-9]{64}$/.test(c.sdkEntrySha256) ||
      !Array.isArray(c.model.params) || c.model.params.some(p => typeof p.id !== 'string' || !p.id.trim() ||
        typeof p.value !== 'string') ||
      !['root', 'sdkPackage', 'apiKeyFile'].every(k => typeof c[k] === 'string' && path.isAbsolute(c[k])) ||
      !Number.isInteger(c.maxRuns) || c.maxRuns < 1 || c.maxRuns > 6 ||
      !Number.isInteger(c.runMs) || c.runMs < 1 || c.runMs > 60000 ||
      !Number.isInteger(c.steerMs) || c.steerMs < 1 || c.steerMs > 10000 ||
      !Number.isFinite(c.budgetUsd) || c.budgetUsd <= 0 || c.budgetUsd > 1 ||
      !Number.isFinite(c.warningUsd) || c.warningUsd <= 0 || c.warningUsd > c.budgetUsd) {
    throw Error('Explicit approved configuration required; no defaults or hard cost cap');
  }
  return c;
}
function privatePath(file, directory = false) {
  const st = fs.lstatSync(file);
  if (st.isSymbolicLink() || (directory ? !st.isDirectory() : !st.isFile()) ||
      (st.mode & 0o077) !== 0 || st.uid !== process.getuid()) throw Error('Private owned path required');
}
export function atomicJson(file, value) {
  if (fs.existsSync(file)) privatePath(file);
  const tmp = `${file}.${randomUUID()}.tmp`;
  const fd = fs.openSync(tmp, 'wx', 0o600);
  try { fs.writeFileSync(fd, JSON.stringify(value, null, 2)); fs.fsyncSync(fd); }
  finally { fs.closeSync(fd); }
  fs.renameSync(tmp, file);
  const dir = fs.openSync(path.dirname(file), 'r');
  try { fs.fsyncSync(dir); } finally { fs.closeSync(dir); }
}
export function checkLiveConfig(file) {
  privatePath(file);
  const c = validateLiveConfig(JSON.parse(fs.readFileSync(file, 'utf8')));
  privatePath(c.root, true); privatePath(c.apiKeyFile); // stat only; never read key in preflight
  const pkg = JSON.parse(fs.readFileSync(path.join(c.sdkPackage, 'package.json'), 'utf8'));
  if (pkg.name !== '@cursor/sdk' || pkg.version !== '1.0.31' || pkg.engines?.node !== '>=22.13') {
    throw Error('Pinned SDK package required');
  }
  if (createHash('sha256').update(fs.readFileSync(path.join(c.sdkPackage, 'dist/esm/index.js'))).digest('hex') !==
      c.sdkEntrySha256) throw Error('Reviewed SDK entry bytes required');
  const ledgerPath = path.join(c.root, 'ledger.json');
  const binding = {approval: c.approval, model: c.model, maxRuns: c.maxRuns,
    runMs: c.runMs, steerMs: c.steerMs, budgetUsd: c.budgetUsd, warningUsd: c.warningUsd,
    sdkPackage: fs.realpathSync(c.sdkPackage), sdkEntrySha256: c.sdkEntrySha256,
    apiKeyFile: fs.realpathSync(c.apiKeyFile)};
  let ledger = {schema: 1, binding, runs: 0, elapsedMs: 0, entries: []};
  if (fs.existsSync(ledgerPath)) {
    privatePath(ledgerPath); ledger = JSON.parse(fs.readFileSync(ledgerPath, 'utf8'));
    if (ledger.schema !== 1 || JSON.stringify(ledger.binding) !== JSON.stringify(binding) ||
        !Number.isSafeInteger(ledger.runs) || ledger.runs < 0 || ledger.runs > 6 ||
        !Number.isFinite(ledger.elapsedMs) || ledger.elapsedMs < 0 || !Array.isArray(ledger.entries) ||
        ledger.entries.some(e => !cases.includes(e.case) || !['observed','unknown'].includes(e.outcome))) {
      throw Error('Approval/model/limits changed');
    }
    if (ledger.entries.some(e => e.case === c.case || e.outcome === 'unknown' || e.costReviewed !== true ||
        typeof e.costEvidence !== 'string' || !e.costEvidence.trim() ||
        !Number.isFinite(e.chargedUsd) || e.chargedUsd < 0 ||
        (e.observedChargedUsd !== null && e.chargedUsd < e.observedChargedUsd))) {
      throw Error('Case already attempted or prior outcome/cost unreviewed');
    }
  } else if (fs.readdirSync(c.root).length) throw Error('Fresh empty private root required');
  const cost = ledger.entries.reduce((sum, e) => sum + e.chargedUsd, 0);
  if (!Number.isFinite(cost) || cost >= c.warningUsd || ledger.elapsedMs >= 360000 ||
      (['S1','S3','S4','S6'].includes(c.case) && ledger.runs >= c.maxRuns)) throw Error('Probe limit reached');
  if (['S2','S5','S6'].includes(c.case) && !ledger.entries.some(e => e.case === 'S1' && e.runId)) {
    throw Error('S1 reference required');
  }
  return {c, ledger, ledgerPath};
}
async function deadline(promise, ms) {
  let timer;
  try { return await Promise.race([promise, new Promise((_, reject) => {
    timer = setTimeout(() => reject(Error('Outcome unknown at client deadline')), Math.max(1, ms));
  })]); } finally { clearTimeout(timer); }
}
// This entry is dormant until an explicitly approved caller supplies a real SDK.
// One case per invocation: no automatic next-case, retry, send-on-resume, or follow-up.
export async function driveCase({c, ledger, ledgerPath}, sdk, apiKey) {
  const started = Date.now(), until = started + Math.min(c.runMs, 360000 - ledger.elapsedMs);
  const entry = {case: c.case, outcome: 'unknown', costReviewed: false, chargedUsd: null,
    timeline: [], events: [], physical_quiescence: 'unobserved', model_consumption: 'unobserved'};
  ledger.entries.push(entry);
  const fresh = ['S1','S3','S4','S6'].includes(c.case);
  if (fresh) ledger.runs++; // Reserve before import/dispatch; crashes never authorize a retry.
  atomicJson(ledgerPath, ledger);
  const record = () => atomicJson(path.join(c.root, `${c.case}.json`), entry);
  const mark = label => { entry.timeline.push({ms: Date.now() - started, label}); record(); };
  const bounded = action => {
    const ms = until - Date.now();
    if (ms <= 0) throw Error('Client run deadline reached before operation');
    return deadline(action(), ms);
  };
  let agent, run, state, stream;
  const acquireAgent = async (kind, action) => {
    let stopped = false;
    try {
      return await bounded(() => Promise.resolve(action()).then(async handle => {
        if (stopped || Date.now() >= until) {
          entry.outcome = 'unknown'; entry.lateAcquisition = kind;
          entry.lateAgentId = handle.agentId; entry.lateDispose = 'requested';
          const persist = label => {
            entry.timeline.push({ms: Date.now() - started, label});
            try { record(); atomicJson(ledgerPath, ledger); }
            catch { entry.lateRecord = 'unknown'; }
          };
          persist('late-agent-owned');
          try { await deadline(handle[Symbol.asyncDispose](), c.steerMs); entry.lateDispose = 'acknowledged'; }
          catch { entry.lateDispose = 'unknown'; }
          persist('late-agent-dispose-settled-or-unknown');
          throw Error('Agent returned after client deadline');
        }
        // Own the handle before the race settles, including an expiry at this boundary.
        agent = handle;
        return handle;
      }));
    } catch (error) { stopped = true; throw error; }
  };
  try {
    for (const name of ['fixture', 'store']) {
      const dir = path.join(c.root, name);
      if (!fs.existsSync(dir)) fs.mkdirSync(dir, {mode: 0o700});
      privatePath(dir, true);
    }
    if (fs.readdirSync(path.join(c.root, 'fixture')).length) throw Error('Fixture must stay empty');
    const store = new sdk.JsonlLocalAgentStore(path.join(c.root, 'store'));
    const options = textOnlyOptions({apiKey, model: c.model, cwd: path.join(c.root, 'fixture'), store});
    const lookup = {runtime: 'local', cwd: options.local.cwd, store};
    const ref = ledger.entries.find(e => e.case === 'S1');
    if (!fresh) {
      if (c.case === 'S5') {
        agent = await acquireAgent('resume', () => sdk.Agent.resume(ref.agentId, options));
        if (agent.agentId !== ref.agentId) throw Error('Resumed owner changed');
        mark('resume-no-send');
      }
      run = await bounded(() => sdk.Agent.getRun(ref.runId, lookup));
      if (run.id !== ref.runId || run.agentId !== ref.agentId) throw Error('Stored run owner changed');
      entry.agentId = run.agentId; entry.runId = run.id;
      entry.methodPresent = typeof run.steer === 'function'; entry.detachedStatus = run.status;
      if (c.case === 'S2') {
        entry.detachedAck = entry.methodPresent ? await bounded(() => run.steer(initialText)) : 'method-absent';
        entry.liveTerminal = ref.terminalAck ?? 'unobserved-after-owner-release';
      } else {
        entry.restored = privateSnapshot(reopenSnapshot(ref.snapshot, run));
        entry.messages = await bounded(() => sdk.Agent.messages.list(ref.agentId, lookup));
      }
    } else {
      agent = await acquireAgent('create', () => sdk.Agent.create(options)); mark('created');
      run = await bounded(() => agent.send(initialText));
      if (run.agentId !== agent.agentId) throw Error('Initial run owner changed');
      entry.agentId = run.agentId; entry.runId = run.id; mark('initial-send');
      state = outbox(`fixture-owner-${c.case}`, run);
      const ticket = {id: `fixture-input-${c.case}`, owner: state.owner, agentId: run.agentId,
        runId: run.id, text: state.draft.text, revision: state.draft.revision};
      stream = (async () => {
        for await (const event of run.stream()) {
          entry.events.push({ms: Date.now() - started, event});
          if (entry.events.length > 256 || Buffer.byteLength(JSON.stringify(entry.events)) > 1048576) {
            throw Error('Private capture limit');
          }
        }
        return 'ended';
      })().catch(() => 'unknown');
      if (c.case === 'S6') {
        const foreign = await bounded(() => sdk.Agent.getRun(ref.runId, lookup));
        await assert.rejects(() => steerOnce(state, foreign, {...ticket, owner: 'fixture-owner-S1'}));
        await assert.rejects(() => steerOnce(state, run, {...ticket, agentId: foreign.agentId, runId: foreign.id}));
        entry.foreignDispatch = 'refused';
      }
      if (Date.now() >= until) throw Error('Client run deadline reached before steering');
      const steering = steerOnce(state, run, ticket, c.case === 'S3' ? 1 :
        Math.max(1, Math.min(c.steerMs, until - Date.now())));
      mark('steer-pending'); entry.snapshot = privateSnapshot(state); record();
      if (c.case === 'S4') { await Promise.resolve(); await bounded(() => closeProbe(state)); mark('close-requested'); }
      await bounded(() => steering); mark('steer-settled-or-unknown');
      entry.snapshotAtDeadline = privateSnapshot(state); record();
      if (c.case === 'S3') state.draft = {text: 'NEWER_FIXTURE_DRAFT', revision: 1};
      entry.result = await bounded(() => run.wait());
      entry.stream = await bounded(() => stream); mark('run-terminal');
      if (entry.stream !== 'ended') throw Error('Stream termination unknown');
      entry.conversation = await bounded(() => run.conversation());
      entry.snapshot = privateSnapshot(state);
      if (c.case === 'S1' && typeof run.steer === 'function') {
        try { entry.terminalAck = await bounded(() => run.steer(initialText)); } // S2 terminal evidence, no follow-up.
        catch { entry.terminalAck = 'unknown'; }
      }
      entry.usage = await bounded(() => agent.getUsage()); // Local usage UUID != client run ID.
      entry.observedChargedUsd = Number.isFinite(entry.usage.cost?.chargedCents) ?
        entry.usage.cost.chargedCents / 100 : null;
      entry.costNote = 'Eventually consistent; reviewer must settle ledger before another invocation';
    }
    entry.outcome = 'observed'; // Observation only: never a Case pass or consumption/durability claim.
  } catch {
    entry.outcome = 'unknown'; mark('exception-or-timeout');
    if (state) { state.closed = true; entry.snapshot = privateSnapshot(state); }
    if (run?.status === 'running') {
      try { await deadline(run.cancel(), c.steerMs); entry.cancel = 'acknowledged'; }
      catch { entry.cancel = 'unknown'; }
    }
  } finally {
    if (agent) {
      try { await deadline(agent[Symbol.asyncDispose](), c.steerMs); entry.dispose = 'acknowledged'; }
      catch { entry.dispose = 'unknown'; entry.outcome = 'unknown'; }
    }
    if (state) {
      entry.snapshot = privateSnapshot(state);
      if (state.cancel !== undefined) entry.cancel = state.cancel;
      entry.closed = state.closed; entry.blocked = state.blocked;
      if (state.cancel === 'unknown' || state.rows.some(row => ['pending','unknown'].includes(row.status))) {
        entry.outcome = 'unknown';
      }
    }
    ledger.elapsedMs += Date.now() - started;
    record(); atomicJson(ledgerPath, ledger);
  }
  return {case: c.case, observation: entry.outcome, provider_case: 'pending-independent-assessment',
    cost_review_required: true, physical_quiescence: 'unobserved'};
}
async function liveCli() {
  const [mode, file, ...extra] = process.argv.slice(2);
  if (!['--check-config','--execute-approved'].includes(mode) || !file || extra.length) {
    throw Error('Use --check-config FILE or explicitly approved --execute-approved FILE');
  }
  let prepared = checkLiveConfig(file);
  if (mode === '--check-config') return {prepared: true, sdk_imported: false, credential_read: false};
  // Explicit file only. Never consult environment, stored login, account/catalog APIs or defaults.
  const key = fs.readFileSync(prepared.c.apiKeyFile, 'utf8').trim();
  if (!key) throw Error('Explicit credential missing');
  process.umask(0o077);
  const lock = path.join(prepared.c.root, 'probe.lock');
  const fd = fs.openSync(lock, 'wx', 0o600); // One owned invocation; never delete an existing lock.
  try {
    // The new lock is the only extra item in a fresh root.
    if (!fs.existsSync(prepared.ledgerPath)) {
      const others = fs.readdirSync(prepared.c.root).filter(name => name !== 'probe.lock');
      if (others.length) throw Error('Fresh root changed');
      atomicJson(prepared.ledgerPath, prepared.ledger);
    }
    prepared = checkLiveConfig(file);
    for (const key of Object.keys(process.env)) {
      if (/^(CURSOR_|OPENAI_|ANTHROPIC_)/.test(key) || ['NODE_OPTIONS','NODE_PATH'].includes(key)) delete process.env[key];
    }
    const sdk = await import(pathToFileURL(path.join(prepared.c.sdkPackage, 'dist/esm/index.js')).href);
    return await driveCase(prepared, sdk, key);
  } finally { fs.closeSync(fd); fs.unlinkSync(lock); }
}
if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  try { console.log(JSON.stringify(await liveCli())); }
  catch { console.error('Probe refused or outcome unknown; inspect private records'); process.exitCode = 1; }
}

export async function offlineCheck() {
  let calls = 0;
  const run = (id = 'fixture-run-a', outcome = 'complete_delivered') => ({
    id, agentId: 'fixture-agent', status: 'running',
    steer: async () => { calls++; return outcome; }, cancel: async () => {},
  });
  const ticket = (state, id = 'fixture-input') => ({id, owner: state.owner,
    agentId: state.run.agentId, runId: state.run.id, text: state.draft.text, revision: state.draft.revision});
  const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r; }); return {promise, resolve}; };
  const rejected = (operation) => assert.rejects(operation, /refused before dispatch/);

  let options, sent;
  const fakeAgent = {send: async text => { sent = text; return run(); }, [Symbol.asyncDispose]: async () => {}};
  const started = await startTextOnly({create: async opts => { options = opts; return fakeAgent; }},
    {apiKey: 'SYNTHETIC_NOT_A_CREDENTIAL', model: {id: 'fixture-model'}, cwd: 'fixture', store: {}});
  assert.deepEqual(options.tools, []); assert.deepEqual(options.local.settingSources, []);
  assert.deepEqual(options.mcpServers, {}); assert.deepEqual(options.agents, {});
  assert.equal(options.local.enableAgentRetries, false); assert.equal(options.local.sandboxOptions.enabled, true);
  assert.equal(sent, initialText);
  let state = outbox('fixture-owner-a', started.run);
  assert.equal((await steerOnce(state, state.run, ticket(state))).status, 'delivered');
  assert.equal(state.draft.text, '');
  await rejected(() => steerOnce(state, state.run, {...ticket(state), text: 'repeat'}));

  for (const variant of ['not-delivered', 'missing', 'finished']) {
    const r = run('fixture-run-a', 'revert_to_followup');
    if (variant === 'missing') delete r.steer;
    if (variant === 'finished') r.status = 'finished';
    state = outbox('fixture-owner-a', r);
    assert.equal((await steerOnce(state, r, ticket(state))).status, variant === 'not-delivered' ? variant : 'not-dispatched');
    assert.notEqual(state.draft.text, '');
  }

  const late = deferred(), timedRun = run(); timedRun.steer = () => late.promise;
  state = outbox('fixture-owner-a', timedRun);
  assert.equal((await steerOnce(state, timedRun, ticket(state), 1)).status, 'unknown');
  const savedUnknown = privateSnapshot(state);
  await rejected(() => steerOnce(state, timedRun, ticket(state, 'another')));
  state.draft = {text: 'newer draft', revision: 1}; late.resolve('complete_delivered');
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(state.rows[0].status, 'delivered'); assert.equal(state.draft.text, 'newer draft');
  for (const bad of [async () => { throw Error('synthetic disconnect'); }, async () => 'confirm_steering']) {
    const r = run(); r.steer = bad; const s = outbox('fixture-owner-a', r);
    assert.equal((await steerOnce(s, r, ticket(s))).status, 'unknown'); assert.equal(s.blocked, true);
  }

  const pending = deferred(), stoppingRun = run(); stoppingRun.steer = () => pending.promise;
  state = outbox('fixture-owner-a', stoppingRun);
  const inFlight = steerOnce(state, stoppingRun, ticket(state));
  await rejected(() => steerOnce(state, stoppingRun, ticket(state, 'concurrent')));
  const savedPending = privateSnapshot(state);
  await closeProbe(state); pending.resolve('complete_delivered'); await inFlight;
  assert.equal(state.closed, true); assert.equal(state.cancel, 'acknowledged');
  assert.equal(state.rows[0].status, 'delivered'); assert.notEqual(state.draft.text, '');
  const failedCancel = outbox('fixture-owner-a', {...run(), cancel: async () => { throw Error('synthetic'); }});
  await closeProbe(failedCancel); assert.equal(failedCancel.cancel, 'unknown');

  for (const saved of [savedUnknown, savedPending, privateSnapshot(state)]) {
    const restored = reopenSnapshot(JSON.parse(JSON.stringify(saved)), run('fixture-detached'));
    assert.notEqual(restored.rows[0].status, 'pending');
    await rejected(() => steerOnce(restored, restored.run, ticket(restored, 'resume-retry')));
  }

  const a = outbox('fixture-owner-a', run()), b = outbox('fixture-owner-b', run('fixture-run-b'));
  for (const t of [{...ticket(a), owner: b.owner}, {...ticket(a), runId: b.run.id},
                   {...ticket(a), agentId: 'fixture-other-agent'}, {...ticket(a), text: ''}]) {
    await rejected(() => steerOnce(a, a.run, t));
  }
  await rejected(() => steerOnce(a, {...a.run}, ticket(a))); // Same IDs are not the owned handle.
  assert.equal(a.rows.length, 0);
  await steerOnce(a, a.run, ticket(a)); await steerOnce(b, b.run, ticket(b));
  assert.equal(a.rows[0].status, 'delivered'); assert.equal(b.rows[0].status, 'delivered');
  assert.equal(calls, 4);
  const dispatchTrace = [];
  const guardedRun = () => ({id: 'fixture-guarded-run', agentId: 'fixture-guarded-agent', status: 'running',
    steer: async text => { dispatchTrace.push(['steer', text]); return 'complete_delivered'; },
    cancel: async () => { dispatchTrace.push(['cancel']); },
  });
  state = outbox('fixture-owner', guardedRun());
  const staleTicket = ticket(state); state.draft = {text: 'NEW_DRAFT', revision: 1};
  await rejected(() => steerOnce(state, state.run, staleTicket));
  assert.equal(state.rows.length, 0); assert.equal(state.draft.text, 'NEW_DRAFT');
  for (const change of [s => { s.draft.text = 'NEW_DRAFT'; }, s => { s.draft.revision++; },
    s => { s.owner = 'other-owner'; }, s => { s.run = {...s.run}; },
    s => { s.run.id = 'other-run'; }, s => { s.run.agentId = 'other-agent'; },
    s => { s.blocked = true; }, s => { s.run.status = 'finished'; }]) {
    const s = outbox('fixture-owner', guardedRun());
    const queued = steerOnce(s, s.run, ticket(s)); change(s);
    assert.equal((await queued).status, 'not-dispatched'); assert.notEqual(s.draft.text, '');
  }
  state = outbox('fixture-owner', guardedRun());
  const beforeClose = steerOnce(state, state.run, ticket(state));
  await closeProbe(state); assert.equal((await beforeClose).status, 'not-dispatched');
  assert.deepEqual(dispatchTrace, [['cancel']]); assert.notEqual(state.draft.text, '');
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'sdk-probe-synthetic-'));
  try {
    fs.chmodSync(dir, 0o700);
    const c = {schema: 1, case: 'S1', approval: 'SYNTHETIC_ONLY', approved: true,
      acceptNoHardCostCap: true, model: {id: 'fixture-model', params: []}, root: dir,
      sdkPackage: dir, sdkEntrySha256: '0'.repeat(64), apiKeyFile: path.join(dir, 'unused-key'),
      maxRuns: 6, runMs: 1000, steerMs: 100, budgetUsd: 1, warningUsd: 0.5};
    validateLiveConfig(c);
    for (const change of [{approved: false}, {acceptNoHardCostCap: false}, {approval: ''},
      {model: {id: 'fixture-model'}}, {maxRuns: 7}, {runMs: 60001}, {budgetUsd: 2}, {root: 'relative'}]) {
      assert.throws(() => validateLiveConfig({...c, ...change}));
    }
    let created = 0, resumed = 0, sends = 0;
    const known = new Map();
    const makeAgent = () => ({agentId: `driver-agent-${sends + 1}`,
      send: async () => {
        sends++; const r = {...run(`driver-run-${sends}`), agentId: `driver-agent-${sends}`};
        r.wait = async () => { r.status = 'finished'; return {id: r.id, status: r.status, result: 'FIXTURE'}; };
        r.stream = async function* () { yield {type: 'fixture'}; };
        r.conversation = async () => [{type: 'fixture-history'}]; known.set(r.id, r); return r;
      },
      getUsage: async () => ({cost: {chargedCents: 1}, runs: []}),
      [Symbol.asyncDispose]: async () => {},
    });
    const fakeSdk = {JsonlLocalAgentStore: class {}, Agent: {
      create: async opts => { created++; assert.deepEqual(opts.tools, []); return makeAgent(); },
      resume: async (id, opts) => { resumed++; assert.deepEqual(opts.tools, []); return {...makeAgent(), agentId: id}; },
      getRun: async id => { const r = {...known.get(id)}; delete r.steer; return r; },
      messages: {list: async () => [{type: 'fixture-message'}]},
    }};
    const ledger = {schema: 1, runs: 0, elapsedMs: 0, entries: []};
    for (const id of cases) {
      const result = await driveCase({c: {...c, case: id}, ledger, ledgerPath: path.join(dir, 'ledger.json')},
        fakeSdk, 'SYNTHETIC_NOT_A_CREDENTIAL');
      assert.equal(result.observation, 'observed');
      assert.equal(result.provider_case, 'pending-independent-assessment');
    }
    assert.equal(created, 4); assert.equal(sends, 4); assert.equal(resumed, 1);
    assert.equal(ledger.runs, 4); assert.equal(ledger.entries.length, 6);
    assert.equal(ledger.entries.find(e => e.case === 'S4').cancel, 'acknowledged');
    assert.equal(ledger.entries.find(e => e.case === 'S4').closed, true);
    assert.equal(fs.statSync(path.join(dir, 'ledger.json')).mode & 0o077, 0);
    const before = fs.readFileSync(path.join(dir, 'ledger.json'), 'utf8');
    assert.throws(() => atomicJson(path.join(dir, 'missing', 'ledger.json'), {}));
    const circular = {}; circular.self = circular;
    assert.throws(() => atomicJson(path.join(dir, 'ledger.json'), circular));
    assert.equal(fs.readFileSync(path.join(dir, 'ledger.json'), 'utf8'), before);
    const root = path.join(dir, 'preflight'), pkg = path.join(dir, 'package');
    fs.mkdirSync(root, {mode: 0o700}); fs.mkdirSync(path.join(pkg, 'dist/esm'), {recursive: true});
    fs.writeFileSync(path.join(pkg, 'package.json'), JSON.stringify({name: '@cursor/sdk', version: '1.0.31',
      engines: {node: '>=22.13'}})); fs.writeFileSync(path.join(pkg, 'dist/esm/index.js'), '');
    fs.writeFileSync(c.apiKeyFile, '', {mode: 0o600}); // Empty synthetic file: preflight must not read/require key.
    const config = {...c, root, sdkPackage: pkg, sdkEntrySha256: createHash('sha256').update('').digest('hex')};
    const file = path.join(dir, 'config.json'); atomicJson(file, config);
    const prepared = checkLiveConfig(file);
    const l = prepared.ledger;
    l.entries.push({case: 'S1', outcome: 'observed', costReviewed: false, chargedUsd: null});
    atomicJson(prepared.ledgerPath, l); atomicJson(file, {...config, case: 'S3'});
    assert.throws(() => checkLiveConfig(file), /unreviewed/);
    l.entries[0] = {...l.entries[0], costReviewed: true, costEvidence: 'SYNTHETIC_ONLY', chargedUsd: 0.01};
    atomicJson(prepared.ledgerPath, l); assert.equal(checkLiveConfig(file).c.case, 'S3');
    l.runs = 6; atomicJson(prepared.ledgerPath, l); assert.throws(() => checkLiveConfig(file), /limit/);
    l.runs = 1; l.entries[0].chargedUsd = 0.5; atomicJson(prepared.ledgerPath, l);
    assert.throws(() => checkLiveConfig(file), /limit/);
    l.entries[0].chargedUsd = 0.01; l.entries[0].outcome = 'unknown'; atomicJson(prepared.ledgerPath, l);
    assert.throws(() => checkLiveConfig(file), /unreviewed/);
    const unknownRoot = path.join(dir, 'unknown'); fs.mkdirSync(unknownRoot, {mode: 0o700});
    const unknownLedger = {runs: 0, elapsedMs: 0, entries: []};
    const unknownResult = await driveCase({c: {...c, case: 'S3', root: unknownRoot},
      ledger: unknownLedger, ledgerPath: path.join(unknownRoot, 'ledger.json')},
      {...fakeSdk, Agent: {...fakeSdk.Agent, create: async () => {
        const agent = makeAgent(), send = agent.send;
        agent.send = async text => { const r = await send(text); r.steer = () => new Promise(() => {}); return r; };
        return agent;
      }}}, 'SYNTHETIC_NOT_A_CREDENTIAL');
    assert.equal(unknownResult.observation, 'unknown');
    assert.equal(unknownLedger.entries[0].snapshot.rows[0].status, 'unknown');
    for (const kind of ['create', 'resume']) {
      const lateRoot = path.join(dir, `late-${kind}`); fs.mkdirSync(lateRoot, {mode: 0o700});
      const acquiring = deferred(), cleaned = deferred(); let acquired = 0, disposed = 0;
      const lateCase = kind === 'create' ? 'S1' : 'S5';
      const lateLedger = {runs: 0, elapsedMs: 0, entries: kind === 'create' ? [] : [{case: 'S1', agentId: 'fixture-prior'}]};
      const lateSdk = {...fakeSdk, Agent: {...fakeSdk.Agent, [kind]: () => { acquired++; return acquiring.promise; }}};
      const expired = await driveCase({c: {...c, case: lateCase, root: lateRoot}, ledger: lateLedger,
        ledgerPath: path.join(lateRoot, 'ledger.json')}, lateSdk, 'SYNTHETIC_NOT_A_CREDENTIAL');
      assert.equal(expired.observation, 'unknown'); assert.equal(acquired, 1);
      acquiring.resolve({agentId: `fixture-late-${kind}`, send: async () => { sends++; },
        [Symbol.asyncDispose]: async () => { disposed++; cleaned.resolve(); }});
      await cleaned.promise; await new Promise(resolve => setImmediate(resolve));
      const saved = JSON.parse(fs.readFileSync(path.join(lateRoot, 'ledger.json'), 'utf8')).entries.at(-1);
      const caseRecord = JSON.parse(fs.readFileSync(path.join(lateRoot, `${lateCase}.json`), 'utf8'));
      assert.equal(disposed, 1); assert.equal(sends, 5); // Late create/resume never sends.
      assert.equal(saved.outcome, 'unknown'); assert.equal(saved.lateAcquisition, kind);
      assert.equal(saved.lateAgentId, `fixture-late-${kind}`); assert.equal(saved.lateDispose, 'acknowledged');
      assert.equal(caseRecord.lateDispose, 'acknowledged');
      assert.equal(saved.costReviewed, false);
    }
  } finally { fs.rmSync(dir, {recursive: true}); }
  return {evidence: 'synthetic-only', offline_checks: ['S1', 'S2', 'S3', 'S4', 'S5', 'S6'],
    provider_cases: Object.fromEntries(['S1', 'S2', 'S3', 'S4', 'S5', 'S6'].map(id => [id, 'blocked'])),
    driver_checks: 'fake-sdk-only; configuration, six entries, atomic private records; late create/resume cleanup and dispatch guards',
    sdk_agents: 0, provider_calls: 0, model_consumption: 'unobserved', physical_quiescence: 'unobserved'};
}
'''


def offline_check(node='node'):
    env = {k: v for k, v in os.environ.items() if k not in ('NODE_OPTIONS', 'NODE_PATH')
           and not k.startswith(('CURSOR_', 'OPENAI_', 'ANTHROPIC_'))}
    result = subprocess.run([node, '--input-type=module', '--eval', NODE_PROBE +
                             '\nconsole.log(JSON.stringify(await offlineCheck()));'],
                            env=env, capture_output=True, text=True, timeout=15)
    if result.returncode:
        raise RuntimeError('Synthetic Node check failed')
    return json.loads(result.stdout)


def inspect_sdk(root):
    """Read public metadata/types only: importing the SDK is deliberately unnecessary."""
    root = Path(root)
    package = json.loads((root / 'package.json').read_text())
    if package.get('name') != '@cursor/sdk' or package.get('version') != SDK_VERSION:
        raise ValueError('Expected @cursor/sdk 1.0.31')
    if package.get('engines', {}).get('node') != '>=22.13':
        raise ValueError('SDK engine contract changed')
    run_type = (root / 'dist/esm/run.d.ts').read_text()
    options = (root / 'dist/esm/options.d.ts').read_text()
    for contract in ('steer?(text: string): Promise<SteerAckOutcome>;',
                     '"complete_delivered" | "revert_to_followup"',
                     '"stream" | "wait" | "cancel" | "conversation"'):
        if contract not in run_type:
            raise ValueError('Pinned public Run contract changed')
    for contract in ('tools?: ToolName[];', 'settingSources?: SettingSource[];',
                     'enableAgentRetries?: boolean;', 'store?: LocalAgentStore;'):
        if contract not in options:
            raise ValueError('Pinned public AgentOptions contract changed')
    return {'sdk_version': SDK_VERSION, 'node_required': '>=22.13', 'evidence': 'static-public-types',
            'files_sha256': {name: hashlib.sha256((root / name).read_bytes()).hexdigest()
                             for name in ('package.json', 'dist/esm/run.d.ts', 'dist/esm/options.d.ts',
                                          'dist/esm/stubs.d.ts', 'dist/esm/usage-types.d.ts',
                                          'dist/esm/store/jsonl-local-agent-store.d.ts', 'dist/esm/index.js')},
            'sdk_agents': 0, 'provider_calls': 0}


def main():
    parser = argparse.ArgumentParser(__doc__)
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument('--inspect-sdk', type=Path, help='Read an already-installed SDK package directory')
    modes.add_argument('--export-node', type=Path, help='Write checked module/approved-only driver to a new private file; do not execute')
    parser.add_argument('--node', default='node', help='Node executable for synthetic checks only')
    args = parser.parse_args()
    if args.inspect_sdk:
        result = inspect_sdk(args.inspect_sdk)
    elif args.export_node:
        fd = os.open(args.export_node, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, 'w', encoding='utf-8') as target:
            target.write(NODE_PROBE)
        result = {'exported': True, 'live_entry_point': 'explicit-approved-config-only', 'provider_calls': 0}
    else:
        result = offline_check(args.node)
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
