# 质量保证计划

## 必测场景

- Worker：正常 DXF 和异常 DXF 解析。
- CAD rendering：`POST /render` 和 `/api/versions/{versionId}/rendered-image` 应返回真实 PNG；渲染失败必须暴露明确错误，不能伪造空图片。
- DWG：安装 LibreDWG 后，验证 DWG 转 DXF 的失败和成功路径。
- Vision Worker：未配置模型时返回明确错误；配置模型后能返回检测框结构。
- 后端：登录、持久化会话恢复、注销失效、会话过期、密码修改、账号停用、角色变更、管理员用户管理、项目成员增删、跨项目访问拒绝、项目创建、图纸创建、版本上传、异步审查任务、任务阶段/步骤记录、可选自动 Vision/OCR 证据采集、任务失败重试、问题整改、报告生成、版本对比、聚合健康检查。
- 审查任务队列：默认内存队列必须保持本地测试和 golden E2E 稳定；Redis 协议队列必须验证入队载荷、容量限制、健康检查、处理队列恢复和消费失败路径。`deploy/run-redis-queue-e2e.ps1` 已覆盖真实 Redis 协议开发机 E2E；`deploy/run-task-retry-e2e.ps1` 已覆盖坏 DXF 失败、失败任务重试、完成任务拒绝重试和审计记录；`deploy/run-compose-e2e.ps1` 用于在具备 Docker 的环境验证 Valkey 容器队列；容器化多副本异常恢复演练仍是后续部署验收项。
- 对象存储：默认本地模式必须验证 key 归一化、路径穿越拒绝、上传/渲染/报告文件可读和 `/api/health` 状态；S3/MinIO 模式必须验证 bucket 连通性、对象上传、缓存下载、权限失败和大文件限制。`deploy/run-object-storage-e2e.ps1` 已覆盖真实 MinIO/S3 开发机 E2E；`deploy/run-compose-e2e.ps1 -WithObjectStorage` 用于在具备 Docker 的环境验证容器化 MinIO/S3 链路；权限配置、生命周期策略、备份恢复和大文件性能仍是后续部署验收项。
- 前端：构建通过，API 调用路径可配置，系统状态页能展示后端、数据库、OpenAPI 和 Worker 状态；审图流程状态应能反映系统、登录、项目图纸、版本、审查、问题和报告进度；项目、图纸、版本列表切换当前上下文后，应同步影响上传、预览、审查和报告选择；审查任务详情能展示步骤时间线和失败细节；dxf-viewer 能加载上传 DXF、显示图层，并在选中问题后按 CAD 证据范围或解析范围聚焦高亮；Canvas 仅作为手动诊断视图，不能自动掩盖正式预览失败。
- Complex DXF parser dataset：`datasets/parser/manifest.json` 中每个合成 DXF 样例都要通过 `tools/check_complex_dxf_dataset.py`，验证来源、许可证、SHA-256、块参照、属性、尺寸、文字、多图层、HATCH 填充、较高实体数量、HATCH bounds、CAD Worker PNG 渲染和非空图像；`tools/check_dxf_viewer_dataset.mjs` 必须验证同一批样例能被正式 `dxf-viewer` parser 接受。标记 `requiresWebglSmoke` 的样例在发布或演示前还应通过浏览器正式预览烟测：前端经后端鉴权文件接口加载 DXF Blob，`DxfViewerPreview` 报告加载成功、显示预期图层，并通过截图非空白检查。Canvas 诊断视图不能作为复杂样例通过条件。
- External DXF candidates：`tools/check_external_dxf_candidates.py --manifest-only` 必须在无网络环境验证来源 commit、许可证、署名、远程缓存策略、SHA-256 和验收字段；联网人工验收时运行完整命令，将固定版本下载到 `.run` 后验证 CAD Worker 解析/渲染，再运行 `tools/check_external_dxf_viewer_candidates.mjs` 验证正式 viewer parser。外部候选只能证明兼容性，不能直接作为规则合规真值。
- Golden dataset：`datasets/rules/expected.json` 中每个合成 DXF 样例都要通过 `tools/run_golden_e2e.py`，并由 `tools/check_rule_golden_coverage.py` 保证每条默认启用规则至少具备一个命中样例和一个不命中样例。当前覆盖合规样例、图层命名、空图层、标题栏、标题栏属性、标题栏版次一致性、尺寸标注、版本号、占位文字、实体数量异常，以及 OCR 占位文本和 YOLO/CAD 标题栏冲突等多模态规则。
- Demo walkthrough：`tools/run_demo_walkthrough.py` 应能用一个真实 DXF 样例走通登录、建项目、建图纸、上传版本、发起审查、生成问题、生成报告，并把项目 ID、图纸 ID、版本 ID、任务步骤、问题证据类型和报告预览写入 `.run/demo-walkthrough-*.md`，用于人工演示核查。
- 报告和问题清单：审查报告必须包含解析证据摘要、问题证据详情、规则代码、图层或实体引用、结构化 evidence chain；报告 Markdown 应写入对象存储并保留数据库副本和对象元数据；`GET /api/reports/{reportId}/download` 应通过鉴权返回 Markdown 附件，若报告已有对象 key，则不能用数据库副本掩盖对象读取失败；前端问题清单应按 CAD、规则、知识条款、YOLO、OCR 等来源分组展示证据。
- 整改闭环：`PATCH /api/issues/{issueId}` 应记录状态前后、经办人、操作人、说明和可选报告引用；`GET /api/issues/{issueId}/remediations` 应按时间顺序返回整改时间线；前端应支持开始整改、提交复核、关闭问题和重新打开。
- 版本对比：`GET /api/versions/compare` 应返回结构化版本差异，包括实体数量变化、图层新增/删除、图层实体数量变化、实体类型变化、块参照变化、文本变化、风险提示和复核重点；前端应以摘要、指标和表格展示，而不是只输出原始 JSON。
- Vision evidence：配置模型后，`POST /api/versions/{versionId}/vision-detect` 和 `POST /api/versions/{versionId}/vision-detect-rendered` 应能保存 `YOLO_SYMBOL` evidence；未配置模型时应返回明确错误，不能伪造检测结果。
- OCR evidence：配置 Tesseract 后，`POST /api/versions/{versionId}/ocr-recognize` 和 `POST /api/versions/{versionId}/ocr-recognize-rendered` 应能保存 `OCR_TEXT` evidence；未安装 OCR 引擎时应返回明确错误，不能伪造识别文本。
- 安全：数据库持久化且仅存摘要的 Token 会话、过期与主动撤销、禁用账号拒绝登录、密码策略、四角色操作级权限矩阵、项目级数据隔离、图纸文件及下游资源范围校验、403 越权拒绝、越权与登录失败审计、文件类型限制、20MB 限制、审计日志分页查询。
- 开源合规：依赖许可证记录、模型权重不入库、真实图纸不入库。
- 数据库迁移：空 H2 数据库应从零执行全部 Flyway 脚本；非空历史 H2 数据库应基线到版本 `0` 后完成加固迁移；迁移完成后 JPA 结构校验必须通过。H2 与 DM8 迁移脚本版本号必须保持同步，且每个 DM8 脚本必须写入 `shipcad_schema_version`。DM8 脚本必须在独立测试实例验证版本记录、后端启动、核心 CRUD 和 E2E 后才能标记为生产认证。
- DM8 兼容性基线：2026 年 6 月 7 日在 DM8 Pack8 `03134284404-20250930-295335-20164` 完成 V1/V2、Hibernate `validate`、健康检查和 Golden E2E 11/11；2026 年 6 月 8 日在同一本地隔离实例完成 V3/V4/V5 DIsql 执行、版本记录、新增列、当前后端 `prod` Profile Hibernate `validate`、数据库/队列/本地对象存储健康检查，并连接真实 CAD Worker 再次通过 DM8 Golden E2E 11/11。对象存储改造另已在 H2/local 和 H2/MinIO S3 链路通过 11/11。生产压测、备份恢复与高可用仍是独立验收项。
- 运行可观测性：`deploy/start-dev.ps1` 应能启动核心开发链路，`/api/health` 和前端“系统状态”应能展示后端、数据库、审查任务队列、对象存储、OpenAPI、CAD Worker 以及可选 Vision/OCR Worker 状态，`deploy/test-health.ps1` 应能检查后端、数据库、审查任务队列、对象存储、OpenAPI、CAD Worker、前端和可选 Vision/OCR Worker，`deploy/run-demo.ps1` 应能执行演示验收闭环，`deploy/run-demo-walkthrough.ps1` 应能生成单样例演示摘要，`deploy/run-task-retry-e2e.ps1` 应能验证失败任务重试边界，`deploy/run-redis-queue-e2e.ps1` 和 `deploy/run-object-storage-e2e.ps1` 应能分别验证真实 Redis 协议队列与真实 MinIO/S3 对象存储链路，`deploy/run-compose-e2e.ps1` 应能验证 Docker Compose 容器栈。

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
- Review task retry must be enforced by the backend service layer. `POST /api/review-tasks/{taskId}/retry` should accept only `FAILED` tasks, create a new queued task with the original review options, and write a `REVIEW_RETRY` audit event.
- `deploy/run-task-retry-e2e.ps1` runs the live API chain for this boundary: corrupt DXF upload, parse failure, retry creation, retry audit lookup, valid DXF completion, and finished-task retry rejection.
- Review task queue implementations should expose health details through `/api/health`; the default in-memory mode should report executor activity and queued count, and Redis mode should report queue key, processing key, worker status, Redis ping, queued count, and processing count.
- `deploy/run-redis-queue-e2e.ps1` starts a real Redis-compatible server, seeds a stale processing payload to verify startup recovery, boots an isolated backend in Redis queue mode, checks `/api/health` for `queue.mode=redis` and `queue.status=ok`, then runs `tools/run_golden_e2e.py --keep-going` so review tasks must be enqueued, consumed and completed through the Redis protocol queue.
- Object storage implementations should expose health details through `/api/health`; local mode should report root path writability, and S3 mode should report endpoint, bucket, cache root and bucket connectivity. Version file download should prefer `DrawingVersion.fileObjectKey` and fall back to legacy `filePath`; report download should prefer `ReportDocument.contentObjectKey` and fall back to database content only for legacy reports without an object key.
- `deploy/run-object-storage-e2e.ps1` starts a real MinIO server, boots an isolated backend in S3 mode, checks `/api/health`, then runs `tools/run_golden_e2e.py --evict-upload-cache` so version file download, review parsing and report attachment download must recover files from object storage instead of relying on upload/report caches.
- `deploy/run-compose-e2e.ps1` builds the Docker Compose stack, waits for CAD Worker, backend and frontend health, verifies `queue.mode=redis` through the Valkey container, and runs `tools/run_golden_e2e.py --keep-going`. With `-WithObjectStorage`, it also starts MinIO, expects `storage.mode=s3`, and evicts local caches during golden E2E.
- Review task detail UI should display the selected task status, stage, version, issue count, evidence count, ordered steps, timestamps, error message, and detail JSON summary.
- Frontend workflow UI should not create fake demo state. It may guide tab navigation and local selection, but all status values must come from authenticated API state, health checks, current selections, review tasks, issues, or generated reports.
- `OCR_PLACEHOLDER_TEXT` should generate an issue-level `OCR_TEXT` evidence reference with `sourceEvidenceId` when OCR text contains unfinished placeholders.
- `YOLO_TITLE_BLOCK_CAD_MISSING` should generate an issue-level `YOLO_SYMBOL` evidence reference with `sourceEvidenceId` when visual title-block evidence conflicts with CAD structured parsing.
- CAD entity evidence should persist the version-scoped CAD handle, anchor, and geometry bounds in `ReviewEvidence.location`.
- Rendered-version YOLO/OCR evidence should persist its top-left pixel bounds, image dimensions, render source, and raster-to-CAD transform. Issue-level evidence references must preserve the source location unchanged.
- Manually uploaded image evidence should retain raster bounds but must not claim a CAD transform without an explicit registration step.
- CAD Worker `/render` should return exact PNG viewport metadata in `X-ShipCAD-Render-Metadata`. Missing, invalid, or cached metadata without valid `modelBounds` must fail visibly instead of silently producing unmappable visual evidence.
- Browser `VIEWPORT` coordinates are derived from the current preview camera and are not persisted as authoritative evidence.
- `tools/check_complex_dxf_dataset.py` verifies complex parser fixture provenance, hash stability, CAD parser summaries, HATCH bounds, CAD Worker render metadata, and nonblank PNG output.
- `tools/check_dxf_viewer_dataset.mjs` verifies that `dxf-viewer` can parse the complex DXF fixtures and see required layers, blocks and entity types before the frontend build gate.
- `tools/check_external_dxf_candidates.py` keeps third-party DXF files out of Git, validates the pinned external manifest in CI, and performs opt-in local download/hash/parser/render checks. `tools/check_external_dxf_viewer_candidates.mjs` verifies the cached files with `dxf-viewer`; neither script assigns rule-compliance labels.
- Browser WebGL smoke for complex DXF fixtures should verify the product path `backend file endpoint -> frontend Blob URL -> DxfViewerPreview -> WebGL canvas`, including visible layer list, no official-preview failure state, and a nonblank `.dxf-webgl-host` screenshot. This is a release/demo check rather than a Canvas diagnostic fallback.
- `tools/check_rule_golden_coverage.py` verifies that default seeded rules have positive and negative golden coverage, rejects unknown rule codes, and checks `expectedIssueCount` consistency before live E2E runs.
- `tools/run_golden_e2e.py` verifies these evidence checks for the golden DXF dataset, including exact issue count, optional mock Vision/OCR task orchestration, required `YOLO_SYMBOL`/`OCR_TEXT` evidence, and raster evidence coordinate spaces.
- `tools/run_multimodal_evidence_e2e.py` verifies the live API chain for review-task automatic rendering, task-scoped `YOLO_SYMBOL` and `OCR_TEXT` evidence, raster-to-CAD location transforms, issue-level location preservation, rule consumption, `sourceEvidenceId` references, AI explanations, and report output. Its default mock workers are deterministic integration substitutes; they do not validate real model accuracy.
- `tools/run_demo_walkthrough.py` verifies one live rule-only review path, then uploads a second version and writes a human-readable Markdown summary for presentation and handoff checks.
- `tools/run_demo_walkthrough.py` also uploads a second DXF version, reviews it, calls `GET /api/versions/compare`, and records the version comparison summary in the walkthrough artifact.
- `tools/run_access_control_e2e.py` verifies role restoration through `/api/auth/me`, viewer read-only access, design engineer authoring boundaries, review expert separation, project membership grant/removal, cross-project HTTP 403 responses, administrator audit visibility, managed-user creation, own-password change, account disabling, logout, and immediate session invalidation.
- `ProjectAccessIntegrationTest` verifies project membership filtering and descendant authorization for drawings, versions, tasks, issues and reports, including `DATA_ACCESS_DENIED` auditing.
- `DatabaseMigrationTest` verifies clean H2 bootstrap, legacy non-empty schema adoption, enum normalization, unique constraints, foreign keys, and migration version tracking.
- `LocalObjectStorageServiceTest` verifies local object writes, path normalization and traversal rejection.

## 持续集成门禁

- `.github/workflows/ci.yml` 对每次 push 和 pull request 自动执行 Python Worker 测试、Spring Boot 后端测试和 Vue 前端生产构建。
- Python 门禁统一收集 CAD、OCR 和 Vision Worker 测试；Vision 的基础测试不依赖模型权重，重点验证未配置模型和无效权重路径能够明确失败。
- Live API E2E 在 GitHub 托管的 Linux 环境中启动真实 CAD Worker 和后端，执行 golden dataset 与审查任务失败重试验收。
- Live API E2E 无论成功或失败都保留服务日志和任务重试诊断文件 14 天，便于定位异步任务、解析或启动问题。
- `.github/workflows/dependency-review.yml` 在 pull request 中检查新增依赖，发现中危及以上漏洞时阻止通过；许可证结论仍需结合 `THIRD_PARTY_LICENSES.md` 人工复核。
- `.github/workflows/codeql.yml` 对 Java、JavaScript/TypeScript 和 Python 执行 CodeQL `security-extended` 查询，并每周执行一次计划扫描。
- `.github/workflows/secret-scan.yml` 使用校验过 SHA-256 的 Gitleaks CLI 扫描完整 Git 历史和当前工作目录，并上传脱敏 SARIF 诊断工件；不使用 `gitleaks-action`，避免组织仓库额外许可证要求和非开源 Action 依赖。
- `.github/workflows/sbom.yml` 使用 Syft 分别为后端、前端、CAD Worker、Vision Worker 和 OCR Worker 生成 SPDX JSON 组件 SBOM，作为开源发布、漏洞响应和许可证复核的依赖快照。
- `.github/dependabot.yml` 每周检查 GitHub Actions、Maven、npm、pip 和容器基础镜像更新；补丁与小版本按生态分组，大版本保持独立评估，禁止不经测试自动合并。
- `tools/check_python_requirements.py` 要求 CAD、Vision 和 OCR Worker 的直接 Python 依赖必须用 `==` 显式锁定；传递依赖以 SBOM 和 CI 安装结果记录，后续如进入正式发布可升级为哈希锁定。
- `tools/check_action_pins.py` 要求所有外部 GitHub Actions 引用固定到 40 位提交 SHA，并保留版本注释；Dependabot 负责后续升级提醒。
- `tools/check_release_readiness.py` 汇总开源必需文件、工作树、remote、禁止文件、GitHub 文件大小、依赖/Action 固定、复杂 DXF 解析基线、规则 golden 覆盖、Vision 数据和秘密扫描状态；正式发布模式下缺少 remote 或存在失败项必须阻止发布。
- `tools/validate_vision_dataset.py` 校验四类 Phase 1 taxonomy、YOLO 目录、图像/标签配对、归一化边界框、许可证、来源、公开批准、复核状态、文件哈希及原图分组隔离。当前仓库为 `0 images / 0 boxes`，只能证明数据工程结构就绪，不能证明模型可训练或具备识别精度。
- 上述 GitHub 安全能力只有在仓库发布到 GitHub 且启用 Actions、Dependency Graph 和 Code Security 后才会执行；本地存在配置文件不等于扫描已经通过。
- Docker Compose、MinIO、Redis/Valkey、DM8 和真实 YOLO 模型精度测试仍属于独立环境验收，不由基础 CI 伪造覆盖。

## 验收目标

- 一台 Windows 开发机可启动前端、后端、Worker。
- 上传 golden DXF 样例后，可通过审查任务队列生成规则问题、CAD 证据和知识条款证据。
- 问题状态可以流转到整改中、待复核和关闭，并保留可审计的整改时间线。
- 可导出包含 evidence chain 和 AI 辅助解释的审查报告。
- OpenAPI 文档可访问。
