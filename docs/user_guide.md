# 用户手册

## 默认账号

admin / admin123

## 演示流程

推荐先用开发脚本启动系统并确认健康状态：

```powershell
.\deploy\start-dev.ps1
.\deploy\test-health.ps1
```

1. 启动 CAD Worker、Spring Boot 后端和 Vue 前端；需要符号识别时额外启动 Vision Worker，需要文字识别时额外启动 OCR Worker。
2. 打开“系统状态”，确认后端、数据库、OpenAPI 和 CAD Worker 可用；Vision/OCR 未启动时会显示为可选不可用，不影响规则审查主链路。
3. 登录系统。
4. 创建项目和图纸。
5. 上传 `samples/dxf/invalid_ship_section.dxf`，系统先创建图纸版本记录。DWG 文件需要本机安装 LibreDWG `dwg2dxf`。
6. 发起审查任务，按需勾选自动视觉证据或自动 OCR 证据；等待任务从 PENDING/RUNNING 进入 FINISHED。任务队列会显示当前阶段和 PARSE、RENDER、VISION、OCR、RULES 步骤状态；点击“详情”可查看每一步的开始时间、结束时间和 detailJson 摘要，如果进入 FAILED，可查看错误和失败步骤后重试。
7. 在“问题闭环”中查看 dxf-viewer 正式DXF预览；Canvas诊断视图只在需要排查解析结果时手动打开。
8. 查看问题清单并完成整改流转。问题证据会按 CAD 图元/图层、解析摘要、规则命中、依据条款、视觉符号和 OCR 文字等来源分组展示。
9. 生成审查报告。报告页会展示审查摘要、解析证据摘要、问题清单和问题证据详情，并支持复制/下载 Markdown。
10. 上传 `valid_ship_section.dxf` 作为新版本并做版本对比。

也可以直接运行演示验收脚本：

```powershell
.\deploy\run-demo.ps1
```

该脚本会检查核心服务健康状态，然后执行 golden dataset 端到端验收。需要关闭脚本启动的服务时运行：

```powershell
.\deploy\stop-dev.ps1
```

## YOLOv8 Vision Worker

Vision Worker 当前提供独立接口，用于后续图纸符号识别。运行前需要准备模型权重并设置 `YOLO_MODEL_PATH`。模型权重和真实训练数据不进入仓库。

启动 Vision Worker 后，可在发起审查任务时勾选“自动视觉证据”，系统会先通过 CAD Worker 生成当前版本 PNG，再触发视觉检测，随后统一执行规则；也可以在预览工作区点击“版本渲染图检测”或上传 PNG/JPG 图像做人工对照。后端会把检测结果保存为 `YOLO_SYMBOL` 证据。已实现规则 `YOLO_TITLE_BLOCK_CAD_MISSING` 会在视觉识别到标题栏但 CAD 解析未提取标题栏块时生成审查问题；其他符号类规则仍需后续扩展。

## OCR Worker

OCR Worker 当前提供独立接口，用于后续图纸文字识别。运行前需要安装 Tesseract OCR；中文图纸文字需要额外安装 `chi_sim` 语言数据并设置 `OCR_LANG=eng+chi_sim`。

启动 OCR Worker 后，可在发起审查任务时勾选“自动OCR证据”，系统会先通过 CAD Worker 生成当前版本 PNG，再触发 OCR 识别，随后统一执行规则；也可以在预览工作区点击“版本渲染图识别”或上传 PNG/JPG 图像做人工对照。后端会把识别结果保存为 `OCR_TEXT` 证据。已实现规则 `OCR_PLACEHOLDER_TEXT` 会在 OCR 文字包含 `TBD`、`TODO`、`XXX`、`待定`、`未定` 等占位内容时生成审查问题；其他 OCR 规则仍需后续扩展。

## Multimodal Evidence Acceptance

After the backend and CAD Worker are running, use the deterministic multimodal E2E script to validate the complete API chain without real YOLO weights or Tesseract:

```powershell
.\.venv\Scripts\python.exe tools\run_multimodal_evidence_e2e.py
```

The script starts mock Vision/OCR workers by default, uploads a DXF fixture, starts a review task with `autoVision=true` and `autoOcr=true`, verifies task-scoped `YOLO_SYMBOL` and `OCR_TEXT` evidence, checks issue-level `sourceEvidenceId` references, and verifies that the report contains the multimodal evidence. Real model quality still needs separate labeled datasets.
