# Evidence Model

The long-term review engine should be evidence-driven. CAD parsing, YOLOv8 detection, OCR, and knowledge graph queries should all produce evidence. Rules then consume evidence and generate review issues.

## Why Evidence Matters

The system should not output unexplained conclusions such as:

```text
This drawing is wrong.
```

It should output traceable findings:

```text
Rule TEXT_PLACEHOLDER was triggered because OCR text "待定" was found in the title block region.
Evidence: OCR text region, CAD layer, drawing version, rule code.
```

This makes review results:

- explainable
- testable
- auditable
- easier to debug
- suitable for AI-assisted report generation

## Evidence Sources

| Source | Evidence Type | Example |
|---|---|---|
| CAD parser | ParsedEntity | layer, block, text entity, line, circle, dimension |
| YOLOv8 | DetectedSymbol | welding symbol, arrow, section marker, title block |
| OCR | OcrTextRegion | title text, revision record, dimension text, note |
| Knowledge graph | KnowledgeClause | standard clause, rule basis, remediation suggestion |
| User workflow | RemediationRecord | assignee, operator, status change, report reference, remediation/review decision |

## Proposed Future Data Objects

Current object:

```text
ParsedEntity
```

Proposed additions:

```text
DetectedSymbol
  id
  versionId
  className
  confidence
  bbox
  sourceImage

OcrTextRegion
  id
  versionId
  text
  confidence
  bbox
  sourceImage

KnowledgeClause
  id
  code
  title
  content
  source
  tags
  remediationHint
  createdAt

Evidence
  id
  versionId
  issueId
  taskId
  ruleCode
  evidenceType
  sourceId
  sourceLabel
  summary
  payloadJson
  confidence
  createdAt
```

Current implementation uses `ReviewEvidence` as the first production schema. `ReviewIssue` returns a transient `evidences` array in API responses, while evidence rows are persisted independently in `review_evidence`. `KnowledgeClause` is also implemented as the minimal rule-basis store before a full graph database is introduced.

Current review tasks now load manual version-level `YOLO_SYMBOL`/`OCR_TEXT` evidence plus current-task automatic `YOLO_SYMBOL`/`OCR_TEXT` evidence into `RuleEngine`. Rules that consume these evidence rows generate issue-level evidence references containing `sourceEvidenceId`, so the original OCR/YOLO output remains intact while the issue chain stays auditable. CAD Worker can render a drawing version to PNG; review tasks can optionally orchestrate rendering, Vision Worker, OCR Worker, rule consumption, and report output. `tools/run_multimodal_evidence_e2e.py` verifies that complete chain with deterministic mock workers.

## Coordinate Contract

`ReviewEvidence.location` is the durable location contract shared by CAD, YOLO, and OCR evidence. The original `payloadJson` remains available for source-specific details, while `location` provides a stable shape for preview positioning and later overlay rendering.

| Coordinate space | Origin and unit | Durable use |
|---|---|---|
| `CAD_MODEL` | bottom-left, drawing units | CAD entity, layer, or drawing locations |
| `RASTER_IMAGE` | top-left, image pixels | YOLO/OCR bounding boxes |
| `VIEWPORT` | top-left, CSS pixels | runtime browser pan/zoom result; derived by the frontend and not persisted as authoritative evidence |

The common fields are:

- `referenceType` and `referenceId`: entity, layer, drawing, or bounding-box identity.
- `cadHandle`: native DXF handle when available. It is scoped to the drawing version and must not be treated as a cross-version business ID.
- `layerName`, `anchor`, and `bounds`: structured CAD or image location.
- `imageWidth`, `imageHeight`, and `imageSource`: raster evidence context.
- `transform`: source and target bounds plus coordinate origins used to map raster evidence into CAD model space.

CAD evidence example:

```json
{
  "coordinateSpace": "CAD_MODEL",
  "referenceType": "ENTITY",
  "referenceId": "entity_123",
  "cadHandle": "2A",
  "layerName": "S-HULL",
  "anchor": {"x": 10.0, "y": 20.0},
  "bounds": {"minX": 10.0, "minY": 20.0, "maxX": 50.0, "maxY": 60.0},
  "origin": "BOTTOM_LEFT",
  "unit": "DRAWING_UNIT"
}
```

YOLO and OCR use the same raster location shape. For evidence collected from the CAD-rendered version image, `transform.sourceBounds` is the full PNG pixel rectangle and `transform.targetBounds` is the exact matplotlib model viewport returned by CAD Worker:

```text
cadX = target.minX + pixelX / imageWidth  * (target.maxX - target.minX)
cadY = target.maxY - pixelY / imageHeight * (target.maxY - target.minY)
```

The Y axis is inverted because image pixels start at the top-left while CAD model coordinates use a bottom-left origin. The render viewport intentionally includes aspect-ratio padding; using only raw entity extents would shift overlays on non-matching image aspect ratios.

Manually uploaded PNG/JPG evidence has raster bounds but no CAD transform because the system cannot prove its crop, scale, rotation, or source version. It must remain visibly unmapped until an explicit registration step is implemented. Browser viewport coordinates are likewise derived at runtime from the current `dxf-viewer` camera and must not overwrite the durable CAD/raster evidence.

CAD Worker returns render metadata in `X-ShipCAD-Render-Metadata`; the backend stores it beside the cached PNG as `render.metadata.json`. Missing or invalid metadata fails the render chain explicitly so visual evidence cannot appear successful while losing its CAD mapping.

## Review Flow

```text
CAD parsing -> ParsedEntity evidence
YOLOv8 detection -> DetectedSymbol evidence
OCR recognition -> OcrTextRegion evidence
Knowledge graph query -> KnowledgeClause evidence

Evidence set -> Rule engine -> ReviewIssue
ReviewIssue + evidence -> AI explanation/report
```

## Rule Examples

### Placeholder Text

Inputs:

- CAD text entities
- OCR text regions

Rule:

```text
if text contains TBD / TODO / 待定 / 未定:
  create ReviewIssue
  attach text evidence
```

### Missing Welding Note

Inputs:

- YOLO welding_symbol detections
- OCR nearby text
- CAD nearby text entities
- knowledge graph clause for welding annotation

Rule:

```text
if welding_symbol exists and no nearby welding note exists:
  create ReviewIssue
  attach symbol, text search result, and standard clause evidence
```

### Title Block Consistency

Inputs:

- YOLO title_block detection
- CAD block references
- OCR title block text
- Drawing metadata

Rule:

```text
if title block drawing number does not match Drawing.drawingNo:
  create ReviewIssue
  attach OCR text and drawing metadata evidence
```

## Implementation Stages

### Stage 1: Evidence Documentation

Status: current document.

Define the model and keep future integrations aligned.

### Stage 2: CAD Evidence

Promote current `ParsedEntity` outputs into evidence attached to issues.

Current status: implemented. CAD parser evidence drives deterministic rules for layers, text placeholders, title block attributes, title revision consistency, dimension existence, and dimension layer placement. Each generated issue now stores `RULE_RESULT` plus one CAD evidence row: `CAD_ENTITY`, `CAD_LAYER`, or `CAD_SUMMARY`. CAD entities include their native handle and structured bounds in `ReviewEvidence.location`. `ReviewIssue.entityRef` is retained as a compatibility shortcut and preview locator, but the durable evidence chain is now `ReviewIssue.evidences`.

### Stage 3: Vision Evidence

Store YOLO detections from Vision Worker and display them as overlays.

Current status: first ingestion layer implemented. `vision_worker` exposes `/detect` for PNG/JPG images and returns YOLOv8 detections with class, confidence, bounding box, image size, and engine metadata. The backend exposes `POST /api/versions/{versionId}/vision-detect` for manual image diagnostics, `POST /api/versions/{versionId}/vision-detect-rendered` for detection on the CAD-rendered version PNG, and review-task orchestration through `POST /api/review-tasks` with `autoVision=true`. Each detection is persisted as `YOLO_SYMBOL` `ReviewEvidence`; automatic task detections carry `taskId` to keep repeated tasks isolated. Rendered-version detections carry a raster-to-CAD transform in `ReviewEvidence.location`; manual image detections remain raster-only. This stage does not yet include trained ship-symbol weights or overlay rendering on top of the official DXF preview.

Rule consumption status: implemented for `YOLO_TITLE_BLOCK_CAD_MISSING`. If YOLO detects `title_block` but CAD structured parsing does not find a title block, the system creates a `ReviewIssue` with `RULE_RESULT`, `YOLO_SYMBOL`, and optional `KNOWLEDGE_CLAUSE` evidence. The frontend evidence list displays `sourceEvidenceId` when the issue-level visual evidence references the original version-level detection.

### Stage 4: OCR Evidence

Store recognized text regions and allow rules to query them.

Current status: first ingestion layer implemented. `ocr_worker` exposes `/ocr` for PNG/JPG images and returns text regions with recognized text, confidence, bounding box, image size, OCR engine, and language metadata. The backend exposes `POST /api/versions/{versionId}/ocr-recognize` for manual image diagnostics, `POST /api/versions/{versionId}/ocr-recognize-rendered` for OCR on the CAD-rendered version PNG, and review-task orchestration through `POST /api/review-tasks` with `autoOcr=true`. Each region is persisted as `OCR_TEXT` `ReviewEvidence`; automatic task regions carry `taskId` to keep repeated tasks isolated. Rendered-version regions carry the same raster-to-CAD contract as YOLO detections. This stage does not yet include OCR overlay rendering or full OCR dataset regression.

Rule consumption status: implemented for `OCR_PLACEHOLDER_TEXT`. If OCR evidence text contains `TBD`, `TODO`, `XXX`, `待定`, or `未定`, the system creates a `ReviewIssue` with `RULE_RESULT`, `OCR_TEXT`, and optional `KNOWLEDGE_CLAUSE` evidence. The frontend evidence list displays `sourceEvidenceId` when the issue-level OCR evidence references the original version-level text region.

### Stage 5: Knowledge Evidence

Attach standard clauses and rule basis to review issues.

Current status: minimal implementation complete. `knowledge_clause` stores internal rule-basis entries, `review_rule.knowledgeClauseCode` binds deterministic rules to those entries, and every bound rule can emit `KNOWLEDGE_CLAUSE` evidence. The current seed clauses are MVP internal review bases, not official classification-society standards. A later graph adapter can replace or enrich this source without changing the evidence chain.

### Stage 6: AI Explanation

Generate explanations from issue plus evidence, not from the drawing alone.

Current status: minimal implementation complete. `AiGateway` generates `AiExplanation` from `ReviewIssue.evidences` using a deterministic local summarizer. It is exposed through `ReviewIssue.aiExplanation` and `/api/issues/{issueId}/ai-explanation`. Future LLM integration should replace the gateway implementation while preserving the evidence-only input contract.
