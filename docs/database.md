# 数据库设计

## 开发与生产策略

当前开发环境使用 H2 文件数据库，便于单机快速启动和测试。H2 结构由 Flyway 迁移脚本创建和升级，Hibernate 固定使用 `ddl-auto=validate`，不再负责修改数据库。

生产交付目标为达梦 DM8。后端已引入达梦 JDBC Driver、官方 Hibernate 6.6 方言及项目适配层 `ShipCadDmDialect`，`prod` Profile 通过环境变量接收连接信息。由于 Flyway 官方支持数据库清单未包含 DM8，生产结构不复用 H2 自动迁移，而是使用 `deploy/database/dm8` 下经过审阅的 DIsql 版本脚本。核心实体模型保持一致，数据库类型、DDL 语法和执行方式按厂商分别维护。

## 核心表

- `app_user`：用户身份、显示名称、角色、启用状态、密码哈希、创建/更新时间、密码修改时间和最后登录时间。
- `auth_session`：持久化登录会话，保存 Token 的 SHA-256 摘要、用户引用、创建/过期/最后使用/撤销时间；不保存客户端原始 Token。
- `project`：审图项目。
- `project_member`：项目与用户的成员关系，`project_id + user_id` 唯一，并记录加入时间和添加人。
- `drawing`：图纸主数据。
- `drawing_version`：图纸版本、文件哈希、解析状态、解析摘要。
- `parsed_entity`：DXF 图元摘要。
- `review_rule`：审查规则；`knowledge_clause_code` 用于绑定规则依据条款。
- `knowledge_clause`：规则依据、规范条款或内部审查知识条目。
- `review_task`：审查任务，包含 PENDING、RUNNING、FINISHED、FAILED 状态、当前阶段 `stage`、失败原因和可选自动 Vision/OCR 证据采集配置。
- `review_task_step`：审查任务步骤记录，按 PARSE、RENDER、VISION、OCR、RULES 记录每一步的状态、开始/结束时间、说明和结构化详情。
- `review_issue`：规则命中问题。
- `review_evidence`：审查证据，保存 CAD 图元、CAD 图层、解析摘要、规则结果、知识条款以及后续 YOLO/OCR 证据。
- `remediation_record`：整改记录。
- `audit_log`：审计日志。
- `report_document`：审查报告。

## 身份与会话设计

`app_user.enabled=false` 时账号不能登录。角色或启用状态变化、用户修改密码、管理员重置密码时，服务会撤销该用户所有未撤销会话。管理员不能停用自身账号或撤销自身管理员角色，避免系统失去可管理入口。

`auth_session.token_hash` 具有唯一索引，认证时对请求 Token 计算 SHA-256 后查询。会话达到 `expires_at`、存在 `revoked_at` 或所属用户被停用时均不可继续使用。该设计使后端进程重启不会导致有效会话丢失，同时数据库泄露时不会直接暴露可用 Token。

## 项目数据范围

`project_member` 不重复保存业务角色。用户的 `app_user.role` 决定允许执行的操作，成员关系只决定非管理员能访问的项目集合。图纸、版本、任务、问题、证据和报告通过现有外键标识链回溯到项目，不在每张表重复写入成员信息。

管理员不依赖成员记录即可查看所有项目。普通用户创建项目后会自动写入成员关系。升级前已经存在但没有成员记录的项目默认只对管理员可见，必须由管理员明确分配成员，避免历史图纸在升级后被意外公开。

## 知识条款表设计

`knowledge_clause` 是当前知识图谱路线的最小落点。它还不是完整图数据库，但已经具备规则依据的核心字段：

- `code`：条款代码，例如 `BASIS_DIMENSION_EVIDENCE`。
- `title`：条款标题。
- `content`：条款内容或内部审查依据说明。
- `source`：来源，例如 `MVP_INTERNAL_RULE_BASIS`。当前种子数据是项目内部规则依据，不冒充真实船级社规范。
- `tags`：检索标签。
- `remediation_hint`：整改提示。

后续接入 Neo4j、Apache Jena 或企业规范库时，应通过适配层把外部查询结果转换为 `KnowledgeClause` 或 `KNOWLEDGE_CLAUSE` evidence。

## 证据表设计

`review_evidence` 是多证据来源的统一落点。当前已由规则引擎自动写入：

- `RULE_RESULT`：规则命中本身的判断证据。
- `CAD_ENTITY`：能定位到具体 `parsed_entity` 时的 CAD 图元证据。
- `CAD_LAYER`：图层级问题的 CAD 图层证据。
- `CAD_SUMMARY`：版本级问题的解析摘要证据。
- `KNOWLEDGE_CLAUSE`：规则绑定的依据条款证据。

`YOLO_SYMBOL` 已作为视觉证据接入，`OCR_TEXT` 已作为文字证据接入。手动生成的版本级证据 `task_id` 为空，可被后续审查任务复用；审查任务自动采集的证据会写入当前 `review_task.id`，规则引擎只消费手动版本级证据和当前任务自动证据，避免重复审查时历史自动证据造成重复问题。后续 Vision Worker、OCR Worker 和知识图谱模块接入时，应继续写入同一张证据表；已生成问题的证据由 `review_issue.evidences` 返回，版本级证据由版本 evidence 接口返回。规则消费 evidence 时，会生成新的 issue-level evidence 引用，并在 `payloadJson.sourceEvidenceId` 中指向原始证据。

## 任务步骤表设计

`review_task_step` 是审查任务的可观测过程记录，不参与规则结论本身。当前固定步骤如下：

- `PARSE`：CAD Worker 结构化解析 DXF/DWG。
- `RENDER`：生成版本渲染图，供自动 Vision/OCR 使用。
- `VISION`：调用 Vision Worker 生成 `YOLO_SYMBOL` 证据。
- `OCR`：调用 OCR Worker 生成 `OCR_TEXT` 证据。
- `RULES`：规则引擎消费 CAD、视觉、OCR 和知识条款证据，生成 `ReviewIssue`。

步骤状态包括 `RUNNING`、`SUCCESS`、`SKIPPED`、`FAILED`。默认规则审查任务会跳过自动渲染、Vision 和 OCR；开启自动多模态证据后，这些步骤必须显式成功或失败，不能静默丢失。

## 结构版本管理

- H2 脚本：`backend-spring/src/main/resources/db/migration/h2`。
- DM8 脚本：`deploy/database/dm8`。
- H2 新库会自动执行全部 Flyway 迁移；已有本地开发库在 `dev` Profile 下先基线到版本 `0`，再执行现有迁移。
- `baseline-on-migrate` 默认关闭，仅在开发 Profile 为接管历史 H2 库而开启，避免误连数据库时失去安全检查。
- DM8 脚本由 DIsql 按版本号顺序执行，并写入 `shipcad_schema_version`。已记录的脚本不得重复执行。
- JPA 在迁移后执行结构校验，表或列缺失时后端启动失败，不允许运行时静默补表。

V2 结构加固了用户名、会话 Token、项目成员、知识条款代码和规则代码的唯一性，并为主要资源链增加关联索引与外键。`remediation_record.report_id` 保留为历史引用而未建立外键，因为报告可能被重新生成或移除，整改时间线仍需保留原始引用值。

自动化测试覆盖空 H2 建库和非空历史 H2 接管。2026 年 6 月 7 日已在 DM8 Pack8 `03134284404-20250930-295335-20164` 独立实例完成 V1/V2 脚本执行、17 张表与 19 个外键核验、Hibernate `validate`、健康检查和 Golden E2E 11/11 验证。该结果证明当前版本具备 DM8 功能兼容性，不代表生产压测、备份恢复、高可用或灾难恢复已经完成。
