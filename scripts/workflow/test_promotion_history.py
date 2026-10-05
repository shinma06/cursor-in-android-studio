"""Promotion history trust boundaries, using real Git objects and disposable repos."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

from verification import PROMOTION, promotion_history


class PromotionMainSyncTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.env = {k: v for k, v in os.environ.items() if not k.startswith('GIT_')}
        self.git('init', '-qb', 'main')
        self.git('config', 'user.name', 'Fixture')
        self.git('config', 'user.email', 'fixture@example.invalid')
        self.git('config', 'core.hooksPath', '/dev/null')
        self.lines = [f'line {i}\n' for i in range(30)]
        self.write('docs/shared.md', ''.join(self.lines))
        self.write('docs/legacy.md', 'original\n')
        self.write('src/main/product.txt', 'old product\n')
        self.common = self.commit('common')
        self.git('checkout', '-qb', 'develop')
        self.write('src/main/product.txt', 'fixed product\n')
        self.write('docs/shared.md', 'candidate knowledge\n' + ''.join(self.lines[1:]))
        self.write('docs/legacy.md', 'candidate policy\n')
        self.write('docs/verification/changes/issue-1.json', self.acceptance(1))
        self.candidate = self.commit('candidate')
        self.git('checkout', 'main')
        self.write('docs/legacy.md', 'historical main policy\n')
        self.old_main = self.commit('historical main')
        self.git('checkout', '-qb', 'promotion', self.candidate)
        # Previously permitted metadata-only resolution is retained as historical evidence.
        self.git('merge', '-s', 'ours', '--no-edit', self.old_main)
        self.path = 'docs/verification/changes/issue-35.json'
        self.allowed = {PROMOTION, self.path}
        self.write(PROMOTION, {'candidate': self.candidate, 'results': {'1:GUI': {'status': 'pending'}}})
        self.write(self.path, self.acceptance(35))
        self.legacy = self.commit('acceptance metadata')
        self.git('checkout', 'main')
        self.write('docs/shared.md', ''.join(self.lines[:-1]) + 'current main knowledge\n')
        self.write('scripts/workflow/helper.py', '# trusted tooling\n')
        self.write('docs/verification/changes/issue-2.json', self.acceptance(2))
        self.main = self.commit('new main tooling')

    def git(self, *args):
        return subprocess.check_output(['git', *args], cwd=self.root, env=self.env,
                                       text=True, stderr=subprocess.DEVNULL).strip()

    def write(self, path, value):
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(value) if isinstance(value, dict) else value)

    def commit(self, message):
        self.git('add', '.')
        self.git('commit', '-qm', message)
        return self.git('rev-parse', 'HEAD')

    @staticmethod
    def acceptance(issue):
        return {'schema': 1, 'issue': issue, 'gui_required': False, 'reason': 'fixture tooling',
                'cli_checks': ['fixture check'], 'cases': []}

    def merge(self, main=None):
        self.git('checkout', 'promotion')
        self.git('merge', '--no-edit', main or self.main)
        return self.git('rev-parse', 'HEAD')

    def verify(self, head, main=None):
        promotion_history(self.candidate, head, main or self.main, self.allowed, self.git)

    def test_old_main_and_clean_current_main_preserve_both_documents_and_pending(self):
        self.verify(self.legacy)
        head = self.merge()
        self.verify(head)
        shared = self.git('show', f'{head}:docs/shared.md')
        self.assertIn('candidate knowledge', shared)
        self.assertIn('current main knowledge', shared)
        self.assertEqual(self.git('show', f'{head}:src/main/product.txt'), 'fixed product')
        self.assertEqual(self.git('show', f'{head}:{PROMOTION}'), self.git('show', f'{self.legacy}:{PROMOTION}'))
        # A later metadata result record is still allowed, without granting GUI acceptance.
        self.write(self.path, dict(self.acceptance(35), reason='record only'))
        self.verify(self.commit('metadata after sync'))

    def test_arbitrary_document_or_product_merge_resolution_is_rejected(self):
        tree = self.git('rev-parse', self.merge() + '^{tree}')
        for path in ('docs/shared.md', 'src/main/product.txt'):
            with self.subTest(path=path):
                self.git('read-tree', '--reset', '-u', tree)
                self.write(path, 'manual mutation')
                self.git('add', '.')
                mutated = self.git('write-tree')
                head = self.git('commit-tree', mutated, '-p', self.legacy, '-p', self.main, '-m', 'forged merge')
                with self.assertRaisesRegex(ValueError, 'clean merge result'):
                    self.verify(head)

    def test_non_merge_edit_revert_cannot_hide_product_or_document_changes(self):
        head = self.merge()
        for path in ('src/main/product.txt', 'docs/shared.md'):
            with self.subTest(path=path):
                self.git('checkout', '--detach', head)
                before = self.git('show', f'{head}:{path}') + '\n'
                self.write(path, 'unobserved')
                self.commit('unobserved change')
                self.write(path, before)
                reverted = self.commit('revert')
                with self.assertRaisesRegex(ValueError, 'Untested commit'):
                    self.verify(reverted)

    def test_main_product_edit_revert_cannot_hide_in_clean_tooling_merge(self):
        self.write('src/main/product.txt', 'unobserved')
        self.commit('product mutation')
        self.write('src/main/product.txt', 'old product\n')
        main = self.commit('product reverted')
        with self.assertRaisesRegex(ValueError, 'Product, build or unknown'):
            self.verify(self.merge(main), main)

    def test_main_edit_revert_is_rejected_even_without_a_merge_diff(self):
        self.git('config', 'diff.ignoreSubmodules', 'all')
        for path, mode in (('src/main/product.txt', None), ('build.gradle.kts', None),
                           ('docs/link.md', '120000'), ('docs/submodule', '160000')):
            for metadata_only in (False, True):
                with self.subTest(path=path, metadata_only=metadata_only):
                    self.git('checkout', '--detach', self.old_main)
                    if mode:
                        obj = self.common if mode == '160000' else self.git('rev-parse', self.main + ':docs/shared.md')
                        self.git('update-index', '--add', '--cacheinfo', mode, obj, path)
                        self.git('commit', '-qm', 'unobserved main change')
                        mutation = self.git('rev-parse', 'HEAD')
                    else:
                        self.write(path, 'unobserved mutation\n')
                        mutation = self.commit('unobserved main change')
                    self.git('revert', '--no-edit', mutation)
                    main = self.git('rev-parse', 'HEAD')
                    self.git('checkout', '-B', 'promotion', self.legacy)
                    head = self.merge(main)
                    self.assertEqual(self.git('rev-parse', head + '^{tree}'),
                                     self.git('rev-parse', self.legacy + '^{tree}'))
                    if metadata_only:
                        self.write(self.path, dict(self.acceptance(35), reason='merge metadata'))
                        self.git('add', self.path)
                        self.git('commit', '--amend', '--no-edit')
                        head = self.git('rev-parse', 'HEAD')
                        self.assertEqual(self.git('diff', '--name-only', self.legacy, head), self.path)
                    with self.assertRaisesRegex(ValueError, 'Product, build or unknown'):
                        self.verify(head, main)

    def test_reverted_tooling_without_a_merge_diff_remains_valid(self):
        self.git('checkout', '--detach', self.old_main)
        self.write('docs/temporary.md', 'temporary knowledge\n')
        mutation = self.commit('main knowledge')
        self.git('revert', '--no-edit', mutation)
        main = self.git('rev-parse', 'HEAD')
        head = self.merge(main)
        self.assertEqual(self.git('rev-parse', head + '^{tree}'),
                         self.git('rev-parse', self.legacy + '^{tree}'))
        self.verify(head, main)

    def test_empty_main_merge_still_checks_its_side_branch_history(self):
        self.git('checkout', '--detach', self.old_main)
        self.write('src/main/product.txt', 'unobserved side branch\n')
        mutation = self.commit('side product change')
        self.git('revert', '--no-edit', mutation)
        side = self.git('rev-parse', 'HEAD')
        main = self.git('commit-tree', self.old_main + '^{tree}',
                        '-p', self.old_main, '-p', side, '-m', 'empty main merge')
        head = self.merge(main)
        self.assertEqual(self.git('rev-parse', head + '^{tree}'),
                         self.git('rev-parse', self.legacy + '^{tree}'))
        with self.assertRaisesRegex(ValueError, 'Product, build or unknown'):
            self.verify(head, main)

    def test_main_merge_cannot_introduce_its_own_product_change(self):
        self.git('checkout', '--detach', self.old_main)
        self.write('docs/side.md', 'safe side branch\n')
        side = self.commit('main side knowledge')
        self.write('src/main/product.txt', 'unobserved merge resolution\n')
        self.git('add', '.')
        main = self.git('commit-tree', self.git('write-tree'),
                        '-p', self.old_main, '-p', side, '-m', 'forged main merge')
        # Hiding the new content in the outer merge cannot hide its provenance.
        head = self.git('commit-tree', self.legacy + '^{tree}',
                        '-p', self.legacy, '-p', main, '-m', 'empty promotion merge')
        with self.assertRaisesRegex(ValueError, 'Product, build or unknown'):
            self.verify(head, main)

    def test_build_unknown_symlink_and_submodule_imports_are_rejected(self):
        for path, mode in (('build.gradle.kts', None), ('scripts/workflow/plugin_compatibility.json', None),
                           ('scripts/workflow/change_impact.py', None), ('unknown.txt', None),
                           ('docs/link.md', '120000'), ('docs/submodule', '160000')):
            with self.subTest(path=path):
                self.git('checkout', '--detach', self.main)
                if mode:
                    obj = self.common if mode == '160000' else self.git('rev-parse', self.main + ':docs/shared.md')
                    self.git('update-index', '--add', '--cacheinfo', mode, obj, path)
                    tree = self.git('write-tree')
                    main = self.git('commit-tree', tree, '-p', self.main, '-m', 'unsafe mode')
                else:
                    self.write(path, 'unsafe import')
                    main = self.commit('unsafe import')
                self.git('checkout', '--detach', self.legacy)
                tree = self.git('merge-tree', '--write-tree', '--no-messages', self.legacy, main)
                head = self.git('commit-tree', tree, '-p', self.legacy, '-p', main, '-m', 'sync')
                with self.assertRaisesRegex(ValueError, 'Product, build or unknown'):
                    self.verify(head, main)

    def test_submodules_cannot_be_hidden_by_git_configuration(self):
        self.git('config', 'diff.ignoreSubmodules', 'all')
        for scenario in ('main_import', 'direct_add_then_revert'):
            with self.subTest(scenario=scenario):
                first = self.main if scenario == 'main_import' else self.legacy
                self.git('checkout', '--detach', first)
                self.git('update-index', '--add', '--cacheinfo', '160000', self.common,
                         'src/main/unobserved-submodule')
                tree = self.git('write-tree')
                added = self.git('commit-tree', tree, '-p', first, '-m', 'add gitlink')
                if scenario == 'main_import':
                    main = added
                    tree = self.git('merge-tree', '--write-tree', '--no-messages', self.legacy, main)
                    head = self.git('commit-tree', tree, '-p', self.legacy, '-p', main, '-m', 'sync')
                    error = 'Product, build or unknown'
                else:
                    main = self.main
                    head = self.git('commit-tree', self.legacy + '^{tree}', '-p', added, '-m', 'revert gitlink')
                    error = 'Untested commit'
                with self.assertRaisesRegex(ValueError, error):
                    self.verify(head, main)

    def test_acceptance_and_environment_cannot_be_revised_by_main_sync(self):
        for path, data in ((PROMOTION, {}), ('docs/verification/environments/rabbit1.json', {}),
                           ('docs/verification/scopes/issue-3.json', {}),
                           ('docs/verification/changes/issue-2.json', self.acceptance(2)),
                           ('docs/verification/changes/issue-1.json', self.acceptance(1)),
                           ('docs/verification/changes/issue-3.json', dict(self.acceptance(3), gui_required=True))):
            with self.subTest(path=path):
                self.git('checkout', '--detach', self.main)
                self.write(path, dict(data, reason='changed policy'))
                main = self.commit('acceptance mutation')
                # Use an arbitrary merge tree so conflicting inputs still reach the policy rejection.
                head = self.git('commit-tree', main + '^{tree}', '-p', self.legacy, '-p', main, '-m', 'sync')
                with self.assertRaisesRegex(ValueError, 'acceptance|GUI|Cases'):
                    self.verify(head, main)

    def test_unrelated_merge_parent_or_reversed_first_parent_is_rejected(self):
        head = self.merge()
        tree = self.git('rev-parse', head + '^{tree}')
        foreign = self.git('commit-tree', tree, '-p', self.old_main, '-m', 'foreign branch')
        for parents in ((self.legacy, foreign), (self.main, self.legacy), (self.legacy, self.main, foreign)):
            with self.subTest(parents=len(parents)):
                args = [item for parent in parents for item in ('-p', parent)]
                bad = self.git('commit-tree', tree, *args, '-m', 'bad parents')
                with self.assertRaises(ValueError):
                    self.verify(bad)

    def test_conflicting_main_update_requires_new_candidate(self):
        self.write('docs/shared.md', 'main changes the candidate line\n' + ''.join(self.lines[1:]))
        main = self.commit('conflicting main')
        # Even a plausible resolution cannot replace a clean merge.
        bad = self.git('commit-tree', main + '^{tree}', '-p', self.legacy, '-p', main, '-m', 'manual resolution')
        with self.assertRaises(subprocess.CalledProcessError):
            self.verify(bad, main)


if __name__ == '__main__':
    unittest.main()
