# Dataset Specification

This document defines the dataset structure for parser regression, rule validation, and YOLOv8 symbol detection.

## Repository Policy

Allowed:

- Self-created DXF/DWG samples.
- Public samples with compatible licenses.
- Small synthetic images for documentation and tests.
- Label files without proprietary geometry if they are generated from synthetic data.

Not allowed:

- Real shipyard or customer CAD drawings.
- Confidential design data.
- Private model weights.
- Large training outputs.

## Parser And Rule Dataset

Use this structure:

```text
datasets/
  external/
    manifest.json
  parser/
    cases/
      valid_title_block.dxf
      missing_title_block.dxf
    manifest.json
  rules/
    cases/
      layer_name_invalid.dxf
      placeholder_text.dxf
    expected.json
```

`manifest.json` should describe parser expectations, provenance, checksum, and preview compatibility expectations:

```json
{
  "datasetVersion": "0.1.0",
  "samples": [
    {
      "id": "complex_ship_section",
      "file": "cases/complex_ship_section.dxf",
      "format": "dxf",
      "source": "Self-created synthetic CAD fixture generated inside this repository.",
      "license": "AGPL-3.0-only",
      "generatedBy": "tools/generate_complex_dxf_dataset.py",
      "sha256": "...",
      "parserExpectations": {
        "minEntityCount": 70,
        "requiredLayers": ["S-HULL", "S-FRAME", "DIM-MAIN"],
        "requiredBlocks": ["TITLE_BLOCK", "WELD_SYMBOL"],
        "requiredEntityTypes": ["INSERT", "ATTRIB", "DIMENSION", "HATCH"],
        "minTypeCounts": {"HATCH": 2},
        "bounds": {"minWidth": 280, "minHeight": 70}
      },
      "previewExpectations": {
        "dxfViewerParser": "required",
        "minDxfViewerEntities": 55,
        "minLayerCount": 10,
        "requiresWebglSmoke": true
      }
    }
  ]
}
```

`expected.json` should describe rule expectations:

```json
[
  {
    "id": "placeholder_text",
    "file": "placeholder_text.dxf",
    "versionNo": "V1",
    "expectedRuleCodes": ["TEXT_PLACEHOLDER"],
    "expectedIssueCount": 1,
    "allowUnexpectedRuleCodes": false,
    "expectedEvidence": {
      "TEXT_PLACEHOLDER": {
        "layerName": "TEXT-NOTE",
        "requireEntityRef": true
      }
    }
  }
]
```

The system should not identify samples by file name. File names only help tests locate input files. The actual result must come from CAD parsing, rule execution, or `dxf-viewer` parsing.

For `datasets/parser/manifest.json`, parser expectations use these machine-checkable keys:

- `minEntityCount`: minimum parsed model-space entity count after expanding supported nested evidence such as block attributes.
- `requiredLayers`: layer names that must appear in the parsed DXF layer table.
- `requiredBlocks`: block names that must appear in parsed block references or related attributes.
- `requiredEntityTypes`: entity types such as `INSERT`, `ATTRIB`, `DIMENSION`, `HATCH`, `LWPOLYLINE`, `MTEXT`, or `TEXT`.
- `minTypeCounts`: per-entity minimum counts for dense fixture coverage.
- `requiredTexts`: text snippets that must appear in parsed text evidence.
- `bounds`: minimum model width and height derived from structured CAD evidence.

Preview expectations use:

- `dxfViewerParser`: whether `dxf-viewer` parser compatibility is required.
- `minDxfViewerEntities`: minimum entity count returned by the `dxf-viewer` parser.
- `minLayerCount`: minimum layer count returned by the `dxf-viewer` parser.
- `requiresWebglSmoke`: marks samples that still need real browser WebGL smoke before release notes or demos claim visual quality.

Current parser dataset:

```text
datasets/parser/cases/*.dxf
datasets/parser/manifest.json
```

Generation and acceptance commands:

```powershell
.\.venv\Scripts\python.exe tools\generate_complex_dxf_dataset.py
.\.venv\Scripts\python.exe tools\check_complex_dxf_dataset.py
node tools\check_dxf_viewer_dataset.mjs
```

`tools/check_complex_dxf_dataset.py` validates provenance, license, SHA-256, CAD Worker parser expectations, HATCH bounds, CAD Worker PNG rendering, render metadata, and nonblank output. `tools/check_dxf_viewer_dataset.mjs` validates that the official `dxf-viewer` parser can parse the same DXF files and see the required layers, blocks, and entity types. Canvas diagnostic rendering is not used as a pass condition.

When `requiresWebglSmoke` is `true`, release and demo verification should also run the real browser path:

1. Start backend, CAD Worker, and frontend.
2. Upload or select the fixture through authenticated product state.
3. Confirm `DxfViewerPreview` loads the DXF Blob from `/api/versions/{versionId}/file`.
4. Confirm the layer list and bounds are visible and no official preview error is shown.
5. Capture the `.dxf-webgl-host` area and verify it is not blank.

Automated local smoke command:

```powershell
.\deploy\run-dxf-viewer-smoke.ps1
```

The command starts the development stack by default, uploads `datasets/parser/cases/dense_deck_grid.dxf`, selects the uploaded version in the Vue app, and writes a JSON report plus a WebGL canvas PNG screenshot under `.run/`.

This smoke proves the formal WebGL preview path is usable; it must not be replaced by automatically opening Canvas diagnostics.

## External DXF Candidate Registry

`datasets/external/manifest.json` records real open-source DXF candidates without vendoring the third-party files. Every entry must include:

- immutable source URL pinned to a 40-character Git commit
- source repository, source path, and human-readable source page
- SPDX-style license identifier, license URL, and attribution
- `repositoryInclusion: remote-cache-only`
- SHA-256 and exact file size
- CAD Worker parser/render expectations
- `dxf-viewer` parser expectations

Commands:

```powershell
# No network access; suitable for CI and release-preflight metadata checks.
.\.venv\Scripts\python.exe tools\check_external_dxf_candidates.py --manifest-only

# Download pinned copies into .run, then validate CAD parsing and PNG rendering.
.\.venv\Scripts\python.exe tools\check_external_dxf_candidates.py

# Validate the cached copies with the official dxf-viewer parser.
node tools\check_external_dxf_viewer_candidates.mjs
```

The external candidates are compatibility inputs, not rule ground truth. A drawing from an open repository cannot be labeled compliant or non-compliant without a separate reviewed rule annotation. Files under CC BY-SA must preserve attribution and share-alike obligations if they are ever redistributed; the default project policy therefore keeps them as pinned remote cache entries.

For `datasets/rules/expected.json`, parser expectations use these machine-checkable keys:

- `minEntityCount`: minimum parsed entity count after expanding supported nested evidence such as block attributes.
- `requiredLayers`: layer names that must appear in the parsed DXF layer table.
- `requiredBlocks`: block names that must appear in parsed block references or related attributes.
- `requiredEntityTypes`: entity types such as `DIMENSION` or `ATTRIB` that must appear in the parsed summary.

Current rule golden dataset:

```text
datasets/rules/cases/*.dxf
datasets/rules/expected.json
```

Generation and acceptance commands:

```powershell
.\.venv\Scripts\python.exe tools\generate_golden_dataset.py
.\.venv\Scripts\python.exe tools\check_rule_golden_coverage.py
.\.venv\Scripts\python.exe tools\run_golden_e2e.py --keep-going
.\.venv\Scripts\python.exe tools\run_multimodal_evidence_e2e.py
```

The coverage checker parses the backend's default seeded rules and verifies that every enabled rule has at least one positive golden case and one negative golden case. It also rejects unknown rule codes and malformed `expectedIssueCount` values.

The E2E script uploads each DXF, creates a review task, polls until it finishes, verifies the ordered task steps for parse/render/vision/OCR/rules, checks the generated `ReviewIssue.ruleCode` set and exact `expectedIssueCount`, validates parser summary expectations, verifies issue evidence fields such as `layerName`, `entityRef`, required evidence types, and expected evidence coordinate spaces, creates an evidence-aware report, checks that report content cites the expected rules/evidence, checks report object-storage metadata and attachment download, and checks the authenticated version file endpoint used by the official preview path. With `--evict-upload-cache`, it deletes local version/report caches before download or review steps so S3-compatible object storage must restore the files.

Cases may include optional `reviewTask`, `mockVision`, and `mockOcr` sections. When any case enables `autoVision` or `autoOcr`, `tools/run_golden_e2e.py` starts deterministic mock Vision/OCR workers on `127.0.0.1:9100` and `127.0.0.1:9200` by default, unless different ports are passed with `--mock-vision-port` and `--mock-ocr-port`. The Spring Boot backend must be configured with matching `SHIPCAD_VISION_URL` and `SHIPCAD_OCR_URL`. These mocks validate orchestration and rule consumption; they do not measure real YOLO or OCR model accuracy.

`tools/run_multimodal_evidence_e2e.py` remains the broader deterministic live API regression for OCR/YOLO evidence. It uses the missing-title-block DXF fixture plus mock Vision/OCR workers to verify review-task automatic CAD rendering, task-scoped `YOLO_SYMBOL`/`OCR_TEXT` evidence, version evidence endpoints, rule consumption, issue-level `sourceEvidenceId`, AI explanations, and report output.

Current deterministic rule coverage:

- `LAYER_NAME_STANDARD`: nonstandard layer names.
- `EMPTY_LAYER_CHECK`: declared custom layers without entities.
- `TITLE_BLOCK_REQUIRED`: missing title block evidence.
- `VERSION_FORMAT`: non-traceable uploaded version numbers.
- `TEXT_PLACEHOLDER`: unfinished placeholder text such as `TBD`.
- `ENTITY_DENSITY`: abnormally sparse drawings.
- `TITLE_ATTRIBUTE_REQUIRED`: missing title block attributes such as `DRAWING_NO` or `REVISION`.
- `VERSION_TITLE_CONSISTENCY`: title block revision inconsistent with uploaded version number.
- `DIMENSION_REQUIRED`: structured drawings without parsed `DIMENSION` entities.
- `DIMENSION_LAYER_STANDARD`: dimensions placed outside `DIM-*` layers.
- `OCR_PLACEHOLDER_TEXT`: OCR text evidence contains unfinished placeholders such as `TBD`, `TODO`, `XXX`, `待定`, or `未定`.
- `YOLO_TITLE_BLOCK_CAD_MISSING`: YOLO detects a visual title block while CAD structured parsing does not extract a title block.

## YOLOv8 Dataset

Use YOLO detection format:

```text
datasets/
  vision/
    classes.json
    manifest.json
    images/
      train/
      val/
      test/
    labels/
      train/
      val/
      test/
    data.yaml
```

Label format per image:

```text
class_id x_center y_center width height
```

Coordinates are normalized to image width and height.

Recommended first symbol classes are defined in `docs/yolov8_symbol_taxonomy.md`.

Run `tools/validate_vision_dataset.py` before committing labels. The validator checks class ID stability, split directories, image/label pairing, normalized boxes, manifest provenance, explicit license, public-release approval, and image SHA-256. The repository currently contains the dataset structure but no trainable samples; `--require-samples` must pass before the first training run.

The current backend can ingest model outputs as `YOLO_SYMBOL` evidence through `POST /api/versions/{versionId}/vision-detect` for manual diagnostic images, `POST /api/versions/{versionId}/vision-detect-rendered` for direct rendered-image detection, and `POST /api/review-tasks` with `autoVision=true` for task-orchestrated detection. `YOLO_TITLE_BLOCK_CAD_MISSING` can consume this evidence when it is already stored. The deterministic multimodal E2E script covers the task-orchestrated rendered-image API and rule chain with mock detections; real model accuracy still requires a separate labeled vision dataset. Do not store private ship drawings, private labels, or model weights in the repository.

## OCR Dataset

OCR evidence currently uses rendered PNG/JPG images and stores recognized text as `OCR_TEXT` evidence through `POST /api/versions/{versionId}/ocr-recognize` for manual diagnostic images, `POST /api/versions/{versionId}/ocr-recognize-rendered` for direct rendered-image OCR, and `POST /api/review-tasks` with `autoOcr=true` for task-orchestrated OCR. `OCR_PLACEHOLDER_TEXT` can consume this evidence when it is already stored. The deterministic multimodal E2E script covers the task-orchestrated rendered-image API and rule chain with mock OCR regions; real OCR accuracy still requires a separate OCR regression dataset.

Recommended future structure:

```text
datasets/
  ocr/
    images/
    expected.json
```

`expected.json` should describe image file, expected text snippets, optional bounding boxes, language, and confidence floor. Do not store private ship drawings or private labels in the repository.
