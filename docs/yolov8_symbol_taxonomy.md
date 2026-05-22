# YOLOv8 Symbol Taxonomy

This is the initial symbol taxonomy for drawing-symbol detection. It should remain small until the dataset and labeling rules are stable.

## Initial Classes

| Class ID | Name | Meaning |
|---:|---|---|
| 0 | welding_symbol | Welding symbol or welding callout mark |
| 1 | arrow | Direction arrow or leader arrow |
| 2 | section_marker | Section cut or section reference marker |
| 3 | title_block | Title block region |
| 4 | revision_block | Revision table or revision mark |
| 5 | dimension_text | Dimension text region |
| 6 | note_text | General note text region |
| 7 | inspection_mark | Inspection or review mark |

## Labeling Rules

- Use tight boxes around visible symbols.
- Do not label ordinary structure lines as symbols.
- For title and revision blocks, label the whole rectangular region.
- If text is too small to read but clearly belongs to a dimension, label it as `dimension_text`.
- Prefer fewer, consistent classes over many ambiguous classes.

## Model Boundary

YOLOv8 should detect visual evidence. It should not decide final compliance alone.

The recommended flow is:

```text
symbol detections -> backend evidence storage -> deterministic rules / knowledge graph -> review issue
```
