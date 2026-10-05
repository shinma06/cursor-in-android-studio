#!/usr/bin/env python3
"""Issue #547 offline checks and export-only Node probe; never start a real SDK agent."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess

SDK_VERSION = '1.0.31'

# One exportable module keeps the checked logic and future probe identical. No SDK import,
# live CLI, dependency installer, credentials lookup, or product adapter is included.
NODE_PROBE = r'''
import assert from 'node:assert/strict';

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
  if (state.closed || state.blocked || state.pending || state.run !== run ||
      ticket.owner !== state.owner || ticket.agentId !== run.agentId || ticket.runId !== run.id ||
      typeof ticket.id !== 'string' || !ticket.id || state.rows.some(row => row.id === ticket.id) ||
      typeof ticket.text !== 'string' || !ticket.text.trim() || Buffer.byteLength(ticket.text) > 8192 ||
      !Number.isSafeInteger(ticket.revision) || ticket.revision < 0 ||
      !Number.isInteger(timeoutMs) || timeoutMs < 1 || timeoutMs > 60000) {
    throw Error('Submission refused before dispatch');
  }
  const row = {...ticket, status: 'pending'};
  state.rows.push(row);
  if (run.status !== 'running' || typeof run.steer !== 'function') {
    row.status = 'not-dispatched';
    return row;
  }
  state.pending = true;
  const unknown = () => { row.status = 'unknown'; state.blocked = true; };
  const ack = Promise.resolve().then(() => run.steer(ticket.text)).then(outcome => {
    row.status = outcome === 'complete_delivered' ? 'delivered' :
      outcome === 'revert_to_followup' ? 'not-delivered' : 'unknown';
    if (row.status === 'unknown') state.blocked = true;
    // Delivery belongs to the captured owner even after close; never clear a newer draft.
    if (!state.closed && row.status === 'delivered' &&
        state.draft.revision === ticket.revision && state.draft.text === ticket.text) {
      state.draft = {text: '', revision: ticket.revision + 1};
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
  return {evidence: 'synthetic-only', offline_checks: ['S1', 'S2', 'S3', 'S4', 'S5', 'S6'],
    provider_cases: Object.fromEntries(['S1', 'S2', 'S3', 'S4', 'S5', 'S6'].map(id => [id, 'blocked'])),
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
                             for name in ('package.json', 'dist/esm/run.d.ts', 'dist/esm/options.d.ts')},
            'sdk_agents': 0, 'provider_calls': 0}


def main():
    parser = argparse.ArgumentParser(__doc__)
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument('--inspect-sdk', type=Path, help='Read an already-installed SDK package directory')
    modes.add_argument('--export-node', type=Path, help='Write the checked module to a new private file; no live entry point')
    parser.add_argument('--node', default='node', help='Node executable for synthetic checks only')
    args = parser.parse_args()
    if args.inspect_sdk:
        result = inspect_sdk(args.inspect_sdk)
    elif args.export_node:
        with args.export_node.open('x', encoding='utf-8') as target:
            target.write(NODE_PROBE)
        result = {'exported': True, 'live_entry_point': False, 'provider_calls': 0}
    else:
        result = offline_check(args.node)
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
