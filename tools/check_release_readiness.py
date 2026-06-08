from __future__ import annotations

import argparse
import json
import subprocess
import sys
from dataclasses import asdict, dataclass
from pathlib import Path


REQUIRED_FILES = [
    "AGENTS.md",
    "README.md",
    "LICENSE",
    "SECURITY.md",
    "CONTRIBUTING.md",
    "CODE_OF_CONDUCT.md",
    "THIRD_PARTY_LICENSES.md",
    ".github/dependabot.yml",
    ".github/workflows/ci.yml",
    ".github/workflows/codeql.yml",
    ".github/workflows/dependency-review.yml",
    ".github/workflows/sbom.yml",
    ".github/workflows/secret-scan.yml",
    ".gitleaks.toml",
    "datasets/vision/classes.json",
    "datasets/vision/data.yaml",
    "datasets/vision/manifest.json",
]
PROHIBITED_SUFFIXES = {
    ".env",
    ".key",
    ".onnx",
    ".p12",
    ".pem",
    ".pfx",
    ".pt",
    ".engine",
    ".db",
    ".doc",
    ".docx",
    ".mv.db",
    ".pptx",
    ".xlsx",
}
MAX_GITHUB_FILE_BYTES = 100 * 1024 * 1024
WARNING_FILE_BYTES = 50 * 1024 * 1024


@dataclass
class Check:
    name: str
    status: str
    detail: str


def git(repo: Path, *args: str, input_text: str | None = None) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["git", *args],
        cwd=repo,
        input=input_text,
        text=True,
        capture_output=True,
        check=False,
    )


def tracked_files(repo: Path) -> list[str]:
    result = git(repo, "ls-files", "-z")
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or "git ls-files failed")
    return [item for item in result.stdout.split("\0") if item]


def largest_history_blob(repo: Path) -> tuple[int, str]:
    objects = git(repo, "rev-list", "--objects", "--all")
    if objects.returncode != 0:
        raise RuntimeError(objects.stderr.strip() or "git rev-list failed")
    object_paths: dict[str, str] = {}
    for line in objects.stdout.splitlines():
        object_id, _, path = line.partition(" ")
        if path:
            object_paths[object_id] = path
    if not object_paths:
        return 0, ""

    batch_input = "".join(f"{object_id}\n" for object_id in object_paths)
    sizes = git(
        repo,
        "cat-file",
        "--batch-check=%(objectname) %(objecttype) %(objectsize)",
        input_text=batch_input,
    )
    if sizes.returncode != 0:
        raise RuntimeError(sizes.stderr.strip() or "git cat-file failed")
    largest_size = 0
    largest_path = ""
    for line in sizes.stdout.splitlines():
        parts = line.split()
        if len(parts) != 3 or parts[1] != "blob":
            continue
        size = int(parts[2])
        if size > largest_size:
            largest_size = size
            largest_path = object_paths.get(parts[0], "")
    return largest_size, largest_path


def run_python_check(repo: Path, script: str) -> Check:
    result = subprocess.run(
        [sys.executable, script],
        cwd=repo,
        text=True,
        capture_output=True,
        check=False,
    )
    output = (result.stdout or result.stderr).strip().splitlines()
    detail = output[-1] if output else f"{script} exited with {result.returncode}"
    return Check(script, "PASS" if result.returncode == 0 else "FAIL", detail)


def main() -> int:
    parser = argparse.ArgumentParser(description="Check whether the repository is ready for its first GitHub push.")
    parser.add_argument("--repo", type=Path, default=Path.cwd())
    parser.add_argument("--allow-missing-remote", action="store_true")
    parser.add_argument("--allow-dirty", action="store_true")
    parser.add_argument("--skip-secret-scan", action="store_true")
    parser.add_argument("--report", type=Path, default=Path(".run/release-preflight.json"))
    args = parser.parse_args()

    repo = args.repo.resolve()
    checks: list[Check] = []
    if git(repo, "rev-parse", "--is-inside-work-tree").stdout.strip() != "true":
        print(f"Not a Git repository: {repo}", file=sys.stderr)
        return 2

    missing = [path for path in REQUIRED_FILES if not (repo / path).is_file()]
    checks.append(
        Check(
            "required repository files",
            "FAIL" if missing else "PASS",
            f"missing: {', '.join(missing)}" if missing else f"{len(REQUIRED_FILES)} required files present",
        )
    )

    status = git(repo, "status", "--porcelain").stdout.strip()
    checks.append(
        Check(
            "clean worktree",
            "WARN" if status and args.allow_dirty else ("FAIL" if status else "PASS"),
            "worktree contains uncommitted changes" if status else "worktree is clean",
        )
    )

    remotes = [line for line in git(repo, "remote").stdout.splitlines() if line.strip()]
    remote_status = "PASS"
    if not remotes:
        remote_status = "WARN" if args.allow_missing_remote else "FAIL"
    checks.append(
        Check(
            "Git remote",
            remote_status,
            f"configured remotes: {', '.join(remotes)}" if remotes else "no Git remote configured",
        )
    )

    branch = git(repo, "branch", "--show-current").stdout.strip()
    checks.append(
        Check(
            "default branch candidate",
            "PASS" if branch in {"main", "master"} else "WARN",
            branch or "detached HEAD",
        )
    )

    files = tracked_files(repo)
    prohibited = [
        path
        for path in files
        if any(path.lower().endswith(suffix) for suffix in PROHIBITED_SUFFIXES)
        or Path(path).name.lower().startswith(".env")
        or Path(path).name.lower() in {"id_rsa", "id_ed25519"}
    ]
    checks.append(
        Check(
            "prohibited tracked files",
            "FAIL" if prohibited else "PASS",
            f"tracked: {', '.join(prohibited)}" if prohibited else "no model weights, secrets, or database files tracked",
        )
    )

    current_sizes = [(repo / path).stat().st_size for path in files if (repo / path).is_file()]
    current_largest = max(current_sizes, default=0)
    history_size, history_path = largest_history_blob(repo)
    largest = max(current_largest, history_size)
    size_status = "FAIL" if largest >= MAX_GITHUB_FILE_BYTES else ("WARN" if largest >= WARNING_FILE_BYTES else "PASS")
    checks.append(
        Check(
            "GitHub file size",
            size_status,
            f"largest reachable blob: {history_path or 'current file'} ({largest} bytes)",
        )
    )

    checks.append(run_python_check(repo, "tools/check_python_requirements.py"))
    checks.append(run_python_check(repo, "tools/check_action_pins.py"))
    checks.append(
        run_python_check(repo, "tools/validate_vision_dataset.py")
    )
    if not args.skip_secret_scan:
        checks.append(run_python_check(repo, "tools/run_secret_scan.py"))

    report_path = args.report if args.report.is_absolute() else repo / args.report
    report_path.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "repository": str(repo),
        "ready": all(check.status != "FAIL" for check in checks),
        "checks": [asdict(check) for check in checks],
    }
    report_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    for check in checks:
        print(f"[{check.status}] {check.name}: {check.detail}")
    print(f"Report: {report_path}")
    return 0 if payload["ready"] else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError, ValueError, json.JSONDecodeError) as exc:
        print(f"Release preflight failed: {exc}", file=sys.stderr)
        raise SystemExit(2) from exc
