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
| User workflow | RemediationRecord | assignee, comment, status change, review decision |

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

Evidence
  id
  versionId
  evidenceType
  sourceType
  sourceId
  summary
  rawJson

ReviewIssueEvidence
  issueId
  evidenceId
```

The exact schema can change later. The stable idea is that `ReviewIssue` should be able to cite one or more pieces of evidence.

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

Current status: CAD parser evidence already drives deterministic rules for layers, text placeholders, title block attributes, title revision consistency, dimension existence, and dimension layer placement. `ReviewIssue.entityRef` points back to the parsed entity when the finding can be tied to a concrete CAD entity.

### Stage 3: Vision Evidence

Store YOLO detections from Vision Worker and display them as overlays.

### Stage 4: OCR Evidence

Store recognized text regions and allow rules to query them.

### Stage 5: Knowledge Evidence

Attach standard clauses and rule basis to review issues.

### Stage 6: AI Explanation

Generate explanations from issue plus evidence, not from the drawing alone.
