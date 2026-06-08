from __future__ import annotations

import argparse
import hashlib
import json
import platform
import shutil
import subprocess
import sys
import tarfile
import urllib.request
import zipfile
from pathlib import Path


GITLEAKS_VERSION = "8.30.1"
RELEASE_ROOT = f"https://github.com/gitleaks/gitleaks/releases/download/v{GITLEAKS_VERSION}"


def asset_name() -> str:
    system = platform.system().lower()
    machine = platform.machine().lower()
    architecture = {
        "amd64": "x64",
        "x86_64": "x64",
        "aarch64": "arm64",
        "arm64": "arm64",
    }.get(machine)
    if architecture is None:
        raise RuntimeError(f"Unsupported architecture for Gitleaks: {machine}")
    if system == "windows":
        return f"gitleaks_{GITLEAKS_VERSION}_windows_{architecture}.zip"
    if system in {"linux", "darwin"}:
        return f"gitleaks_{GITLEAKS_VERSION}_{system}_{architecture}.tar.gz"
    raise RuntimeError(f"Unsupported operating system for Gitleaks: {system}")


def download(url: str, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    with urllib.request.urlopen(url, timeout=120) as response, destination.open("wb") as output:
        shutil.copyfileobj(response, output)


def expected_checksum(checksums_path: Path, asset: str) -> str:
    for line in checksums_path.read_text(encoding="utf-8").splitlines():
        parts = line.split()
        if len(parts) == 2 and parts[1].lstrip("*") == asset:
            return parts[0].lower()
    raise RuntimeError(f"Checksum entry not found for {asset}")


def verify_checksum(archive_path: Path, expected: str) -> None:
    digest = hashlib.sha256()
    with archive_path.open("rb") as archive:
        for chunk in iter(lambda: archive.read(1024 * 1024), b""):
            digest.update(chunk)
    actual = digest.hexdigest()
    if actual != expected:
        raise RuntimeError(f"Gitleaks checksum mismatch: expected={expected} actual={actual}")


def safe_extract_zip(archive_path: Path, destination: Path) -> None:
    destination_root = destination.resolve()
    with zipfile.ZipFile(archive_path) as archive:
        for member in archive.infolist():
            target = (destination / member.filename).resolve()
            if destination_root not in target.parents and target != destination_root:
                raise RuntimeError(f"Unsafe path in Gitleaks archive: {member.filename}")
        archive.extractall(destination)


def install_gitleaks(repo_root: Path) -> Path:
    tool_root = repo_root / ".tools" / f"gitleaks-v{GITLEAKS_VERSION}"
    executable = tool_root / ("gitleaks.exe" if platform.system().lower() == "windows" else "gitleaks")
    if executable.exists():
        return executable

    asset = asset_name()
    archive_path = tool_root / asset
    checksums_path = tool_root / f"gitleaks_{GITLEAKS_VERSION}_checksums.txt"
    if not archive_path.exists():
        download(f"{RELEASE_ROOT}/{asset}", archive_path)
    if not checksums_path.exists():
        download(f"{RELEASE_ROOT}/{checksums_path.name}", checksums_path)
    verify_checksum(archive_path, expected_checksum(checksums_path, asset))

    if asset.endswith(".zip"):
        safe_extract_zip(archive_path, tool_root)
    else:
        with tarfile.open(archive_path, "r:gz") as archive:
            archive.extractall(tool_root, filter="data")
    if not executable.exists():
        raise RuntimeError(f"Gitleaks executable was not found after extraction: {executable}")
    if platform.system().lower() != "windows":
        executable.chmod(0o755)
    return executable


def count_findings(report_path: Path) -> int:
    if not report_path.exists() or report_path.stat().st_size == 0:
        return 0
    report = json.loads(report_path.read_text(encoding="utf-8"))
    return sum(len(run.get("results", [])) for run in report.get("runs", []))


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Scan the complete Git history and current worktree with a verified Gitleaks binary."
    )
    parser.add_argument("--repo", type=Path, default=Path.cwd())
    parser.add_argument("--report-dir", type=Path, default=Path(".run/security"))
    parser.add_argument("--timeout", type=int, default=180)
    args = parser.parse_args()

    repo_root = args.repo.resolve()
    report_dir = args.report_dir if args.report_dir.is_absolute() else repo_root / args.report_dir
    report_dir.mkdir(parents=True, exist_ok=True)
    executable = install_gitleaks(repo_root)
    results: list[int] = []
    total_findings = 0
    for mode, report_name in (
        ("git", "gitleaks-history.sarif"),
        ("dir", "gitleaks-worktree.sarif"),
    ):
        report_path = report_dir / report_name
        command = [
            str(executable),
            mode,
            "--no-banner",
            "--redact",
            "--report-format",
            "sarif",
            "--report-path",
            str(report_path),
            "--timeout",
            str(args.timeout),
            str(repo_root),
        ]
        result = subprocess.run(command, cwd=repo_root, check=False)
        findings = count_findings(report_path)
        total_findings += findings
        results.append(result.returncode)
        print(f"Gitleaks {mode} findings: {findings}")
    print(f"Gitleaks v{GITLEAKS_VERSION} total findings: {total_findings}")
    return max(results, default=0)


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError, ValueError, json.JSONDecodeError) as exc:
        print(f"Secret scan failed: {exc}", file=sys.stderr)
        raise SystemExit(2) from exc
