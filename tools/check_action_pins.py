from __future__ import annotations

import re
import sys
from pathlib import Path


WORKFLOW_DIR = Path(".github/workflows")
USES_PATTERN = re.compile(r"^\s*(?:-\s*)?uses:\s*([^\s#]+)(?:\s+#\s*(.+))?$")
COMMIT_SHA = re.compile(r"^[0-9a-f]{40}$")


def main() -> int:
    errors: list[str] = []
    references = 0
    for path in sorted([*WORKFLOW_DIR.glob("*.yml"), *WORKFLOW_DIR.glob("*.yaml")]):
        for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
            match = USES_PATTERN.match(line)
            if not match:
                continue
            target, version_comment = match.groups()
            if target.startswith(("./", "docker://")):
                continue
            references += 1
            if "@" not in target:
                errors.append(f"{path}:{line_number}: external action has no ref: {target}")
                continue
            action, ref = target.rsplit("@", 1)
            if not COMMIT_SHA.fullmatch(ref):
                errors.append(f"{path}:{line_number}: {action} must be pinned to a 40-character commit SHA")
            if not version_comment:
                errors.append(f"{path}:{line_number}: pinned action must keep a human-readable version comment")

    if errors:
        print("GitHub Action pin check failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print(f"Checked {references} external GitHub Action references; all are pinned to immutable commits.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
