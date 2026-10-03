"""Check public observation consistency; does not replay live or prove goal lifecycle."""
import hashlib
import json
from pathlib import Path


# Exact allowlist for this one reviewed public snapshot, not a raw-wire sanitizer.
# A new observation requires a separate privacy review before changing this digest.
PUBLIC_PROJECTION_SHA256 = "056ff0f5708b5aeadb5a2b6be63f7df55deea7c385a318b782f31cf8cd343253"


def unique_object(pairs):
    data = dict(pairs)
    if len(data) != len(pairs):
        raise ValueError('Duplicate public projection key')
    return data


def read_projection(text):
    data = json.loads(text, object_pairs_hook=unique_object)
    canonical = json.dumps(data, sort_keys=True, ensure_ascii=False, separators=(',', ':')).encode()
    if hashlib.sha256(canonical).hexdigest() != PUBLIC_PROJECTION_SHA256:
        raise ValueError('Projection differs from the reviewed public snapshot')
    return data


def main():
    root = Path(__file__).resolve().parent
    data = read_projection((root / 'issue-278-observations.json').read_text())
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
    print('PASS: schema projection, 0-prompt catalog, 2 finite goals, no goal-state claim, reviewed public snapshot integrity')


if __name__ == '__main__':
    main()
