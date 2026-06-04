# 开源MVP架构说明

## 业务架构

系统面向设计院审图组，围绕“图纸上传、DXF/DWG解析、规则审查、符号识别、问题整改、复核归档、版本对比、报告输出”形成闭环。

## 应用架构

- Vue Web 工作台：项目、图纸、版本、审图流程状态、当前上下文选择、系统状态、审查任务详情、dxf-viewer WebGL正式预览、Canvas诊断视图、问题定位、证据链分组、统计看板和报告导出。
- Spring Boot API：鉴权、主数据管理、文件管理、异步审查任务、Easy Rules规则审查、整改流转、审计日志和OpenAPI。
- Python CAD Worker：基于 ezdxf 解析 DXF 文件，并通过 ezdxf drawing/matplotlib 将版本渲染为 PNG；安装 LibreDWG 后，通过 `dwg2dxf` 转换 DWG 并复用 DXF 解析和渲染链路。
- Python Vision Worker：基于 Ultralytics YOLOv8 识别图纸渲染图中的符号目标，输出类别、置信度和检测框。
- Python OCR Worker：基于 Tesseract OCR 提取图纸渲染图中的文字区域，输出文本、置信度和检测框；后续可替换或增强为 PaddleOCR。
- AI Gateway：当前采用可审计的本地 evidence summarizer，只基于 `ReviewIssue` 与 evidence chain 生成解释；后续可替换为本地大模型或 OpenAI 兼容接口。

## 数据架构

开发环境使用 H2，生产目标为达梦 DM8。核心模型包括用户、角色、项目、图纸、图纸版本、解析实体、审查规则、审查任务、审查问题、整改记录、审计日志和报告。

## 技术架构

后端按照 Controller / Service / Repository 分层，CAD解析、CAD渲染和视觉识别作为独立Worker能力接入，避免重型CAD/AI依赖污染核心业务服务。上传版本后先入库，审查任务进入后台队列，由任务线程完成解析；如果任务开启自动 Vision/OCR，则先渲染版本 PNG 并采集任务级证据；随后执行规则、生成问题并回写状态。任务会同步维护 `review_task.stage` 和 `review_task_step`，记录 PARSE、RENDER、VISION、OCR、RULES 每一步的成功、跳过或失败原因，便于排障和验收；前端任务详情页负责展示步骤时间线、错误和 detailJson 摘要。规则通过 Easy Rules 注册和执行，后续可迁移到 Drools 或规则配置中心。文件默认保存到 `data/uploads`，版本渲染图缓存到 `data/rendered/{versionId}`，报告保存到数据库并可导出。

## 证据驱动架构

后续能力不应绕过审查流程直接给结论，而应统一进入证据层：

- CAD解析产生 `ParsedEntity` 证据。
- YOLOv8 产生 `DetectedSymbol` 证据。
- OCR 产生 `OcrTextRegion` 证据。
- 知识图谱产生 `KnowledgeClause` 证据。

规则引擎消费证据并生成 `ReviewIssue`，AI 基于问题和证据生成解释与报告。详细设计见 `docs/evidence_model.md`。

当前实现中，后端已新增 `ReviewEvidence`/`review_evidence`，现有确定性规则会为每个 `ReviewIssue` 自动保存 `RULE_RESULT` 与 CAD 证据。版本可通过 `/api/versions/{versionId}/rendered-image` 自动渲染为 PNG；Vision Worker 检测结果可通过手动图片、版本渲染图接口或审查任务自动编排写入 `YOLO_SYMBOL` 证据。OCR Worker 识别结果可通过手动图片、版本渲染图接口或审查任务自动编排写入 `OCR_TEXT` 证据。手动版本级证据可被后续任务复用；任务自动采集证据会绑定 `review_task.id`，规则只消费手动证据和当前任务自动证据，避免历史自动证据造成重复问题。`review_task_step` 只记录过程，不直接充当审查证据；真正的业务判断仍以 `review_evidence` 和 `review_issue` 为准。更完整的知识图谱后续不应直接绕过问题闭环，而应继续写入同一证据层。

## 部署架构

当前提供三类边界：

- 本地开发：PowerShell 脚本或手动启动前端、后端、CAD Worker、可选 Vision Worker 和可选 OCR Worker。
- 本地健康检查：`/api/health` 聚合数据库、OpenAPI、CAD Worker 和可选 Vision/OCR Worker 状态；前端“系统状态”页展示同一组状态；`deploy/test-health.ps1` 统一检查 `/api/health`、OpenAPI、Worker `/health`/`/capabilities` 和前端入口；`deploy/run-demo.ps1` 在服务启动后执行 golden dataset 演示验收。
- 容器部署：`deploy/docker-compose.yml` 构建并启动核心服务；使用 `--profile vision` 启动 YOLOv8 识别服务，使用 `--profile ocr` 启动 OCR 识别服务。
- 云原生占位：`deploy/kubernetes/shipcad-review.yaml` 提供 Deployment、Service、ConfigMap 和 PVC 骨架，后续可接入 DM8、Redis、MinIO 和 Ingress。

## 开源合规边界

项目源码采用 AGPL-3.0。ezdxf、LibreDWG、Ultralytics YOLOv8 等第三方依赖保留各自许可证。真实图纸、训练数据和模型权重不进入仓库。详细边界见 `docs/extension_boundaries.md`。

## 当前知识证据状态

当前已实现知识图谱路线的最小工程骨架：后端新增 `KnowledgeClause`/`knowledge_clause`，默认规则通过 `review_rule.knowledge_clause_code` 绑定内部审查依据，规则命中时会生成 `KNOWLEDGE_CLAUSE` evidence。后续接入 Neo4j、Apache Jena 或企业规范库时，应通过适配层替换或丰富 `KnowledgeClause` 来源，而不是绕过 `ReviewIssue` 和 `review_evidence`。

当前 AI Gateway 已实现最小 evidence-only 解释链路：`ReviewIssue.evidences` 会生成 `AiExplanation`，前端和报告均可展示。后续模型化时，必须保持“证据输入、解释输出、不可凭空新增结论”的边界。

前端审图流程状态条只负责展示和切换当前上下文：系统、登录、项目图纸、版本、审查、问题和报告状态均来自健康检查、登录状态、主数据、审查任务、问题列表或报告结果。该状态条不生成业务结论，也不能替代后端规则审查。
