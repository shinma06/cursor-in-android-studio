"""Qualify the one native queue field missing from pinned actionlint."""
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import actionlint_check as lint

SOURCE = ('name: Synthetic delivery\non: workflow_dispatch\n' + lint.CONCURRENCY +
          'jobs:\n  check:\n    runs-on: ubuntu-latest\n    steps:\n      - run: echo synthetic\n')


class ActionlintCheckTest(unittest.TestCase):
    def test_projection_only_blanks_the_validated_queue_line_and_keeps_line_numbers(self):
        before = SOURCE.splitlines(keepends=True)
        after = lint.delivery_projection(SOURCE).splitlines(keepends=True)
        self.assertEqual(len(before), len(after))
        self.assertEqual([(a, b) for a, b in zip(before, after) if a != b], [('  queue: max\n', '\n')])

    def test_changed_missing_duplicate_or_non_top_level_contract_stops_before_lint(self):
        for source in (SOURCE.replace('queue: max', 'queue: single'),
                       SOURCE.replace('queue: max', 'queue: unknown'),
                       SOURCE.replace('  queue: max\n', ''),
                       SOURCE.replace('cancel-in-progress: false', 'cancel-in-progress: true'),
                       SOURCE.replace('  queue: max\n', '  queue: max\n  queue: max\n'),
                       SOURCE + lint.CONCURRENCY,
                       SOURCE.replace('concurrency:', 'other-concurrency:')):
            with self.subTest(source=source), self.assertRaises(ValueError):
                lint.delivery_projection(source)

    def test_other_unsupported_fields_and_nested_queues_remain_for_the_linter(self):
        extra = '\n  nested:\n    concurrency:\n      group: synthetic\n      queue: max\n'
        source = SOURCE + extra + 'unexpected-key: true\n'
        result = lint.delivery_projection(source)
        self.assertIn(extra, result)
        self.assertIn('unexpected-key: true', result)

    def test_all_other_workflows_are_linted_as_files_and_delivery_uses_qualified_stdin(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder); workflows = root / '.github/workflows'; workflows.mkdir(parents=True)
            (root / lint.DELIVERY).write_text(SOURCE)
            (workflows / 'other.yml').write_text('synthetic yml')
            (workflows / 'another.yaml').write_text('synthetic yaml')
            with patch.object(lint, 'ROOT', root), patch.object(lint.subprocess, 'run') as run:
                lint.check(Path('/synthetic/actionlint'))
                self.assertEqual(run.call_count, 2)
                first, second = run.call_args_list
                self.assertEqual(first.args[0], ['/synthetic/actionlint', '-shellcheck=', '-pyflakes=',
                                                '.github/workflows/another.yaml', '.github/workflows/other.yml'])
                self.assertEqual(second.args[0], ['/synthetic/actionlint', '-shellcheck=', '-pyflakes=',
                                                 '-stdin-filename', str(lint.DELIVERY), '-'])
                self.assertEqual(second.kwargs['input'], lint.delivery_projection(SOURCE))
                self.assertTrue(all(call.kwargs['check'] and call.kwargs['cwd'] == root for call in run.call_args_list))
                self.assertEqual((root / lint.DELIVERY).read_text(), SOURCE)
                for failure in (0, 1):
                    run.reset_mock()
                    run.side_effect = [None] * failure + [subprocess.CalledProcessError(1, 'synthetic-actionlint')]
                    with self.assertRaises(subprocess.CalledProcessError): lint.check(Path('/synthetic/actionlint'))
                    self.assertEqual(run.call_count, failure + 1)
                (root / lint.DELIVERY).write_text(SOURCE.replace('queue: max', 'queue: single'))
                run.reset_mock()
                with self.assertRaises(ValueError): lint.check(Path('/synthetic/actionlint'))
                run.assert_not_called()

    def test_repository_delivery_and_ci_keep_the_native_queue_and_pinned_checks(self):
        source = (lint.ROOT / lint.DELIVERY).read_text()
        self.assertIn(lint.CONCURRENCY, source)
        lint.delivery_projection(source)
        self.assertIn("if: ${{ false && github.ref == 'refs/heads/main' }}", source)
        ci = (lint.ROOT / '.github/workflows/ci.yml').read_text()
        self.assertIn('python3 scripts/workflow/actionlint_check.py --actionlint /tmp/actionlint', ci)
        self.assertIn('/v1.7.12/actionlint_1.7.12_linux_amd64.tar.gz', ci)
        self.assertIn('8aca8db96f1b94770f1b0d72b6dddcb1ebb8123cb3712530b08cc387b349a3d8', ci)


if __name__ == '__main__':
    unittest.main()
