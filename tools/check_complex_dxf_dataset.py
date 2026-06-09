from __future__ import annotations

import argparse
import hashlib
import json
import sys
import tempfile
from pathlib import Path
from typing import Any

from PIL import Image, ImageStat

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from cad_worker.app.parser import parse_dxf
from cad_worker.app.renderer import render_dxf_to_png


DEFAULT_MANIFEST = ROOT / "datasets" / "parser" / "manifest.json"


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


def validate_sample(root: Path, sample: dict[str, Any], render: bool) -> list[str]:
    errors: list[str] = []
    sample_id = str(sample.get("id") or "<missing-id>")
    relative_file = sample.get("file")
    if not isinstance(relative_file, str):
        return [f"{sample_id}: file must be a string"]

    path = (root / relative_file).resolve()
    if not path.exists():
        return [f"{sample_id}: file does not exist: {relative_file}"]
    if path.suffix.lower() != ".dxf":
        errors.append(f"{sample_id}: file must be a DXF")

    if sample.get("source") != "Self-created synthetic CAD fixture generated inside this repository.":
        errors.append(f"{sample_id}: source must declare self-created synthetic provenance")
    if sample.get("license") != "AGPL-3.0-only":
        errors.append(f"{sample_id}: license must be AGPL-3.0-only")
    if sample.get("generatedBy") != "tools/generate_complex_dxf_dataset.py":
        errors.append(f"{sample_id}: generatedBy must point to tools/generate_complex_dxf_dataset.py")

    expected_hash = sample.get("sha256")
    actual_hash = sha256(path)
    if expected_hash != actual_hash:
        errors.append(f"{sample_id}: sha256 mismatch expected={expected_hash} actual={actual_hash}")

    parsed = parse_dxf(path)
    summary = parsed["summary"]
    expectations = sample.get("parserExpectations") or {}
    errors.extend(validate_parser_expectations(sample_id, summary, parsed["entities"], expectations))

    if render:
        errors.extend(validate_render(sample_id, path, sample.get("previewExpectations") or {}))
    return errors


def validate_parser_expectations(sample_id: str, summary: dict[str, Any], entities: list[dict[str, Any]], expectations: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    entity_count = int(summary.get("entityCount") or 0)
    min_entity_count = int(expectations.get("minEntityCount") or 0)
    if entity_count < min_entity_count:
        errors.append(f"{sample_id}: entityCount {entity_count} < {min_entity_count}")

    layers = set(summary.get("layers") or [])
    for layer in expectations.get("requiredLayers") or []:
        if layer not in layers:
            errors.append(f"{sample_id}: missing required layer {layer}")

    blocks = set(summary.get("blocks") or [])
    for block in expectations.get("requiredBlocks") or []:
        if block not in blocks:
            errors.append(f"{sample_id}: missing required block {block}")

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

    hatch_entities = [entity for entity in entities if entity.get("entityType") == "HATCH"]
    if "HATCH" in (expectations.get("requiredEntityTypes") or []) and not all((entity.get("geometry") or {}).get("bounds") for entity in hatch_entities):
        errors.append(f"{sample_id}: every HATCH entity must include geometry bounds")
    return errors


def validate_render(sample_id: str, path: Path, expectations: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    with tempfile.TemporaryDirectory() as temp_dir:
        output = Path(temp_dir) / f"{path.stem}.png"
        metadata = render_dxf_to_png(path, output, width=1200, height=900)
        if not output.read_bytes().startswith(b"\x89PNG\r\n\x1a\n"):
            errors.append(f"{sample_id}: render output is not a PNG")
        if output.stat().st_size < int(expectations.get("minRenderBytes") or 5000):
            errors.append(f"{sample_id}: rendered PNG is unexpectedly small")

        model_bounds = metadata.get("modelBounds") or {}
        if float(model_bounds.get("minX", 0)) >= float(model_bounds.get("maxX", 0)):
            errors.append(f"{sample_id}: render metadata has invalid X bounds")
        if float(model_bounds.get("minY", 0)) >= float(model_bounds.get("maxY", 0)):
            errors.append(f"{sample_id}: render metadata has invalid Y bounds")
        if is_blank_image(output):
            errors.append(f"{sample_id}: rendered PNG appears blank")
    return errors


def is_blank_image(path: Path) -> bool:
    with Image.open(path) as image:
        grayscale = image.convert("L")
        extrema = grayscale.getextrema()
        stat = ImageStat.Stat(grayscale)
        return extrema[0] == extrema[1] or (extrema[1] - extrema[0] < 4 and (stat.stddev[0] if stat.stddev else 0) < 1)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate complex synthetic DXF parser and render fixtures.")
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--skip-render", action="store_true", help="Only validate hashes and parser expectations")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    manifest_path = args.manifest.resolve()
    manifest = load_manifest(manifest_path)
    root = manifest_path.parent
    errors: list[str] = []
    for sample in manifest["samples"]:
        errors.extend(validate_sample(root, sample, render=not args.skip_render))

    if errors:
        print("Complex DXF dataset validation failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1
    print(f"Complex DXF dataset: {len(manifest['samples'])} samples validated")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
