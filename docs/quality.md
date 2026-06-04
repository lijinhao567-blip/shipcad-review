# 质量保证计划

## 必测场景

- Worker：正常 DXF 和异常 DXF 解析。
- DWG：安装 LibreDWG 后，验证 DWG 转 DXF 的失败和成功路径。
- Vision Worker：未配置模型时返回明确错误；配置模型后能返回检测框结构。
- 后端：登录、项目创建、图纸创建、版本上传、异步审查任务、任务失败重试、问题整改、报告生成、版本对比。
- 前端：构建通过，API 调用路径可配置，dxf-viewer 能加载上传 DXF 并显示图层；Canvas 仅作为手动诊断视图，不能自动掩盖正式预览失败。
- Golden dataset：`datasets/rules/expected.json` 中每个合成 DXF 样例都要通过 `tools/run_golden_e2e.py`，覆盖合规样例、图层命名、空图层、标题栏、标题栏属性、标题栏版次一致性、尺寸标注、版本号、占位文字和实体数量异常。
- 报告：审查报告必须包含解析证据摘要、问题证据详情、规则代码、图层或实体引用、结构化 evidence chain。
- Vision evidence：配置模型后，`POST /api/versions/{versionId}/vision-detect` 应能保存 `YOLO_SYMBOL` evidence；未配置模型时应返回明确错误，不能伪造检测结果。
- OCR evidence：配置 Tesseract 后，`POST /api/versions/{versionId}/ocr-recognize` 应能保存 `OCR_TEXT` evidence；未安装 OCR 引擎时应返回明确错误，不能伪造识别文本。
- 安全：Token 鉴权、文件类型限制、20MB 限制、审计日志。
- 开源合规：依赖许可证记录、模型权重不入库、真实图纸不入库。

## Evidence Regression Checks

- Every generated `ReviewIssue` should include at least one `RULE_RESULT` evidence row.
- Rules with expected entity evidence should include `CAD_ENTITY` evidence whose `sourceId` matches `ReviewIssue.entityRef`.
- Rules with expected layer evidence should include `CAD_LAYER` evidence whose `sourceId` matches `ReviewIssue.layerName`.
- Default seeded rules should include `KNOWLEDGE_CLAUSE` evidence so each issue has a traceable rule basis.
- Every generated issue returned by `/api/issues` should include `aiExplanation` with summary, reason, and basis generated from the evidence chain.
- Version-level visual detections should be stored as `YOLO_SYMBOL` evidence and remain separate from `ReviewIssue` until rules explicitly consume them.
- Version-level OCR regions should be stored as `OCR_TEXT` evidence and remain separate from `ReviewIssue` until rules explicitly consume them.
- `OCR_PLACEHOLDER_TEXT` should generate an issue-level `OCR_TEXT` evidence reference with `sourceEvidenceId` when OCR text contains unfinished placeholders.
- `YOLO_TITLE_BLOCK_CAD_MISSING` should generate an issue-level `YOLO_SYMBOL` evidence reference with `sourceEvidenceId` when visual title-block evidence conflicts with CAD structured parsing.
- `tools/run_golden_e2e.py` verifies these evidence checks for the golden DXF dataset.

## 验收目标

- 一台 Windows 开发机可启动前端、后端、Worker。
- 上传 golden DXF 样例后，可通过审查任务队列生成规则问题、CAD 证据和知识条款证据。
- 问题状态可以流转到整改中、待复核和关闭。
- 可导出包含 evidence chain 和 AI 辅助解释的审查报告。
- OpenAPI 文档可访问。
