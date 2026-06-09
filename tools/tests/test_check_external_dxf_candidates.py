from pathlib import Path

import pytest

from tools.check_external_dxf_candidates import resolve_cache_path, validate_manifest, validate_parser_expectations


def test_resolve_cache_path_rejects_traversal(tmp_path: Path):
    with pytest.raises(ValueError, match="escapes cache directory"):
        resolve_cache_path(tmp_path, "../outside.dxf")


def test_manifest_requires_pinned_remote_cache_metadata():
    payload = {
        "storagePolicy": "wrong",
        "samples": [
            {
                "id": "sample",
                "cacheFile": "sample.dxf",
                "sourceCommit": "main",
                "sourceUrl": "https://raw.githubusercontent.com/example/repo/main/sample.dxf",
                "repositoryInclusion": "vendored",
                "license": "unknown",
                "fileSize": 0,
                "sha256": "bad",
                "parserExpectations": {},
                "previewExpectations": {},
            }
        ],
    }

    errors = validate_manifest(payload)

    assert "storagePolicy must declare remote-cache-only storage" in errors
    assert any("sourceCommit must be a 40-character" in error for error in errors)
    assert any("repositoryInclusion must be remote-cache-only" in error for error in errors)
    assert any("unsupported or unclear license" in error for error in errors)


def test_parser_expectations_validate_structure_and_text():
    summary = {
        "entityCount": 5,
        "layers": ["0", "HULL"],
        "typeCounts": {"LINE": 4, "TEXT": 1},
        "texts": ["Hull section"],
        "bounds": {"minX": 0, "minY": 0, "maxX": 100, "maxY": 50},
    }
    expectations = {
        "minEntityCount": 5,
        "requiredLayers": ["HULL"],
        "requiredEntityTypes": ["LINE", "TEXT"],
        "minTypeCounts": {"LINE": 4},
        "requiredTexts": ["Hull"],
        "bounds": {"minWidth": 100, "minHeight": 50},
    }

    assert validate_parser_expectations("sample", summary, expectations) == []
