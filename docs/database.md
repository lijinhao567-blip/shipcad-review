# 数据库设计

## 开发与生产策略

当前开发环境使用 H2 文件数据库，便于单机快速启动和测试。生产交付目标为达梦 DM8，后续只需替换 JDBC Driver、连接串和方言配置，核心实体模型保持不变。

## 核心表

- `app_user`：用户、角色、密码哈希。
- `project`：审图项目。
- `drawing`：图纸主数据。
- `drawing_version`：图纸版本、文件哈希、解析状态、解析摘要。
- `parsed_entity`：DXF 图元摘要。
- `review_rule`：审查规则；`knowledge_clause_code` 用于绑定规则依据条款。
- `knowledge_clause`：规则依据、规范条款或内部审查知识条目。
- `review_task`：审查任务，包含 PENDING、RUNNING、FINISHED、FAILED 状态、失败原因和可选自动 Vision/OCR 证据采集配置。
- `review_issue`：规则命中问题。
- `review_evidence`：审查证据，保存 CAD 图元、CAD 图层、解析摘要、规则结果、知识条款以及后续 YOLO/OCR 证据。
- `remediation_record`：整改记录。
- `audit_log`：审计日志。
- `report_document`：审查报告。

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

## 迁移建议

商业化或长期维护版本建议引入 Flyway 或 Liquibase 管理达梦 DM8 脚本，避免依赖 `ddl-auto=update`。
