"""Exercise immutable transfer and the existing promotion boundary without publishing."""
import copy
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import release_candidate as rc
import plugin_compatibility as pc
import branch_zip


class ReleaseTest(unittest.TestCase):
    def notes(self):
        document = (rc.ROOT / 'docs/releases/0.1.0.md').read_text()
        return document, document.split(rc.NOTES_START)[1].split(rc.NOTES_END)[0].strip()

    def test_version_notes_required_and_machine_record_not_visible(self):
        document, notes = self.notes()
        record = {'candidate': {'identity': {'plugin.version': '0.1.0'}}, 'main_merge': 'a' * 40, 'promotion_pr': 411}
        with patch.dict(rc.os.environ, {'GITHUB_REPOSITORY': 'shinma06/cursor-in-android-studio'}):
            with patch.object(rc, 'git_read', side_effect=lambda *a: 'a' * 40 if a[0] == 'rev-parse' else document) as git:
                self.assertEqual(rc.release_notes('0.1.0'), notes)
                self.assertEqual([c.args for c in git.call_args_list], [('rev-parse', 'origin/main'),
                                 ('show', 'a' * 40 + ':docs/releases/0.1.0.md')])
            body = rc.formal_body(notes, record)
            self.assertEqual(rc.formal_record(body), (record, notes))
            self.assertNotIn('"candidate"', body.split('<!--')[0])
            for bad in (document.replace(rc.NOTES_START, ''), document + rc.NOTES_START,
                        document.replace('## 主な変更', '## TODO'),
                        document.replace('/download/v0.1.0/', '/download/v0.2.0/')):
                with self.subTest(bad=bad[:30]), patch.object(rc, 'git_read', side_effect=lambda *a: 'a' * 40 if a[0] == 'rev-parse' else bad):
                    with self.assertRaises(ValueError): rc.release_notes('0.1.0')
            for bad in ('', '{"candidate":{}}', notes.replace('## 対応環境', '## 対象'),
                        notes[:notes.index('## 制約・詳細')] + '## 制約・詳細\n', notes + '\nTODO'):
                with self.assertRaises(ValueError): rc.validate_notes(bad, '0.1.0')
            correct = 'https://github.com/shinma06/cursor-in-android-studio/releases/download/v0.1.0/cursor-in-android-studio-0.1.0.zip'
            for wrong in (correct.replace('v0.1.0', 'v0.2.0'), correct.replace('shinma06', 'someone'),
                          '../../releases/download/v0.2.0/plugin.zip',
                          'https://github.com/shinma06/cursor-in-android-studio/blob/main/docs/releases/0.2.0.md'):
                with self.assertRaisesRegex(ValueError, 'only this version'):
                    rc.validate_notes(notes + '\n[別リンク](' + wrong + ')', '0.1.0')
            for unsupported in ('[旧版][old]\n\n[old]: ../../releases/download/v0.2.0/plugin.zip',
                                '<a href="../../releases/download/v0.2.0/plugin.zip">旧版</a>',
                                '[旧版](https://github.com/a/b/releases\\/download/v0.2.0/a.zip)'):
                with self.assertRaisesRegex(ValueError, 'inline Markdown'):
                    rc.validate_notes(notes + '\n' + unsupported, '0.1.0')
            with self.assertRaisesRegex(ValueError, 'only this version'):
                rc.validate_notes(notes + '\n[旧版](\n ../../releases/download/v0.2.0/plugin.zip\n)', '0.1.0')
            for bad in (body + rc.MARKER, body.replace('release-candidate:v1', 'unknown')):
                with self.assertRaises(ValueError): rc.formal_record(bad)

    def test_formal_notes_update_preserves_identity_and_assets_and_normal_retry_keeps_edits(self):
        _, notes = self.notes()
        record = {'candidate': {'identity': {'plugin.version': '0.1.0', 'source.commit': 'b' * 40},
                                'sha256': 'c' * 64, 'size': 8}, 'main_merge': 'a' * 40, 'promotion_pr': 411}
        legacy = rc.MARKER + '\n' + json.dumps(record)
        with tempfile.TemporaryDirectory() as temp, patch.dict(rc.os.environ, {'GITHUB_REPOSITORY': 'shinma06/cursor-in-android-studio'}):
            path = Path(temp); (path / 'a.zip').write_bytes(b'original')
            original = {'id': 1, 'body': legacy, 'draft': False, 'prerelease': False, 'target_commitish': 'a' * 40}
            state = {'release': copy.deepcopy(original), 'bytes': b'original'}
            asset = {'id': 2, 'name': 'a.zip', 'state': 'uploaded'}
            writes = []; downloads = []
            def api(endpoint, method='GET', data=None):
                self.assertEqual((endpoint, method, set(data)), ('releases/1', 'PATCH', {'body'}))
                self.assertTrue(downloads)  # Byte validation must precede the only write.
                writes.append(copy.deepcopy(data)); state['release'].update(data)
                return copy.deepcopy(state['release'])
            def download(item, destination):
                downloads.append(item['id']); destination.write_bytes(state['bytes'])
            body = rc.formal_body(notes, record)
            with patch.object(rc, 'release_for', side_effect=lambda _: copy.deepcopy(state['release'])), \
                    patch.object(rc.github, 'api', side_effect=api), \
                    patch.object(rc.github, 'pages', return_value=[asset]), \
                    patch.object(rc, 'download_asset', side_effect=download), patch.object(rc.github, 'gh') as upload:
                rc.preserve('v0.1.0', 'a' * 40, path, ['a.zip'], False, body, update_notes=True)
                self.assertEqual(state['release'], {**original, 'body': body})
                self.assertEqual(downloads, [2, 2])
                self.assertEqual(len(writes), 1); upload.assert_not_called()
                edited = body.replace('ビルドツール', 'ビルド用ツール')
                state['release']['body'] = edited
                rc.preserve('v0.1.0', 'a' * 40, path, ['a.zip'], False, body)
                self.assertEqual(state['release']['body'], edited); self.assertEqual(len(writes), 1)
                for key in ('main_merge', 'promotion_pr', 'candidate'):
                    changed = copy.deepcopy(record); changed[key] = 'changed'
                    state['release']['body'] = rc.MARKER + '\n' + json.dumps(changed)
                    with self.assertRaisesRegex(ValueError, 'identity'):
                        rc.preserve('v0.1.0', 'a' * 40, path, ['a.zip'], False, body, update_notes=True)
                state['release'] = copy.deepcopy(original); state['bytes'] = b'changed'
                with self.assertRaisesRegex(ValueError, 'Existing release differs'):
                    rc.preserve('v0.1.0', 'f' * 40, path, ['a.zip'], False, body, update_notes=True)
                with self.assertRaisesRegex(ValueError, 'no overwrite'):
                    rc.preserve('v0.1.0', 'a' * 40, path, ['a.zip'], False, body, update_notes=True)
                self.assertEqual(len(writes), 1); self.assertEqual(state['release'], original)
                with patch.object(rc.github, 'pages', return_value=[]):
                    with self.assertRaisesRegex(ValueError, 'incomplete'):
                        rc.preserve('v0.1.0', 'a' * 40, path, ['a.zip'], False, body, update_notes=True)
                with patch.object(rc, 'release_for', return_value=None):
                    with self.assertRaisesRegex(ValueError, 'existing published'):
                        rc.preserve('v0.1.0', 'a' * 40, path, ['a.zip'], False, body, update_notes=True)
                state['release']['prerelease'] = True
                with self.assertRaisesRegex(ValueError, 'Existing release differs'):
                    rc.preserve('v0.1.0', 'a' * 40, path, ['a.zip'], False, body, update_notes=True)
                self.assertEqual(len(writes), 1); upload.assert_not_called()

    def test_legacy_formal_draft_retries_after_partial_upload_and_body_patch_failure(self):
        _, notes = self.notes()
        record = {'candidate': {'identity': {'plugin.version': '0.1.0'}}, 'main_merge': 'a' * 40, 'promotion_pr': 411}
        legacy = rc.MARKER + '\n' + json.dumps(record)
        with tempfile.TemporaryDirectory() as temp, patch.dict(rc.os.environ, {'GITHUB_REPOSITORY': 'shinma06/cursor-in-android-studio'}):
            path = Path(temp); (path / 'a.zip').write_bytes(b'original')
            state = {'release': {'id': 1, 'body': legacy, 'draft': True, 'prerelease': False, 'target_commitish': 'a' * 40},
                     'assets': [], 'bytes': b'', 'fail_patch': True}
            writes = []
            def api(endpoint, method='GET', data=None):
                self.assertEqual((endpoint, method), ('releases/1', 'PATCH'))
                if 'body' in data and state['fail_patch']:
                    raise RuntimeError('interrupted body patch')
                writes.append(data); state['release'].update(data)
                return copy.deepcopy(state['release'])
            def upload(*args):
                self.assertFalse(state['assets']); state['bytes'] = (path / 'a.zip').read_bytes()
                item = {'id': 2, 'name': 'a.zip', 'digest': 'sha256:' + pc.digest(path / 'a.zip')}
                state['assets'].append(item); return json.dumps(item)
            def download(item, destination): destination.write_bytes(state['bytes'])
            body = rc.formal_body(notes, record)
            with patch.object(rc, 'release_for', side_effect=lambda _: copy.deepcopy(state['release'])), \
                    patch.object(rc.github, 'api', side_effect=api), \
                    patch.object(rc.github, 'pages', side_effect=lambda _: list(state['assets'])), \
                    patch.object(rc.github, 'gh', side_effect=upload) as uploads, patch.object(rc, 'download_asset', side_effect=download):
                with self.assertRaisesRegex(RuntimeError, 'interrupted body patch'):
                    rc.preserve('v0.1.0', 'a' * 40, path, ['a.zip'], False, body)
                self.assertTrue(state['release']['draft']); self.assertEqual(state['release']['body'], legacy)
                state['fail_patch'] = False
                rc.preserve('v0.1.0', 'a' * 40, path, ['a.zip'], False, body)
                self.assertEqual(uploads.call_count, 1)
                self.assertFalse(state['release']['draft']); self.assertEqual(state['release']['body'], body)
                self.assertEqual(writes, [{'body': body}, {'draft': False, 'make_latest': 'false'}])
                self.assertEqual(state['bytes'], b'original')

    def test_publish_checks_notes_and_existing_tag_before_any_write(self):
        _, notes = self.notes()
        manifest = {'identity': {'plugin.version': '0.1.0'}, 'sha256': 'c' * 64}
        with patch.dict(rc.os.environ, {'GITHUB_REPOSITORY': 'shinma06/cursor-in-android-studio'}), \
                patch.object(rc, 'publication', return_value=(manifest, 'a' * 40)), \
                patch.object(rc, 'release_notes', side_effect=ValueError('missing notes')), patch.object(rc, 'preserve') as preserve:
            with self.assertRaisesRegex(ValueError, 'missing notes'): rc.publish(Path('.'), 'c' * 64, 411)
            preserve.assert_not_called()
            with patch.object(rc, 'release_notes', return_value=notes), patch.object(rc, 'fetch', return_value=manifest), \
                    patch.object(rc.github, 'api', return_value=[]):
                with self.assertRaisesRegex(ValueError, 'final tag'):
                    rc.publish(Path('.'), 'c' * 64, 411, update_notes=True)
                preserve.assert_not_called()
            def tag_api(endpoint):
                return [] if endpoint.startswith('git/matching-refs/') else {'object': {'type': 'commit', 'sha': 'a' * 40}}
            with patch.object(rc, 'release_notes', return_value=notes), patch.object(rc, 'fetch', return_value=manifest), \
                    patch.object(rc.github, 'api', side_effect=tag_api), patch.object(rc, 'read', return_value={}):
                rc.publish(Path('.'), 'c' * 64, 411)
                body = preserve.call_args.args[5]
                actual, visible = rc.formal_record(body)
                self.assertEqual(visible, notes)
                self.assertEqual(actual, {'candidate': manifest, 'main_merge': 'a' * 40, 'promotion_pr': 411})

    def test_bundle_binds_source_zip_both_reports_and_excludes_private_log(self):
        policy = json.loads(pc.POLICY.read_text())
        source = 'a' * 40
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp); directory = root / 'candidate'; directory.mkdir()
            product = directory / rc.version_name('1.2.3')
            jar = io.BytesIO()
            with zipfile.ZipFile(jar, 'w') as inner:
                inner.writestr('META-INF/plugin.xml', '<idea-plugin><id>com.cursoragent.plugin</id><version>1.2.3</version></idea-plugin>')
                inner.writestr('cursor-agent-build.properties', f'plugin.version=1.2.3\nsource.commit={source}\nsource.state=clean\nsdk.build={policy["targets"]["quail1"]["build"]}\njvm.target=21\n')
                inner.writestr('Example.class', b'bytecode')
            with zipfile.ZipFile(product, 'w') as outer: outer.writestr('plugin/lib/plugin.jar', jar.getvalue())
            manifest=pc.seal(product, source, policy)
            rc.write(directory/'manifest.json',manifest)
            rc.write(directory/'inputs.json',{'source':source,'version':'1.2.3','files':{},'libraries':['plugin.jar'],'command':rc.build_command('1.2.3'),'java_version':'21.0.11'})
            evidence={}
            for key,target in policy['targets'].items():
                path=root/key; reports=path/'reports'/target['build']/'plugins'/pc.PLUGIN_ID/'1.2.3'; reports.mkdir(parents=True)
                (reports/'verification-verdict.txt').write_text('Compatible.')
                (reports/'telemetry.txt').write_text('Verified classes in plugin artifact: 1\n')
                (reports/'dependencies.txt').write_text(pc.PLUGIN_ID+':1.2.3\n')
                log='Starting the IntelliJ Plugin Verifier 1.410\nScheduled verifications (1):\nFinished 1 of 1 verifications\nSDK /Users/private-user/sdk\n'
                (path/'verifier.log').write_text(log)
                result={'status':'passed','artifact':manifest,'verifier_version':'1.410',
                        'target':{'build':target['build'],'java_version':target['java_version'], 'distribution_version':target['version'],
                                  'java_runtime':'Picked up JAVA_TOOL_OPTIONS: -Dsecret=fake-test-value\nopenjdk version "'+target['java_version']+'"\nOpenJDK Runtime Environment (build 21.0.10+-123-b1.1)\nOpenJDK 64-Bit Server VM (build 21.0.10+-123-b1.1, mixed mode)'},
                        **pc.check_reports(path/'reports',log,target,manifest,policy)}
                rc.write(path/'result.json',result); evidence[key]=path
            with patch.object(rc,'source_inputs',return_value={}),patch.object(rc,'git_read',return_value=json.dumps(policy)):
                rc.bundle(directory,manifest['sha256'],evidence)
                original = pc.digest(product)
                rc.bundle(directory,manifest['sha256'],evidence)  # Same reports can safely retry.
                (directory/'compatibility-quail4.zip').unlink()  # Simulate interrupted copy.
                (directory/'bundle.json').unlink()
                rc.bundle(directory,manifest['sha256'],evidence)
                self.assertEqual(pc.digest(product),original)
                self.assertEqual(rc.validate_bundle(directory,manifest['sha256']),manifest)
                with zipfile.ZipFile(directory/'compatibility-quail1.zip') as report:
                    self.assertNotIn('/Users/',report.read('verifier-summary.txt').decode())
                    self.assertNotIn('fake-test-value',report.read('result.json').decode())
                inputs=rc.read(directory/'inputs.json'); inputs['java_version']='21.0.11\nPicked up JAVA_TOOL_OPTIONS: fake-test-value'
                rc.write(directory/'inputs.json',inputs)
                with self.assertRaisesRegex(ValueError,'public build inputs'):rc.candidate(directory,manifest['sha256'])
                inputs['java_version']='21.0.11'; rc.write(directory/'inputs.json',inputs)
                with self.assertRaises(ValueError):rc.validate_bundle(directory,'0'*64)
                product.write_bytes(b'changed')
                with self.assertRaises(ValueError):rc.validate_bundle(directory,manifest['sha256'])
            for version in ('1.2.3-SNAPSHOT','../v1','01.2.3'):
                with self.assertRaises(ValueError):rc.version_name(version)

    def test_preserve_retries_exact_bytes_and_never_overwrites(self):
        with tempfile.TemporaryDirectory() as temp:
            path=Path(temp); (path/'a.zip').write_bytes(b'original')
            state={'release':None,'assets':[],'bytes':{}}; mutations=[]
            def api(endpoint,method='GET',data=None):
                mutations.append((endpoint,method))
                if endpoint=='releases':state['release']={**data,'id':1};return state['release']
                if endpoint=='releases/1':state['release'].update(data);return state['release']
                raise AssertionError(endpoint)
            def upload(*args):
                self.assertNotIn('--clobber',args)
                state['bytes'][2]=(path/'a.zip').read_bytes()
                asset={'id':2,'name':'a.zip','state':'uploaded','digest':'sha256:'+pc.digest(path/'a.zip')}
                state['assets'].append(asset);return json.dumps(asset)
            def download(asset,destination):destination.write_bytes(state['bytes'][asset['id']])
            with patch.object(rc,'release_for',side_effect=lambda _:state['release']),patch.object(rc.github,'api',side_effect=api),patch.object(rc.github,'pages',side_effect=lambda _:state['assets']),patch.object(rc.github,'gh',side_effect=upload),patch.object(rc,'download_asset',side_effect=download),patch.dict(rc.os.environ,{'GITHUB_REPOSITORY':'owner/repo'}):
                for _ in range(2):rc.preserve('plugin-rc-test','main',path,['a.zip'],True,'body')
                self.assertEqual(len(state['assets']),1)
                self.assertNotIn('DELETE',[m for _,m in mutations])
                (path/'a.zip').write_bytes(b'changed')
                with self.assertRaisesRegex(ValueError,'no overwrite'):rc.preserve('plugin-rc-test','main',path,['a.zip'],True,'body')
                self.assertEqual(state['bytes'][2],b'original')
                self.assertIsNone(branch_zip.cleanup_identity(state['release']))
                state['release']['prerelease']=False
                self.assertIsNone(branch_zip.cleanup_identity(state['release']))

    def test_publication_requires_merged_promotion_matching_candidate_hash_and_checks(self):
        candidate='a'*40; head='b'*40; base='c'*40; merge='d'*40; sha='e'*64
        manifest={'identity':{'source.commit':candidate}}
        pr={'merged':True,'head':{'sha':head,'repo':{'full_name':'owner/repo'}},'base':{'ref':'main','repo':{'full_name':'owner/repo'}},'merge_commit_sha':merge}
        promotion={'artifact_sha256':sha}
        gate={'mode':'promotion','gui_complete':True,'candidate':candidate}
        checks=[{'name':k,'state':'SUCCESS'} for k in ('test','PR policy','Agent review','Acceptance gate')]
        def git(*args):
            if args[0]=='rev-list':return f'{merge} {base} {head}'
            if args[0]=='show':return json.dumps(promotion)
            return ''
        with patch.dict(rc.os.environ,{'GITHUB_REPOSITORY':'owner/repo'}),patch.object(rc,'validate_bundle',return_value=manifest),patch.object(rc.github,'api',return_value=pr),patch.object(rc,'git_read',side_effect=git),patch.object(rc,'verify_pr',return_value=gate) as verify,patch.object(rc.github,'gh',side_effect=lambda *a:json.dumps(checks)):
            self.assertEqual(rc.publication(Path('.'),sha,1),(manifest,merge))
            self.assertEqual(verify.call_args[0][0]['base']['sha'],base)
            for obj,key,value in ((pr,'merged',False),(promotion,'artifact_sha256','f'*64),(gate,'candidate','f'*40),(gate,'mode','tooling')):
                old=obj[key];obj[key]=value
                with self.assertRaises(ValueError):rc.publication(Path('.'),sha,1)
                obj[key]=old
            checks.pop()
            with self.assertRaisesRegex(ValueError,'checks'):rc.publication(Path('.'),sha,1)
            with patch.object(rc,'publication',side_effect=ValueError('missing acceptance')),patch.object(rc,'preserve') as preserve:
                with self.assertRaises(ValueError):rc.publish(Path('.'),sha,1)
                preserve.assert_not_called()
