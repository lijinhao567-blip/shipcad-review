# 开源MVP架构说明

## 业务架构

系统面向设计院审图组，围绕“图纸上传、DXF/DWG解析、规则审查、符号识别、问题整改、复核归档、版本对比、报告输出”形成闭环。

## 应用架构

- Vue Web 工作台：项目、图纸、版本、Canvas图纸预览、问题定位、统计看板和报告导出。
- Spring Boot API：鉴权、主数据管理、文件管理、异步审查任务、Easy Rules规则审查、整改流转、审计日志和OpenAPI。
- Python CAD Worker：基于 ezdxf 解析 DXF 文件；安装 LibreDWG 后，通过 `dwg2dxf` 转换 DWG 并复用 DXF 解析链路。
- Python Vision Worker：基于 Ultralytics YOLOv8 识别图纸渲染图中的符号目标，输出类别、置信度和检测框。
- AI Gateway：第一阶段采用可审计的摘要生成器，后续可替换为本地大模型或OpenAI兼容接口，并连接符号识别结果与知识图谱。

## 数据架构

开发环境使用 H2，生产目标为达梦 DM8。核心模型包括用户、角色、项目、图纸、图纸版本、解析实体、审查规则、审查任务、审查问题、整改记录、审计日志和报告。

## 技术架构

后端按照 Controller / Service / Repository 分层，CAD解析和视觉识别作为独立Worker，避免重型CAD/AI依赖污染核心业务服务。上传版本后先入库，审查任务进入后台队列，由任务线程完成解析、规则执行、问题生成和状态回写。规则通过 Easy Rules 注册和执行，后续可迁移到 Drools 或规则配置中心。文件默认保存到 `data/uploads`，报告保存到数据库并可导出。

## 证据驱动架构

后续能力不应绕过审查流程直接给结论，而应统一进入证据层：

- CAD解析产生 `ParsedEntity` 证据。
- YOLOv8 产生 `DetectedSymbol` 证据。
- OCR 产生 `OcrTextRegion` 证据。
- 知识图谱产生 `KnowledgeClause` 证据。

规则引擎消费证据并生成 `ReviewIssue`，AI 基于问题和证据生成解释与报告。详细设计见 `docs/evidence_model.md`。

## 部署架构

当前提供三类边界：

- 本地开发：PowerShell 脚本或手动启动前端、后端、CAD Worker 和可选 Vision Worker。
- 容器部署：`deploy/docker-compose.yml` 构建并启动核心服务；使用 `--profile vision` 启动 YOLOv8 识别服务。
- 云原生占位：`deploy/kubernetes/shipcad-review.yaml` 提供 Deployment、Service、ConfigMap 和 PVC 骨架，后续可接入 DM8、Redis、MinIO 和 Ingress。

## 开源合规边界

项目源码采用 AGPL-3.0。ezdxf、LibreDWG、Ultralytics YOLOv8 等第三方依赖保留各自许可证。真实图纸、训练数据和模型权重不进入仓库。详细边界见 `docs/extension_boundaries.md`。
