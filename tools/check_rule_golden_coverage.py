from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_INITIALIZER = ROOT / "backend-spring" / "src" / "main" / "java" / "com" / "shipcad" / "review" / "config" / "DataInitializer.java"
DEFAULT_MANIFEST = ROOT / "datasets" / "rules" / "expected.json"


def default_rule_codes(path: Path) -> list[str]:
    content = path.read_text(encoding="utf-8")
    return sorted(set(re.findall(r'addRuleIfMissing\("([^"]+)"', content)))


def load_manifest(path: Path) -> list[dict[str, Any]]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(payload, list):
        raise ValueError(f"{path} must contain a JSON array")
    return payload


def validate(rule_codes: list[str], cases: list[dict[str, Any]]) -> list[str]:
    errors: list[str] = []
    if not rule_codes:
        errors.append("no default rule codes found")
    known = set(rule_codes)
    hit_cases = {rule: [] for rule in rule_codes}
    clean_cases = {rule: [] for rule in rule_codes}

    for case in cases:
        case_id = str(case.get("id") or "<missing-id>")
        expected_rules = case.get("expectedRuleCodes")
        if not isinstance(expected_rules, list):
            errors.append(f"{case_id}: expectedRuleCodes must be a list")
            expected_rules = []
        expected_list = [str(rule) for rule in expected_rules]
        expected_set = set(expected_list)
        duplicates = sorted({rule for rule in expected_list if expected_list.count(rule) > 1})
        if duplicates:
            errors.append(f"{case_id}: duplicate expectedRuleCodes {duplicates}")
        unknown = sorted(expected_set - known)
        if unknown:
            errors.append(f"{case_id}: references unknown rule codes {unknown}")

        expected_issue_count = case.get("expectedIssueCount")
        if not isinstance(expected_issue_count, int) or expected_issue_count < 0:
            errors.append(f"{case_id}: expectedIssueCount must be a non-negative integer")
        elif expected_issue_count < len(expected_list):
            errors.append(
                f"{case_id}: expectedIssueCount={expected_issue_count} is smaller than expectedRuleCodes={expected_list}"
            )

        for rule in rule_codes:
            if rule in expected_set:
                hit_cases[rule].append(case_id)
            else:
                clean_cases[rule].append(case_id)

    for rule in rule_codes:
        if not hit_cases[rule]:
            errors.append(f"{rule}: missing at least one positive golden case")
        if not clean_cases[rule]:
            errors.append(f"{rule}: missing at least one negative golden case")

    return errors


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate rule coverage in datasets/rules/expected.json.")
    parser.add_argument("--initializer", type=Path, default=DEFAULT_INITIALIZER)
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    rule_codes = default_rule_codes(args.initializer)
    cases = load_manifest(args.manifest)
    errors = validate(rule_codes, cases)
    if errors:
        print("Rule golden coverage failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1
    print(f"Rule golden coverage: {len(rule_codes)} rules covered by {len(cases)} cases")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
