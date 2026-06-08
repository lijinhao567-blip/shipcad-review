from tools.check_rule_golden_coverage import validate


def test_accepts_positive_and_negative_coverage():
    cases = [
        {"id": "rule_a_positive", "expectedRuleCodes": ["RULE_A"], "expectedIssueCount": 1},
        {"id": "rule_b_positive", "expectedRuleCodes": ["RULE_B"], "expectedIssueCount": 1},
        {"id": "clean", "expectedRuleCodes": [], "expectedIssueCount": 0},
    ]

    assert validate(["RULE_A", "RULE_B"], cases) == []


def test_rejects_missing_positive_unknown_rule_and_bad_issue_count():
    cases = [
        {"id": "bad", "expectedRuleCodes": ["UNKNOWN_RULE"], "expectedIssueCount": 0},
        {"id": "clean", "expectedRuleCodes": [], "expectedIssueCount": 0},
    ]

    errors = validate(["RULE_A"], cases)

    assert "bad: references unknown rule codes ['UNKNOWN_RULE']" in errors
    assert "bad: expectedIssueCount=0 is smaller than expectedRuleCodes=['UNKNOWN_RULE']" in errors
    assert "RULE_A: missing at least one positive golden case" in errors


def test_rejects_duplicate_rule_codes():
    cases = [
        {"id": "duplicate", "expectedRuleCodes": ["RULE_A", "RULE_A"], "expectedIssueCount": 1},
        {"id": "clean", "expectedRuleCodes": [], "expectedIssueCount": 0},
    ]

    errors = validate(["RULE_A"], cases)

    assert "duplicate: duplicate expectedRuleCodes ['RULE_A']" in errors
    assert "duplicate: expectedIssueCount=1 is smaller than expectedRuleCodes=['RULE_A', 'RULE_A']" in errors
