# 用户手册

## 本地开发账号

```text
admin / admin123       系统管理员
expert / expert123     审图专家
engineer / engineer123 设计工程师
viewer / viewer123     只读访客
```

这些账号仅在 `dev` Profile 中创建，供本地开发和自动化验收使用。生产环境不得启用 `dev` Profile。

角色职责：

- 系统管理员：全部操作，并可进入“审计日志”筛选查看关键操作和越权拒绝。
- 审图专家：采集证据、发起审查、复核/关闭问题和生成报告。
- 设计工程师：创建项目/图纸、上传版本、采集证据、整改并提交复核。
- 只读访客：查看项目、图纸、任务、问题、证据和既有报告。

除系统管理员外，以上能力只对用户已加入的项目生效。没有项目成员关系时，用户看不到对应项目及其下游图纸数据。

## 账号与用户

登录后可进入“账号与用户”页面查看当前账号、角色和会话过期时间。所有已登录用户都可以输入当前密码修改自己的密码；修改成功后现有会话会被撤销，需要使用新密码重新登录。

系统管理员还可以：

1. 创建用户并指定显示名称、角色和启用状态。
2. 修改已有用户的显示名称、角色和启用状态。
3. 为用户重置密码。
4. 查看创建时间、密码修改时间和最后登录时间。

密码必须为 10-128 个字符，并同时包含字母和数字。停用账号、变更角色或重置密码后，该用户已登录的会话会立即失效。管理员不能停用自身账号或撤销自身管理员角色。

生产环境空数据库首次启动前，需要设置以下环境变量创建初始管理员：

```powershell
$env:SPRING_PROFILES_ACTIVE="prod"
$env:SHIPCAD_BOOTSTRAP_ADMIN_USERNAME="admin"
$env:SHIPCAD_BOOTSTRAP_ADMIN_PASSWORD="ReplaceWithStrongPassword123"
$env:SHIPCAD_BOOTSTRAP_ADMIN_DISPLAY_NAME="系统管理员"
```

初始管理员只会在用户表为空时创建。创建成功后，应从部署配置中移除明文密码，并通过“账号与用户”页面管理后续账号。

## 项目成员管理

系统管理员进入“项目与图纸”，先在项目列表中将目标项目设为当前，然后在“项目成员”区域添加或移除用户。

- 添加成员后，该用户重新刷新页面即可看到项目及其图纸、版本、任务、问题、证据、报告和范围内统计。
- 移除成员后，该用户后续请求会立即失去访问权限，不需要重新登录。
- 成员关系不改变用户角色。例如只读访客加入项目后仍不能上传或审查，审图专家加入项目后可以执行其角色允许的审查操作。
- 管理员始终可以查看全部项目，不受成员关系限制。
- 升级前的历史项目没有成员记录时，只对管理员可见，需要管理员明确分配，系统不会自动向普通用户公开历史图纸。

## 演示流程

推荐先用开发脚本启动系统并确认健康状态：

```powershell
.\deploy\start-dev.ps1
.\deploy\test-health.ps1
```

1. 启动 CAD Worker、Spring Boot 后端和 Vue 前端；需要符号识别时额外启动 Vision Worker，需要文字识别时额外启动 OCR Worker。
2. 打开“系统状态”，确认后端、数据库、OpenAPI 和 CAD Worker 可用；Vision/OCR 未启动时会显示为可选不可用，不影响规则审查主链路。
3. 登录系统。页面顶部“审图流程”会同步显示系统、登录、项目图纸、版本、审查、问题和报告状态。
4. 创建项目和图纸。项目、图纸和版本列表会显示短 ID，可点击“设为当前”切换当前上下文。
5. 上传 `samples/dxf/invalid_ship_section.dxf`，系统先创建图纸版本记录。DWG 文件需要本机安装 LibreDWG `dwg2dxf`。上传完成后当前版本会自动进入预览和审查选择。
6. 发起审查任务，按需勾选自动视觉证据或自动 OCR 证据；等待任务从 PENDING/RUNNING 进入 FINISHED。任务队列会显示当前阶段和 PARSE、RENDER、VISION、OCR、RULES 步骤状态；点击“详情”可查看每一步的开始时间、结束时间和 detailJson 摘要，如果进入 FAILED，可查看错误和失败步骤后重试。
7. 在“问题闭环”中查看 dxf-viewer 正式DXF预览。选中问题后，正式预览会优先使用 `ReviewEvidence.location` 的 CAD 模型范围聚焦并高亮；旧数据缺少证据范围时，会退回使用解析图元或图层范围。Canvas诊断视图只在需要排查解析结果时手动打开。
8. 查看问题清单并完成整改流转。问题证据会按 CAD 图元/图层、解析摘要、规则命中、依据条款、视觉符号和 OCR 文字等来源分组展示；选中问题后可填写整改/复核说明、经办人和报告引用，并在时间线中查看开始整改、提交复核、关闭或重新打开记录。
9. 生成审查报告。报告页会展示审查摘要、解析证据摘要、问题清单和问题证据详情，并支持复制 Markdown 或通过后端鉴权接口下载 Markdown 附件。
10. 上传 `valid_ship_section.dxf` 作为新版本并做版本对比。版本对比页会展示实体数量、图层、实体类型、块参照和文本变化，并给出风险提示与复核重点。

也可以直接运行演示验收脚本：

```powershell
.\deploy\run-demo.ps1
```

验证角色权限、用户生命周期、会话撤销和审计日志：

```powershell
.\deploy\run-access-control-e2e.ps1
```

该脚本会检查核心服务健康状态，然后执行 golden dataset 端到端验收。需要关闭脚本启动的服务时运行：

如果需要生成一份可人工查阅的演示走查摘要，可在后端和 CAD Worker 启动后运行：

```powershell
.\deploy\run-demo-walkthrough.ps1
```

摘要会写入 `.run/demo-walkthrough-*.md`，记录本次真实创建的项目、图纸、两个图纸版本、审查任务、问题、证据类型、整改时间线、报告预览、服务端报告下载校验结果和版本对比摘要。

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
