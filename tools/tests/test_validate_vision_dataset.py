from __future__ import annotations

import hashlib
import json
from pathlib import Path

from tools.validate_vision_dataset import validate_dataset


CLASSES = {
    "schemaVersion": 1,
    "datasetVersion": "0.1.0",
    "classes": [
        {"id": 0, "name": "welding_symbol"},
        {"id": 1, "name": "section_marker"},
        {"id": 2, "name": "title_block"},
        {"id": 3, "name": "revision_block"},
    ],
}


def write_dataset(root: Path, label: str = "2 0.5 0.5 0.4 0.2") -> None:
    for split in ("train", "val", "test"):
        (root / "images" / split).mkdir(parents=True)
        (root / "labels" / split).mkdir(parents=True)
    image = root / "images" / "train" / "sample.png"
    image.write_bytes(b"synthetic-image-placeholder")
    (root / "labels" / "train" / "sample.txt").write_text(label + "\n", encoding="utf-8")
    (root / "classes.json").write_text(json.dumps(CLASSES), encoding="utf-8")
    (root / "data.yaml").write_text(
        "path: .\ntrain: images/train\nval: images/val\ntest: images/test\n"
        "names:\n  0: welding_symbol\n  1: section_marker\n  2: title_block\n  3: revision_block\n",
        encoding="utf-8",
    )
    manifest = {
        "schemaVersion": 1,
        "datasetVersion": "0.1.0",
        "samples": [
            {
                "image": "images/train/sample.png",
                "label": "labels/train/sample.txt",
                "split": "train",
                "sourceType": "synthetic",
                "source": "Unit-test generated placeholder",
                "license": "CC0-1.0",
                "publicReleaseApproved": True,
                "annotationStatus": "reviewed",
                "groupId": "unit-test-drawing-001",
                "sha256": hashlib.sha256(image.read_bytes()).hexdigest(),
            }
        ],
    }
    (root / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")


def test_accepts_valid_dataset(tmp_path):
    write_dataset(tmp_path)
    result = validate_dataset(tmp_path, require_samples=True)
    assert result.errors == []
    assert (result.images, result.labels, result.boxes) == (1, 1, 1)


def test_rejects_box_outside_image(tmp_path):
    write_dataset(tmp_path, label="2 0.95 0.5 0.4 0.2")
    result = validate_dataset(tmp_path, require_samples=True)
    assert any("box exceeds image width" in error for error in result.errors)


def test_rejects_missing_provenance_approval(tmp_path):
    write_dataset(tmp_path)
    manifest_path = tmp_path / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    manifest["samples"][0]["publicReleaseApproved"] = False
    manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
    result = validate_dataset(tmp_path, require_samples=True)
    assert any("publicReleaseApproved must be true" in error for error in result.errors)


def test_rejects_same_drawing_group_across_splits(tmp_path):
    write_dataset(tmp_path)
    second_image = tmp_path / "images" / "val" / "sample-val.png"
    second_image.write_bytes(b"second-placeholder")
    (tmp_path / "labels" / "val" / "sample-val.txt").write_text("1 0.5 0.5 0.2 0.2\n", encoding="utf-8")
    manifest_path = tmp_path / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    manifest["samples"].append(
        {
            "image": "images/val/sample-val.png",
            "label": "labels/val/sample-val.txt",
            "split": "val",
            "sourceType": "synthetic",
            "source": "Unit-test generated placeholder",
            "license": "CC0-1.0",
            "publicReleaseApproved": True,
            "annotationStatus": "reviewed",
            "groupId": "unit-test-drawing-001",
            "sha256": hashlib.sha256(second_image.read_bytes()).hexdigest(),
        }
    )
    manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
    result = validate_dataset(tmp_path, require_samples=True)
    assert any("appears in both train and val" in error for error in result.errors)
