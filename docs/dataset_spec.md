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

`manifest.json` should describe parser expectations:

```json
[
  {
    "file": "valid_title_block.dxf",
    "format": "dxf",
    "parserExpectations": {
      "minEntityCount": 1,
      "requiredLayers": ["S-STRUCTURE", "S-TITLE"],
      "requiredBlocks": ["TITLE_BLOCK"],
      "requiredEntityTypes": ["INSERT", "ATTRIB", "DIMENSION"]
    }
  }
]
```

`expected.json` should describe rule expectations:

```json
[
  {
    "file": "placeholder_text.dxf",
    "expectedIssues": [
      {
        "ruleCode": "TEXT_PLACEHOLDER",
        "severity": "MEDIUM"
      }
    ],
    "expectedEvidence": {
      "TEXT_PLACEHOLDER": {
        "layerName": "TEXT-NOTE",
        "requireEntityRef": true
      }
    }
  }
]
```

The system should not identify samples by file name. File names only help tests locate input files. The actual result must come from CAD parsing and rule execution.

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
.\.venv\Scripts\python.exe tools\run_golden_e2e.py --keep-going
.\.venv\Scripts\python.exe tools\run_multimodal_evidence_e2e.py
```

The E2E script uploads each DXF, creates a review task, polls until it finishes, verifies the ordered task steps for parse/render/vision/OCR/rules, checks the generated `ReviewIssue.ruleCode` set, validates parser summary expectations, verifies issue evidence fields such as `layerName` and `entityRef`, creates an evidence-aware report, checks that report content cites the expected rules/evidence, checks report object-storage metadata and attachment download, and checks the authenticated version file endpoint used by the official preview path. With `--evict-upload-cache`, it deletes local version/report caches before download or review steps so S3-compatible object storage must restore the files.

`tools/run_multimodal_evidence_e2e.py` is the deterministic live API regression for OCR/YOLO evidence. It uses the missing-title-block DXF fixture plus mock Vision/OCR workers to verify review-task automatic CAD rendering, task-scoped `YOLO_SYMBOL`/`OCR_TEXT` evidence, version evidence endpoints, rule consumption, issue-level `sourceEvidenceId`, AI explanations, and report output.

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
    images/
      train/
      val/
    labels/
      train/
      val/
    data.yaml
```

Label format per image:

```text
class_id x_center y_center width height
```

Coordinates are normalized to image width and height.

Recommended first symbol classes are defined in `docs/yolov8_symbol_taxonomy.md`.

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
