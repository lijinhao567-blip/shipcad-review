# 质量保证计划

## 必测场景

- Worker：正常 DXF 和异常 DXF 解析。
- CAD rendering：`POST /render` 和 `/api/versions/{versionId}/rendered-image` 应返回真实 PNG；渲染失败必须暴露明确错误，不能伪造空图片。
- DWG：安装 LibreDWG 后，验证 DWG 转 DXF 的失败和成功路径。
- Vision Worker：未配置模型时返回明确错误；配置模型后能返回检测框结构。
- 后端：登录、项目创建、图纸创建、版本上传、异步审查任务、任务阶段/步骤记录、可选自动 Vision/OCR 证据采集、任务失败重试、问题整改、报告生成、版本对比、聚合健康检查。
- 前端：构建通过，API 调用路径可配置，系统状态页能展示后端、数据库、OpenAPI 和 Worker 状态；审图流程状态应能反映系统、登录、项目图纸、版本、审查、问题和报告进度；项目、图纸、版本列表切换当前上下文后，应同步影响上传、预览、审查和报告选择；审查任务详情能展示步骤时间线和失败细节；dxf-viewer 能加载上传 DXF 并显示图层；Canvas 仅作为手动诊断视图，不能自动掩盖正式预览失败。
- Golden dataset：`datasets/rules/expected.json` 中每个合成 DXF 样例都要通过 `tools/run_golden_e2e.py`，覆盖合规样例、图层命名、空图层、标题栏、标题栏属性、标题栏版次一致性、尺寸标注、版本号、占位文字和实体数量异常。
- Demo walkthrough：`tools/run_demo_walkthrough.py` 应能用一个真实 DXF 样例走通登录、建项目、建图纸、上传版本、发起审查、生成问题、生成报告，并把项目 ID、图纸 ID、版本 ID、任务步骤、问题证据类型和报告预览写入 `.run/demo-walkthrough-*.md`，用于人工演示核查。
- 报告和问题清单：审查报告必须包含解析证据摘要、问题证据详情、规则代码、图层或实体引用、结构化 evidence chain；`GET /api/reports/{reportId}/download` 应通过鉴权返回 Markdown 附件；前端问题清单应按 CAD、规则、知识条款、YOLO、OCR 等来源分组展示证据。
- 整改闭环：`PATCH /api/issues/{issueId}` 应记录状态前后、经办人、操作人、说明和可选报告引用；`GET /api/issues/{issueId}/remediations` 应按时间顺序返回整改时间线；前端应支持开始整改、提交复核、关闭问题和重新打开。
- 版本对比：`GET /api/versions/compare` 应返回结构化版本差异，包括实体数量变化、图层新增/删除、图层实体数量变化、实体类型变化、块参照变化、文本变化、风险提示和复核重点；前端应以摘要、指标和表格展示，而不是只输出原始 JSON。
- Vision evidence：配置模型后，`POST /api/versions/{versionId}/vision-detect` 和 `POST /api/versions/{versionId}/vision-detect-rendered` 应能保存 `YOLO_SYMBOL` evidence；未配置模型时应返回明确错误，不能伪造检测结果。
- OCR evidence：配置 Tesseract 后，`POST /api/versions/{versionId}/ocr-recognize` 和 `POST /api/versions/{versionId}/ocr-recognize-rendered` 应能保存 `OCR_TEXT` evidence；未安装 OCR 引擎时应返回明确错误，不能伪造识别文本。
- 安全：Token 鉴权、文件类型限制、20MB 限制、审计日志。
- 开源合规：依赖许可证记录、模型权重不入库、真实图纸不入库。
- 运行可观测性：`deploy/start-dev.ps1` 应能启动核心开发链路，`/api/health` 和前端“系统状态”应能展示后端、数据库、OpenAPI、CAD Worker 以及可选 Vision/OCR Worker 状态，`deploy/test-health.ps1` 应能检查后端、数据库、OpenAPI、CAD Worker、前端和可选 Vision/OCR Worker，`deploy/run-demo.ps1` 应能执行演示验收闭环，`deploy/run-demo-walkthrough.ps1` 应能生成单样例演示摘要。

## Evidence Regression Checks

- Every generated `ReviewIssue` should include at least one `RULE_RESULT` evidence row.
- Rules with expected entity evidence should include `CAD_ENTITY` evidence whose `sourceId` matches `ReviewIssue.entityRef`.
- Rules with expected layer evidence should include `CAD_LAYER` evidence whose `sourceId` matches `ReviewIssue.layerName`.
- Default seeded rules should include `KNOWLEDGE_CLAUSE` evidence so each issue has a traceable rule basis.
- Every generated issue returned by `/api/issues` should include `aiExplanation` with summary, reason, and basis generated from the evidence chain.
- Version-level visual detections should be stored as `YOLO_SYMBOL` evidence and remain separate from `ReviewIssue` until rules explicitly consume them.
- Version-level OCR regions should be stored as `OCR_TEXT` evidence and remain separate from `ReviewIssue` until rules explicitly consume them.
- Automatic review-task detections should carry `taskId`; RuleEngine should consume manual version-level evidence plus current-task automatic evidence, not stale automatic evidence from earlier tasks.
- Review tasks should expose `review_task.stage` and ordered `review_task_step` rows. Default rule-only tasks should mark PARSE and RULES as `SUCCESS`, and RENDER/VISION/OCR as `SKIPPED`; automatic multimodal tasks should mark RENDER/VISION/OCR as `SUCCESS` or `FAILED` instead of silently skipping them.
- Review task detail UI should display the selected task status, stage, version, issue count, evidence count, ordered steps, timestamps, error message, and detail JSON summary.
- Frontend workflow UI should not create fake demo state. It may guide tab navigation and local selection, but all status values must come from authenticated API state, health checks, current selections, review tasks, issues, or generated reports.
- `OCR_PLACEHOLDER_TEXT` should generate an issue-level `OCR_TEXT` evidence reference with `sourceEvidenceId` when OCR text contains unfinished placeholders.
- `YOLO_TITLE_BLOCK_CAD_MISSING` should generate an issue-level `YOLO_SYMBOL` evidence reference with `sourceEvidenceId` when visual title-block evidence conflicts with CAD structured parsing.
- `tools/run_golden_e2e.py` verifies these evidence checks for the golden DXF dataset.
- `tools/run_multimodal_evidence_e2e.py` verifies the live API chain for review-task automatic rendering, task-scoped `YOLO_SYMBOL` and `OCR_TEXT` evidence, rule consumption, issue-level `sourceEvidenceId` references, AI explanations, and report output. Its default mock workers are deterministic integration substitutes; they do not validate real model accuracy.
- `tools/run_demo_walkthrough.py` verifies one live rule-only review path, then uploads a second version and writes a human-readable Markdown summary for presentation and handoff checks.
- `tools/run_demo_walkthrough.py` also uploads a second DXF version, reviews it, calls `GET /api/versions/compare`, and records the version comparison summary in the walkthrough artifact.

## 验收目标

- 一台 Windows 开发机可启动前端、后端、Worker。
- 上传 golden DXF 样例后，可通过审查任务队列生成规则问题、CAD 证据和知识条款证据。
- 问题状态可以流转到整改中、待复核和关闭，并保留可审计的整改时间线。
- 可导出包含 evidence chain 和 AI 辅助解释的审查报告。
- OpenAPI 文档可访问。
