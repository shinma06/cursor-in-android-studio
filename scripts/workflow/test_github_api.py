"""Current consumers retain transport behavior after retiring the old publisher."""
import json
import os
import unittest
from unittest.mock import patch

import github_api as github


class GitHubTransportTests(unittest.TestCase):
    def test_repository_and_json_payload_are_forwarded_without_shell(self):
        with patch.dict(os.environ, {'GITHUB_REPOSITORY': 'owner/repo'}), \
                patch.object(github.subprocess, 'check_output', return_value='{"id": 1}') as run:
            self.assertEqual(github.api('releases', 'POST', {'body': 'literal $value'}), {'id': 1})
        self.assertEqual(run.call_args.args[0],
                         ['gh', 'api', 'repos/owner/repo/releases', '--method', 'POST', '--input', '-'])
        self.assertEqual(json.loads(run.call_args.kwargs['input']), {'body': 'literal $value'})

    def test_pages_keeps_every_page_and_stops_at_short_page(self):
        first = [{'id': i} for i in range(100)]
        with patch.object(github, 'api', side_effect=[first, [{'id': 100}]]) as api:
            self.assertEqual(github.pages('releases'), first + [{'id': 100}])
        self.assertEqual([c.args[0] for c in api.call_args_list],
                         ['releases?per_page=100&page=1', 'releases?per_page=100&page=2'])

    def test_cli_failure_is_not_an_empty_success(self):
        error = github.subprocess.CalledProcessError(1, 'gh')
        with patch.dict(os.environ, {'GITHUB_REPOSITORY': 'owner/repo'}), \
                patch.object(github.subprocess, 'check_output', side_effect=error):
            with self.assertRaises(github.subprocess.CalledProcessError):
                github.api('releases')


if __name__ == '__main__':
    unittest.main()
