# 数据库设计

## 开发与生产策略

当前开发环境使用 H2 文件数据库，便于单机快速启动和测试。生产交付目标为达梦 DM8，后续只需替换 JDBC Driver、连接串和方言配置，核心实体模型保持不变。

## 核心表

- `app_user`：用户、角色、密码哈希
- `project`：审图项目
- `drawing`：图纸主数据
- `drawing_version`：图纸版本、文件哈希、解析状态、解析摘要
- `parsed_entity`：DXF图元摘要
- `review_rule`：审查规则
- `review_task`：审查任务，包含 PENDING、RUNNING、FINISHED、FAILED 状态和失败原因
- `review_issue`：规则命中问题
- `review_evidence`：审查证据，保存 CAD 图元、CAD 图层、解析摘要、规则结果以及后续 YOLO/OCR/知识图谱证据
- `remediation_record`：整改记录
- `audit_log`：审计日志
- `report_document`：审查报告

## 证据表设计

`review_evidence` 是后续多证据来源的统一落点。当前已由规则引擎自动写入：

- `RULE_RESULT`：规则命中本身的判断证据。
- `CAD_ENTITY`：能定位到具体 `parsed_entity` 时的 CAD 图元证据。
- `CAD_LAYER`：图层级问题的 CAD 图层证据。
- `CAD_SUMMARY`：版本级问题的解析摘要证据。

预留类型包括 `YOLO_SYMBOL`、`OCR_TEXT`、`KNOWLEDGE_CLAUSE`。后续 Vision Worker、OCR Worker 和知识图谱模块接入时，应写入同一张证据表，并由 `review_issue.evidences` 返回给前端和报告生成器。

## 迁移建议

商业化版本建议引入 Flyway 或 Liquibase 管理达梦 DM8 脚本，避免依赖 `ddl-auto=update`。
