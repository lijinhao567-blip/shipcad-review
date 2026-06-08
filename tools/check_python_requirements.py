from __future__ import annotations

import re
import sys
from pathlib import Path


REQUIREMENT_FILES = [
    Path("cad_worker/requirements.txt"),
    Path("vision_worker/requirements.txt"),
    Path("ocr_worker/requirements.txt"),
]

PINNED_REQUIREMENT = re.compile(
    r"^[A-Za-z0-9_.-]+(?:\[[A-Za-z0-9_,.-]+\])?==[A-Za-z0-9_.!+-]+(?:\s*;.+)?$"
)


def main() -> int:
    errors: list[str] = []
    for path in REQUIREMENT_FILES:
        if not path.exists():
            errors.append(f"{path}: missing requirements file")
            continue
        for line_number, raw_line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
            line = raw_line.strip()
            if not line or line.startswith("#"):
                continue
            if line.startswith(("-r ", "--requirement ", "-c ", "--constraint ")):
                errors.append(f"{path}:{line_number}: nested requirements are not allowed in worker manifests")
                continue
            if not PINNED_REQUIREMENT.match(line):
                errors.append(f"{path}:{line_number}: requirement must be pinned with ==: {line}")

    if errors:
        print("Python requirement pin check failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print(f"Checked {len(REQUIREMENT_FILES)} Python requirement files; all direct dependencies are pinned.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
