from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import urllib.request
from pathlib import Path
from typing import Any

from PIL import Image, ImageStat

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from cad_worker.app.parser import parse_dxf
from cad_worker.app.renderer import render_dxf_to_png


DEFAULT_MANIFEST = ROOT / "datasets" / "external" / "manifest.json"
DEFAULT_CACHE = ROOT / ".run" / "external-dxf-candidates"
ALLOWED_LICENSES = {"MIT", "CC-BY-SA-4.0"}
COMMIT_PATTERN = re.compile(r"^[0-9a-f]{40}$")
HASH_PATTERN = re.compile(r"^[0-9a-f]{64}$")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_manifest(path: Path) -> dict[str, Any]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(payload, dict):
        raise ValueError(f"{path} must contain a JSON object")
    samples = payload.get("samples")
    if not isinstance(samples, list) or not samples:
        raise ValueError(f"{path} must contain a non-empty samples array")
    return payload


def resolve_cache_path(cache_root: Path, cache_file: str) -> Path:
    root = cache_root.resolve()
    target = (root / cache_file).resolve()
    try:
        target.relative_to(root)
    except ValueError as exc:
        raise ValueError(f"cacheFile escapes cache directory: {cache_file}") from exc
    return target


def validate_manifest(payload: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    ids: set[str] = set()
    cache_files: set[str] = set()
    if payload.get("storagePolicy") != "Remote DXF files are not vendored. Validation downloads pinned copies into .run/external-dxf-candidates/.":
        errors.append("storagePolicy must declare remote-cache-only storage")

    for sample in payload["samples"]:
        sample_id = str(sample.get("id") or "<missing-id>")
        if sample_id in ids:
            errors.append(f"{sample_id}: duplicate id")
        ids.add(sample_id)

        cache_file = sample.get("cacheFile")
        if not isinstance(cache_file, str) or not cache_file.lower().endswith(".dxf"):
            errors.append(f"{sample_id}: cacheFile must be a DXF filename")
        elif cache_file in cache_files:
            errors.append(f"{sample_id}: duplicate cacheFile {cache_file}")
        else:
            cache_files.add(cache_file)
            try:
                resolve_cache_path(DEFAULT_CACHE, cache_file)
            except ValueError as exc:
                errors.append(f"{sample_id}: {exc}")

        commit = str(sample.get("sourceCommit") or "")
        if not COMMIT_PATTERN.fullmatch(commit):
            errors.append(f"{sample_id}: sourceCommit must be a 40-character lowercase Git commit")
        source_url = str(sample.get("sourceUrl") or "")
        if not source_url.startswith("https://raw.githubusercontent.com/") or f"/{commit}/" not in source_url:
            errors.append(f"{sample_id}: sourceUrl must be an immutable raw GitHub URL pinned to sourceCommit")
        if sample.get("repositoryInclusion") != "remote-cache-only":
            errors.append(f"{sample_id}: repositoryInclusion must be remote-cache-only")
        if sample.get("license") not in ALLOWED_LICENSES:
            errors.append(f"{sample_id}: unsupported or unclear license {sample.get('license')!r}")
        for key in ("sourceRepository", "sourcePath", "sourcePage", "licenseUrl", "attribution"):
            if not isinstance(sample.get(key), str) or not sample[key].strip():
                errors.append(f"{sample_id}: {key} is required")

        digest = str(sample.get("sha256") or "")
        if not HASH_PATTERN.fullmatch(digest):
            errors.append(f"{sample_id}: sha256 must be a 64-character lowercase digest")
        if int(sample.get("fileSize") or 0) <= 0:
            errors.append(f"{sample_id}: fileSize must be positive")
        if not isinstance(sample.get("parserExpectations"), dict):
            errors.append(f"{sample_id}: parserExpectations must be an object")
        if not isinstance(sample.get("previewExpectations"), dict):
            errors.append(f"{sample_id}: previewExpectations must be an object")
    return errors


def ensure_cached(sample: dict[str, Any], cache_root: Path, offline: bool, refresh: bool) -> Path:
    target = resolve_cache_path(cache_root, sample["cacheFile"])
    expected_hash = sample["sha256"]
    expected_size = int(sample["fileSize"])
    if target.is_file() and not refresh and target.stat().st_size == expected_size and sha256(target) == expected_hash:
        return target
    if offline:
        raise ValueError(f"{sample['id']}: valid cached file is unavailable in offline mode")

    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_suffix(target.suffix + ".part")
    request = urllib.request.Request(
        sample["sourceUrl"],
        headers={"User-Agent": "ShipCAD-Review-external-dataset-validator/0.1"},
    )
    try:
        with urllib.request.urlopen(request, timeout=60) as response, temporary.open("wb") as output:
            while chunk := response.read(1024 * 1024):
                output.write(chunk)
        if temporary.stat().st_size != expected_size:
            raise ValueError(
                f"{sample['id']}: downloaded size {temporary.stat().st_size} != expected {expected_size}"
            )
        actual_hash = sha256(temporary)
        if actual_hash != expected_hash:
            raise ValueError(f"{sample['id']}: downloaded sha256 {actual_hash} != expected {expected_hash}")
        temporary.replace(target)
    finally:
        temporary.unlink(missing_ok=True)
    return target


def validate_parser_expectations(
    sample_id: str,
    summary: dict[str, Any],
    expectations: dict[str, Any],
) -> list[str]:
    errors: list[str] = []
    entity_count = int(summary.get("entityCount") or 0)
    minimum = int(expectations.get("minEntityCount") or 0)
    if entity_count < minimum:
        errors.append(f"{sample_id}: entityCount {entity_count} < {minimum}")

    layers = set(summary.get("layers") or [])
    for layer in expectations.get("requiredLayers") or []:
        if layer not in layers:
            errors.append(f"{sample_id}: missing required layer {layer}")

    type_counts = summary.get("typeCounts") or {}
    for entity_type in expectations.get("requiredEntityTypes") or []:
        if int(type_counts.get(entity_type) or 0) <= 0:
            errors.append(f"{sample_id}: missing required entity type {entity_type}")
    for entity_type, expected_min in (expectations.get("minTypeCounts") or {}).items():
        actual = int(type_counts.get(entity_type) or 0)
        if actual < int(expected_min):
            errors.append(f"{sample_id}: {entity_type} count {actual} < {expected_min}")

    texts = "\n".join(str(text) for text in summary.get("texts") or [])
    for snippet in expectations.get("requiredTexts") or []:
        if str(snippet) not in texts:
            errors.append(f"{sample_id}: missing required text snippet {snippet!r}")

    bounds = summary.get("bounds") or {}
    width = float(bounds.get("maxX", 0)) - float(bounds.get("minX", 0))
    height = float(bounds.get("maxY", 0)) - float(bounds.get("minY", 0))
    expected_bounds = expectations.get("bounds") or {}
    if width < float(expected_bounds.get("minWidth") or 0):
        errors.append(f"{sample_id}: model width {width:.2f} is below expected minimum")
    if height < float(expected_bounds.get("minHeight") or 0):
        errors.append(f"{sample_id}: model height {height:.2f} is below expected minimum")
    return errors


def validate_render(sample_id: str, path: Path, output: Path, expectations: dict[str, Any]) -> tuple[list[str], dict[str, Any]]:
    errors: list[str] = []
    metadata = render_dxf_to_png(path, output, width=1200, height=900)
    if not output.read_bytes().startswith(b"\x89PNG\r\n\x1a\n"):
        errors.append(f"{sample_id}: render output is not a PNG")
    minimum = int(expectations.get("minRenderBytes") or 5000)
    if output.stat().st_size < minimum:
        errors.append(f"{sample_id}: rendered PNG size {output.stat().st_size} < {minimum}")
    if is_blank_image(output):
        errors.append(f"{sample_id}: rendered PNG appears blank")
    return errors, metadata


def is_blank_image(path: Path) -> bool:
    with Image.open(path) as image:
        grayscale = image.convert("L")
        extrema = grayscale.getextrema()
        stat = ImageStat.Stat(grayscale)
        deviation = stat.stddev[0] if stat.stddev else 0
        return extrema[0] == extrema[1] or (extrema[1] - extrema[0] < 4 and deviation < 1)


def validate_sample(
    sample: dict[str, Any],
    cache_root: Path,
    offline: bool,
    refresh: bool,
    render: bool,
) -> tuple[list[str], dict[str, Any]]:
    sample_id = sample["id"]
    errors: list[str] = []
    path = ensure_cached(sample, cache_root, offline=offline, refresh=refresh)
    if path.stat().st_size != int(sample["fileSize"]):
        errors.append(f"{sample_id}: cached file size mismatch")
    if sha256(path) != sample["sha256"]:
        errors.append(f"{sample_id}: cached sha256 mismatch")

    parsed = parse_dxf(path)
    summary = parsed["summary"]
    errors.extend(validate_parser_expectations(sample_id, summary, sample["parserExpectations"]))
    result: dict[str, Any] = {
        "id": sample_id,
        "cacheFile": str(path),
        "sha256": sha256(path),
        "entityCount": summary.get("entityCount"),
        "layerCount": len(summary.get("layers") or []),
        "typeCounts": summary.get("typeCounts") or {},
        "bounds": summary.get("bounds") or {},
    }
    if render:
        render_dir = cache_root / "renders"
        render_dir.mkdir(parents=True, exist_ok=True)
        output = render_dir / f"{sample_id}.png"
        render_errors, metadata = validate_render(sample_id, path, output, sample["parserExpectations"])
        errors.extend(render_errors)
        result["renderFile"] = str(output)
        result["renderBytes"] = output.stat().st_size
        result["renderMetadata"] = metadata
    return errors, result


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Download and validate pinned external DXF regression candidates.")
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--cache-dir", type=Path, default=DEFAULT_CACHE)
    parser.add_argument("--manifest-only", action="store_true", help="Validate tracked metadata without network access")
    parser.add_argument("--offline", action="store_true", help="Use only already cached files")
    parser.add_argument("--refresh", action="store_true", help="Download pinned files even when cache is valid")
    parser.add_argument("--skip-render", action="store_true")
    parser.add_argument("--report", type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    manifest_path = args.manifest.resolve()
    payload = load_manifest(manifest_path)
    errors = validate_manifest(payload)
    if args.manifest_only:
        if errors:
            return print_errors(errors)
        print(f"External DXF manifest: {len(payload['samples'])} candidates validated")
        return 0

    results: list[dict[str, Any]] = []
    cache_root = args.cache_dir.resolve()
    if not errors:
        for sample in payload["samples"]:
            try:
                sample_errors, result = validate_sample(
                    sample,
                    cache_root,
                    offline=args.offline,
                    refresh=args.refresh,
                    render=not args.skip_render,
                )
                errors.extend(sample_errors)
                results.append(result)
            except Exception as exc:
                errors.append(f"{sample.get('id', '<missing-id>')}: {exc}")

    report_path = args.report.resolve() if args.report else cache_root / "report.json"
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(
        json.dumps(
            {
                "manifest": str(manifest_path),
                "valid": not errors,
                "samples": results,
                "errors": errors,
            },
            ensure_ascii=False,
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
    if errors:
        return print_errors(errors)
    print(f"External DXF candidates: {len(results)} samples validated; report={report_path}")
    return 0


def print_errors(errors: list[str]) -> int:
    print("External DXF candidate validation failed:", file=sys.stderr)
    for error in errors:
        print(f"- {error}", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
