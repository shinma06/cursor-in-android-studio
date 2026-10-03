"""Offline contract oracle for #149, not a production parser/UI acceptance test."""
import json
import re
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

MAX_COUNTER = 2**63 - 1  # Current Kotlin Long boundary; larger uint64 is unknown here.
PRINT_KEYS = ('inputTokens', 'outputTokens', 'cacheReadTokens', 'cacheWriteTokens')


def counter(value):
    return (type(value) in (int, Decimal) and Decimal(value).is_finite()
            and Decimal(value) == Decimal(value).to_integral_value() and 0 <= value <= MAX_COUNTER)


def percent(value):
    if not isinstance(value, dict):
        return None
    used, size = value.get('used'), value.get('size')
    if not counter(used) or not counter(size) or size == 0 or used > size:
        return None
    return str((Decimal(used) * 100 / Decimal(size)).quantize(Decimal('0.1'), rounding=ROUND_HALF_UP))


def counters(value):
    if not isinstance(value, dict):
        return {}
    return {key: int(value[key]) for key in PRINT_KEYS if counter(value.get(key))}


def cost(case):
    value = case['input']
    if not isinstance(value, dict) or case['scope'] != 'session_cumulative' or not case['currency_confirmed']:
        return None
    amount, currency = value.get('amount'), value.get('currency')
    if type(amount) not in (int, Decimal) or not Decimal(amount).is_finite() or amount < 0:
        return None
    if not isinstance(currency, str) or re.fullmatch('[A-Z]{3}', currency) is None:
        return None
    return {'amount': str(amount), 'currency': currency}


def check():
    data = json.loads(Path(__file__).with_name('issue-149-fixtures.json').read_text(), parse_float=Decimal)
    fixtures = data['synthetic']
    for case in fixtures['context']:
        assert percent(case['input']) == case['percent'], case['id']
    for case in fixtures['print']:
        assert counters(case['input']) == case['expected'], case['id']
    for case in fixtures['cost']:
        assert cost(case) == case['display'], case['id']
    for case in fixtures['lifecycle']:
        # origin_known is an explicit test precondition, never inferred from receipt time.
        accepted = case['origin_known'] and not case['stopped'] and case['captured'] == case['current']
        assert accepted == case['accept'], case['id']
    assert percent({'used': Decimal('NaN'), 'size': 100}) is None
    assert cost({'input': {'amount': Decimal('Infinity'), 'currency': 'USD'},
                 'scope': 'session_cumulative', 'currency_confirmed': True}) is None
    live = data['live_projection']
    assert live['acp']['usage_update_count'] == live['acp']['prompt_response_usage_count'] == 0
    assert len(live['acp']['turns']) == len(live['print']) == 3
    for turn in live['print']:
        assert counters(turn['usage']) == turn['usage']
    print(f"PASS: {sum(map(len, fixtures.values()))} synthetic cases + non-finite checks; public projection consistent.")


if __name__ == '__main__':
    check()
