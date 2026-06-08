from __future__ import annotations

import argparse
import hashlib
import json
import sys
from dataclasses import dataclass
from pathlib import Path


IMAGE_SUFFIXES = {".jpg", ".jpeg", ".png"}
SPLITS = ("train", "val", "test")


@dataclass
class ValidationResult:
    images: int
    labels: int
    boxes: int
    errors: list[str]
    warnings: list[str]


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_json(path: Path) -> dict:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ValueError(f"cannot read {path}: {exc}") from exc


def validate_dataset(root: Path, require_samples: bool = False) -> ValidationResult:
    root = root.resolve()
    errors: list[str] = []
    warnings: list[str] = []
    classes_path = root / "classes.json"
    manifest_path = root / "manifest.json"
    data_yaml = root / "data.yaml"
    for required in (classes_path, manifest_path, data_yaml):
        if not required.is_file():
            errors.append(f"missing required file: {required.relative_to(root)}")
    if errors:
        return ValidationResult(0, 0, 0, errors, warnings)

    classes_document = load_json(classes_path)
    classes = classes_document.get("classes")
    if not isinstance(classes, list) or not classes:
        errors.append("classes.json must define a non-empty classes array")
        classes = []
    class_ids = [item.get("id") for item in classes if isinstance(item, dict)]
    class_names = [item.get("name") for item in classes if isinstance(item, dict)]
    if class_ids != list(range(len(classes))):
        errors.append("class IDs must be unique, contiguous, and ordered from 0")
    if len(class_names) != len(set(class_names)) or any(not isinstance(name, str) or not name for name in class_names):
        errors.append("class names must be non-empty and unique")

    yaml_text = data_yaml.read_text(encoding="utf-8")
    for index, name in enumerate(class_names):
        if f"{index}: {name}" not in yaml_text:
            errors.append(f"data.yaml is missing class mapping {index}: {name}")
    for split in SPLITS:
        expected = f"{split}: images/{split}"
        if expected not in yaml_text:
            errors.append(f"data.yaml is missing split path: {expected}")

    manifest_document = load_json(manifest_path)
    samples = manifest_document.get("samples")
    if not isinstance(samples, list):
        errors.append("manifest.json must define a samples array")
        samples = []
    manifest_by_image: dict[str, dict] = {}
    group_splits: dict[str, str] = {}
    for index, sample in enumerate(samples):
        if not isinstance(sample, dict):
            errors.append(f"manifest sample {index} must be an object")
            continue
        image = sample.get("image")
        if not isinstance(image, str) or not image:
            errors.append(f"manifest sample {index} has no image path")
            continue
        if image in manifest_by_image:
            errors.append(f"duplicate manifest image: {image}")
        manifest_by_image[image] = sample
        split = sample.get("split")
        if split not in SPLITS:
            errors.append(f"{image}: split must be train, val, or test")
        label = sample.get("label")
        if not isinstance(label, str) or not label:
            errors.append(f"{image}: label path is required")
        source_type = sample.get("sourceType")
        if sample.get("license") in {None, "", "unknown", "UNKNOWN"}:
            errors.append(f"{image}: license must be explicit")
        if source_type not in {"self_created", "synthetic", "public"}:
            errors.append(f"{image}: sourceType must be self_created, synthetic, or public")
        if not isinstance(sample.get("source"), str) or not sample["source"].strip():
            errors.append(f"{image}: source description is required")
        if source_type == "public":
            if not isinstance(sample.get("sourceUrl"), str) or not sample["sourceUrl"].startswith(("http://", "https://")):
                errors.append(f"{image}: public samples require sourceUrl")
            if not isinstance(sample.get("licenseUrl"), str) or not sample["licenseUrl"].startswith(("http://", "https://")):
                errors.append(f"{image}: public samples require licenseUrl")
        if sample.get("publicReleaseApproved") is not True:
            errors.append(f"{image}: publicReleaseApproved must be true before repository inclusion")
        if sample.get("annotationStatus") != "reviewed":
            errors.append(f"{image}: annotationStatus must be reviewed")
        group_id = sample.get("groupId")
        if not isinstance(group_id, str) or not group_id.strip():
            errors.append(f"{image}: groupId is required to prevent split leakage")
        elif split in SPLITS:
            previous_split = group_splits.setdefault(group_id, split)
            if previous_split != split:
                errors.append(f"{image}: groupId {group_id} appears in both {previous_split} and {split}")

    image_count = 0
    label_count = 0
    box_count = 0
    actual_images: set[str] = set()
    for split in SPLITS:
        image_dir = root / "images" / split
        label_dir = root / "labels" / split
        if not image_dir.is_dir():
            errors.append(f"missing image split directory: images/{split}")
            continue
        if not label_dir.is_dir():
            errors.append(f"missing label split directory: labels/{split}")
            continue
        images = sorted(path for path in image_dir.iterdir() if path.suffix.lower() in IMAGE_SUFFIXES)
        labels = sorted(path for path in label_dir.glob("*.txt"))
        image_by_stem: dict[str, Path] = {}
        for image_path in images:
            if image_path.stem in image_by_stem:
                errors.append(f"duplicate image stem in images/{split}: {image_path.stem}")
            image_by_stem[image_path.stem] = image_path
            relative_image = image_path.relative_to(root).as_posix()
            actual_images.add(relative_image)
            image_count += 1
            label_path = label_dir / f"{image_path.stem}.txt"
            if not label_path.is_file():
                errors.append(f"{relative_image}: matching label file is missing")
                continue
            label_count += 1
            sample = manifest_by_image.get(relative_image)
            if sample is None:
                errors.append(f"{relative_image}: manifest entry is missing")
            else:
                if sample.get("split") != split:
                    errors.append(f"{relative_image}: manifest split does not match directory")
                expected_label = label_path.relative_to(root).as_posix()
                if sample.get("label") != expected_label:
                    errors.append(f"{relative_image}: manifest label path must be {expected_label}")
                expected_hash = sample.get("sha256")
                if not isinstance(expected_hash, str) or expected_hash.lower() != sha256(image_path):
                    errors.append(f"{relative_image}: manifest sha256 does not match image")

            for line_number, raw_line in enumerate(label_path.read_text(encoding="utf-8").splitlines(), start=1):
                line = raw_line.strip()
                if not line:
                    continue
                parts = line.split()
                if len(parts) != 5:
                    errors.append(f"{label_path.relative_to(root)}:{line_number}: expected 5 fields")
                    continue
                try:
                    class_id = int(parts[0])
                    values = [float(value) for value in parts[1:]]
                except ValueError:
                    errors.append(f"{label_path.relative_to(root)}:{line_number}: invalid numeric value")
                    continue
                if class_id not in class_ids:
                    errors.append(f"{label_path.relative_to(root)}:{line_number}: unknown class ID {class_id}")
                x_center, y_center, width, height = values
                if not all(0.0 <= value <= 1.0 for value in values):
                    errors.append(f"{label_path.relative_to(root)}:{line_number}: coordinates must be normalized")
                if width <= 0.0 or height <= 0.0:
                    errors.append(f"{label_path.relative_to(root)}:{line_number}: box dimensions must be positive")
                if not (width / 2 <= x_center <= 1 - width / 2):
                    errors.append(f"{label_path.relative_to(root)}:{line_number}: box exceeds image width")
                if not (height / 2 <= y_center <= 1 - height / 2):
                    errors.append(f"{label_path.relative_to(root)}:{line_number}: box exceeds image height")
                box_count += 1
        for label_path in labels:
            if label_path.stem not in image_by_stem:
                errors.append(f"{label_path.relative_to(root)}: orphan label without matching image")

    extra_manifest = sorted(set(manifest_by_image) - actual_images)
    for image in extra_manifest:
        errors.append(f"{image}: manifest entry points to a missing image")
    if image_count == 0:
        message = "vision dataset structure is valid but contains no trainable samples"
        if require_samples:
            errors.append(message)
        else:
            warnings.append(message)
    if image_count and box_count == 0:
        errors.append("dataset contains images but no bounding boxes")

    return ValidationResult(image_count, label_count, box_count, errors, warnings)


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate ShipCAD YOLO detection data and provenance metadata.")
    parser.add_argument("--root", type=Path, default=Path("datasets/vision"))
    parser.add_argument("--require-samples", action="store_true")
    args = parser.parse_args()
    result = validate_dataset(args.root, require_samples=args.require_samples)
    for warning in result.warnings:
        print(f"WARNING: {warning}")
    for error in result.errors:
        print(f"ERROR: {error}", file=sys.stderr)
    print(f"Vision dataset: {result.images} images, {result.labels} label files, {result.boxes} boxes")
    return 1 if result.errors else 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except ValueError as exc:
        print(f"Vision dataset validation failed: {exc}", file=sys.stderr)
        raise SystemExit(2) from exc
