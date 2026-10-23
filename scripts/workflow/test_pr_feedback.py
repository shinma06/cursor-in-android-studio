"""Only observable current-HEAD terminal review can leave provider wait."""
import copy
import json
import unittest
from unittest.mock import patch

import agent_loop as al
from agent_policy import next_action


HEAD = 'a' * 40


def summary(state='Completed', head=HEAD[:7]):
    return {'user': {'login': 'chatgpt-codex-connector[bot]'},
            'body': '<!-- codex-pull-request-review-summary -->\n'
                    f'| **Code Review** | ✅ **{state}** <relative-time datetime="2026-10-23T00:00:00Z">now</relative-time> | `{head}` | details |'}


class ProviderFeedbackTests(unittest.TestCase):
    def test_current_completed_summary_and_newer_request(self):
        self.assertTrue(al.remote_review_complete(HEAD, [summary()]))
        later = {'user': {'login': al.OWNER}, 'body': '@codex review',
                 'created_at': '2026-10-23T00:01:00Z'}
        self.assertFalse(al.remote_review_complete(HEAD, [summary(), later]))
        self.assertEqual(next_action({}, {'remote_review_complete': False}, 'success', False), 'remote-review-wait')

    def test_missing_running_stale_ambiguous_unknown_or_forged_summary_waits(self):
        forged = summary()
        forged['user']['login'] = al.OWNER
        for comments in ([], [summary('Running')], [summary(head='b' * 7)],
                         [summary(), summary()], [forged],
                         [dict(summary(), body='Completed')]):
            with self.subTest(comments=comments):
                self.assertFalse(al.remote_review_complete(HEAD, comments))

    def test_thread_query_is_target_scoped_and_partial_or_errors_block(self):
        thread = {'data': {'repository': {'pullRequest': {'reviewThreads': {
            'nodes': [{'id': 'thread', 'isResolved': False, 'isOutdated': True}],
            'pageInfo': {'hasNextPage': False}}}}}}
        for reply, valid in (([thread], True), ([], False), ([{'errors': ['unavailable']}], False)):
            with patch.object(al, 'command', return_value=json.dumps(reply)) as command:
                if valid:
                    self.assertEqual(al.GitHub().review_threads(572)[0]['id'], 'thread')
                    query = command.call_args.args[0][-1]
                    self.assertIn('name:"cursor-in-android-studio"', query)
                    self.assertNotIn('android-replay-buffer', query)
                else:
                    with self.assertRaises(ValueError):
                        al.GitHub().review_threads(572)
        partial = copy.deepcopy(thread)
        partial['data']['repository']['pullRequest']['reviewThreads']['pageInfo']['hasNextPage'] = True
        with patch.object(al, 'command', return_value=json.dumps([partial])):
            with self.assertRaises(ValueError):
                al.GitHub().review_threads(572)


if __name__ == '__main__':
    unittest.main()
