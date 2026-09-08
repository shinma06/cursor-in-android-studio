"""Offline REST fixture: tag lookup omits drafts, listing includes all IDs/pages."""
import copy
import hashlib
import io
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import plugin_zip_publish as publisher

SHA = 'a' * 40
TREE = 'b' * 40
TAG = 'plugin-build-' + SHA
ZIP = publisher.NAME + '-' + SHA + '.zip'
BASE = 'repos/' + publisher.REPOSITORY + '/releases'


def envelope(folder, marker='local', recipe='gradle-test-buildPlugin-v1'):
    inner = io.BytesIO()
    with zipfile.ZipFile(inner, 'w') as jar:
        jar.writestr('META-INF/plugin.xml', '<idea-plugin><id>com.cursoragent.plugin</id></idea-plugin>')
        jar.writestr('source.txt', marker)
    outer = io.BytesIO()
    with zipfile.ZipFile(outer, 'w') as archive:
        archive.writestr('plugin/lib/plugin.jar', inner.getvalue())
    data = outer.getvalue()
    manifest = {'schema': 1, 'repository': publisher.REPOSITORY, 'commit': SHA, 'tree': TREE,
                'sha256': hashlib.sha256(data).hexdigest(), 'recipe': recipe,
                'build_environment': {'platform': marker, 'jdk': '21'}}
    files = {ZIP: data, 'manifest.json': json.dumps(manifest).encode()}
    for name, content in files.items():
        (folder / name).write_bytes(content)
    return files


class ReleaseAPI:
    def __init__(self):
        self.releases = {}
        self.data = {}
        self.calls = []
        self.next_asset = 1000
        self.fail_upload = None
        self.lost_upload_response = None
        self.corrupt_download = None
        self.stay_draft = False

    def add(self, release_id, files=None, draft=True, tag=TAG):
        self.releases[release_id] = {'id': release_id, 'tag_name': tag, 'draft': draft, 'assets': []}
        for name, content in (files or {}).items():
            self.asset(release_id, name, content)

    def asset(self, release_id, name, content):
        self.next_asset += 1
        obj = {'id': self.next_asset, 'name': name, 'state': 'uploaded'}
        self.releases[release_id]['assets'].append(obj)
        self.data[self.next_asset] = content
        return obj

    def call(self, cmd, **kwargs):
        self.calls.append(list(cmd))
        if cmd[:2] == ['git', 'rev-parse']:
            return TREE + '\n'
        assert cmd[:2] == ['gh', 'api'], cmd
        args = cmd[2:]
        method = args[args.index('--method') + 1] if '--method' in args else 'GET'
        endpoint = next(a for a in args if a.startswith(('repos/', 'https://uploads.github.com/')))
        path = endpoint.split('?', 1)[0]
        if path.startswith(BASE + '/tags/'):
            found = [r for r in self.releases.values() if not r['draft'] and r['tag_name'] == TAG]
            if not found:
                raise subprocess.CalledProcessError(1, cmd, stderr='HTTP 404')
            return json.dumps(found[0])
        if path == BASE and method == 'GET':
            assert '--paginate' in args and '--slurp' in args
            rows = list(self.releases.values())
            # Deliberately put a matching draft on a later page.
            return json.dumps([rows[:1], rows[1:]])
        if path == BASE and method == 'POST':
            payload = json.loads(kwargs['input'])
            assert payload['draft'] is True and payload['target_commitish'] == SHA
            number = max(self.releases.keys(), default=0) + 1
            self.add(number)
            return json.dumps(self.releases[number])
        if path.startswith('https://uploads.github.com/'):
            release_id = int(path.split('/')[-2])
            name = endpoint.split('?name=', 1)[1]
            if name == self.fail_upload:
                self.fail_upload = None
                raise subprocess.CalledProcessError(1, cmd, stderr='HTTP 502')
            if any(a['name'] == name for a in self.releases[release_id]['assets']):
                raise subprocess.CalledProcessError(1, cmd, stderr='HTTP 422 already_exists')
            content = Path(args[args.index('--input') + 1]).read_bytes()
            obj = self.asset(release_id, name, content)
            if name == self.lost_upload_response:
                self.lost_upload_response = None
                raise subprocess.CalledProcessError(1, cmd, stderr='connection lost')
            return json.dumps(obj).encode()
        if path.startswith(BASE + '/assets/'):
            assert args[-2:] == ['-H', 'Accept: application/octet-stream']
            asset_id = int(path.split('/')[-1])
            return b'broken' if self.corrupt_download == asset_id else self.data[asset_id]
        if path.endswith('/assets'):
            release_id = int(path.split('/')[-2])
            assert '--paginate' in args and '--slurp' in args
            return json.dumps([self.releases[release_id]['assets']])
        release_id = int(path.split('/')[-1])
        if method == 'PATCH':
            payload = json.loads(kwargs['input'])
            assert payload == {'draft': False, 'prerelease': True, 'make_latest': 'false'}
            if not self.stay_draft:
                self.releases[release_id]['draft'] = False
        else:
            assert method == 'GET', cmd
        return json.dumps(self.releases[release_id])

    def mutations(self):
        return [c for c in self.calls if '--method' in c and c[c.index('--method') + 1] != 'GET']


class ReleaseIdPublishTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.folder = Path(self.tmp.name)
        self.files = envelope(self.folder)
        self.api = ReleaseAPI()
        self.patch = patch.object(publisher.subprocess, 'check_output', side_effect=self.api.call)
        self.patch.start()
        self.addCleanup(self.patch.stop)

    def publish(self):
        return publisher.publish(self.folder, SHA)

    def test_tag_404_but_second_page_draft_is_resumed_by_id(self):
        self.api.add(1, tag='unrelated')
        self.api.add(9, {ZIP: self.files[ZIP]})
        with self.assertRaises(subprocess.CalledProcessError):
            self.api.call(['gh', 'api', BASE + '/tags/' + TAG])
        result = self.publish()
        self.assertEqual(result['commit'], SHA)
        self.assertFalse(self.api.releases[9]['draft'])
        self.assertEqual(len(self.api.releases), 2)
        mutations = self.api.mutations()
        self.assertEqual(len(mutations), 2)  # missing manifest and publish, no new draft
        self.assertIn('/releases/9/assets?', mutations[0][4])
        self.assertIn(BASE + '/9', mutations[1])
        self.assertTrue(all(c[:2] != ['gh', 'release'] for c in self.api.calls))

    def test_duplicate_drafts_prefer_complete_id_and_preserve_empty_ids(self):
        self.api.add(8)
        self.api.add(19, self.files)
        self.api.add(27)
        before = copy.deepcopy(self.api.data)
        self.publish()
        self.assertFalse(self.api.releases[19]['draft'])
        self.assertTrue(self.api.releases[8]['draft'])
        self.assertTrue(self.api.releases[27]['draft'])
        self.assertEqual(before, self.api.data)
        self.assertEqual(len(self.api.mutations()), 1)

    def test_broken_duplicate_draft_does_not_hide_valid_complete_release(self):
        self.api.add(1, {ZIP: self.files[ZIP]})
        self.api.releases[1]['assets'][0]['state'] = 'starter'
        self.api.add(2, {ZIP: b'corrupt', 'manifest.json': self.files['manifest.json']})
        self.api.add(9, self.files)
        self.publish()
        self.assertTrue(self.api.releases[1]['draft'])
        self.assertTrue(self.api.releases[2]['draft'])
        self.assertFalse(self.api.releases[9]['draft'])
        self.assertEqual(len(self.api.mutations()), 1)

    def test_only_broken_complete_draft_does_not_publish_empty_duplicate(self):
        self.api.add(2, {ZIP: b'corrupt', 'manifest.json': self.files['manifest.json']})
        self.api.add(9)
        with self.assertRaisesRegex(ValueError, 'no valid complete draft'):
            self.publish()
        self.assertEqual(self.api.mutations(), [])

    def test_partial_upload_retry_does_not_add_draft_or_replace_zip(self):
        self.api.add(9)
        self.api.fail_upload = 'manifest.json'
        with self.assertRaises(subprocess.CalledProcessError):
            self.publish()
        original = self.api.releases[9]['assets'][0]['id']
        self.publish()
        self.assertEqual(self.api.releases[9]['assets'][0]['id'], original)
        self.assertEqual(len(self.api.releases), 1)
        self.assertFalse(self.api.releases[9]['draft'])

    def test_upload_success_with_lost_response_requires_matching_id_readback(self):
        self.api.add(9)
        self.api.lost_upload_response = ZIP
        self.publish()
        self.assertFalse(self.api.releases[9]['draft'])
        self.assertEqual(len(self.api.releases[9]['assets']), 2)

    def test_partial_conflicting_asset_is_never_overwritten(self):
        self.api.add(9, {ZIP: b'conflicting existing data'})
        with self.assertRaisesRegex(ValueError, 'draft asset differs'):
            self.publish()
        self.assertEqual(self.api.mutations(), [])

    def test_published_canonical_reused_across_build_environment_and_recipe(self):
        remote = self.folder / 'remote'
        remote.mkdir()
        other = envelope(remote, marker='Linux SDK other', recipe='gradle-buildPlugin-v1')
        self.api.add(9, other, draft=False)
        self.api.add(10)  # accidental empty duplicate is left untouched
        result = self.publish()
        self.assertEqual(result['recipe'], 'gradle-buildPlugin-v1')
        self.assertNotEqual(result['sha256'], json.loads(self.files['manifest.json'])['sha256'])
        self.assertEqual(self.api.mutations(), [])

    def test_complete_draft_canonical_reused_without_replacing_valid_data(self):
        remote = self.folder / 'remote'
        remote.mkdir()
        other = envelope(remote, marker='old recorded build', recipe='gradle-buildPlugin-v1')
        self.api.add(9, other)
        result = self.publish()
        self.assertEqual(result['recipe'], 'gradle-buildPlugin-v1')
        self.assertEqual(len(self.api.mutations()), 1)

    def test_published_wrong_source_or_corrupt_zip_fails_without_mutation(self):
        for field in ('commit', 'tree'):
            with self.subTest(field=field):
                files = dict(self.files)
                manifest = json.loads(files['manifest.json'])
                manifest[field] = 'c' * 40
                files['manifest.json'] = json.dumps(manifest).encode()
                self.api.releases.clear()
                self.api.add(9, files, draft=False)
                with self.assertRaisesRegex(ValueError, 'source mismatch'):
                    self.publish()
                self.assertEqual(self.api.mutations(), [])
        self.api.releases.clear()
        self.api.add(9, self.files, draft=False)
        self.api.corrupt_download = self.api.releases[9]['assets'][0]['id']
        with self.assertRaisesRegex(ValueError, 'digest mismatch'):
            self.publish()
        self.assertEqual(self.api.mutations(), [])

    def test_create_uses_returned_release_id_and_reads_published_state(self):
        self.publish()
        self.assertEqual(list(self.api.releases), [1])
        self.assertFalse(self.api.releases[1]['draft'])
        self.assertEqual(len(self.api.mutations()), 4)

    def test_publish_readback_still_draft_fails(self):
        self.api.add(9, self.files)
        self.api.stay_draft = True
        with self.assertRaisesRegex(ValueError, 'still draft'):
            self.publish()
