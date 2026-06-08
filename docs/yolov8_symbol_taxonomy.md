# YOLOv8 Symbol Taxonomy

首批模型保持四类，先建立稳定标注边界和可复现基线。机器可读定义位于 `datasets/vision/classes.json`。

## Phase 1 Classes

| Class ID | Name | Meaning |
|---:|---|---|
| 0 | welding_symbol | 完整焊接符号或焊接标注组合 |
| 1 | section_marker | 剖切位置、方向或剖面索引标记 |
| 2 | title_block | 完整标题栏区域 |
| 3 | revision_block | 完整修订栏或修订记录区域 |

`title_block` 已能被 `YOLO_TITLE_BLOCK_CAD_MISSING` 规则消费。其他三类先作为版本级 `YOLO_SYMBOL` 证据保存，只有在明确规则和知识依据建立后才生成审查问题。

## Deferred Classes

- `arrow`：普通引线箭头与方向箭头高度相似，需先定义子类和负样本。
- `dimension_text`、`note_text`：文字内容优先由 OCR 负责，后续仅在需要文字区域检测时加入。
- `inspection_mark`：依赖企业实际审图标记体系，缺少业务样例时不应凭空定义。

## Labeling Rules

- Use tight boxes around visible symbols.
- Do not label ordinary structure lines as symbols.
- For title and revision blocks, label the whole rectangular region.
- Prefer fewer, consistent classes over many ambiguous classes.
- Do not assign a class solely because the surrounding text suggests it; label visible geometry.
- A symbol partly cut by a tile boundary should be ignored unless its class remains unambiguous.

## Model Boundary

YOLOv8 should detect visual evidence. It should not decide final compliance alone.

The recommended flow is:

```text
symbol detections -> backend evidence storage -> deterministic rules / knowledge graph -> review issue
```
