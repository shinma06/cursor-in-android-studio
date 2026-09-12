"""Check public observation consistency; does not replay live or prove goal lifecycle."""
import json
from pathlib import Path


def main():
    root = Path(__file__).resolve().parent
    data = json.loads((root / 'issue-278-observations.json').read_text())
    assert data['cli_version'] == '2026.09.10-fd3934a'
    for schema in data['schemas'].values():
        assert schema['prompt_request_properties'] == ['sessionId', 'prompt', '_meta']
        assert schema['session_notification_properties'] == ['sessionId', 'update', '_meta']
        assert set(schema['goal_word_definition_names']) == {'Plan', 'PlanEntry', 'PlanEntryPriority'}
    catalog = data['catalog']
    assert catalog['prompt_requests'] == 0
    assert catalog['goal_command']['name'] == 'goal'
    assert catalog['btw_advertised'] is catalog['side_advertised'] is False
    assert data['acp']['prompt_requests'] == data['print']['prompt_requests'] == 1
    assert data['acp']['answer'] == data['print']['answer'] == 'FIVE_278'
    assert data['acp']['stopReason'] == 'end_turn'
    assert data['print']['exit'] == 0 and data['print']['is_error'] is False
    assert data['acp']['goal_state_event_observed'] is data['print']['goal_state_event_observed'] is False
    assert data['acp']['client_callback_requests'] == data['stderr_bytes'] == data['workspace_file_count_after'] == 0
    assert sum(data['acp']['update_counts'].values()) == 20
    assert sum(data['print']['event_counts'].values()) == 16
    assert 'simultaneous ACP prompts' in data['not_executed']
    public = json.dumps(data)
    assert not any(value in public for value in ('/Users/', '/tmp/', 'session_id', 'request_id', 'apiKeySource'))
    print('PASS: schema projection, 0-prompt catalog, 2 finite goals, no goal-state claim, public field boundary')


if __name__ == '__main__':
    main()
