# 开源能力与扩展边界

项目定位已切换为 AGPL-3.0 开源项目，因此可以把 YOLOv8 与 LibreDWG 纳入开源技术路线。但它们仍然需要清晰边界：源码可以开源，模型权重、训练数据、用户图纸和本机安装的系统工具不进入仓库。

## DWG 解析：LibreDWG 路线

当前状态：已在 `cad_worker/app/dwg_adapter.py` 增加 LibreDWG 适配器。

实现方式：

1. CAD Worker 接收 `.dwg` 文件。
2. 调用本机或容器中的 `dwg2dxf` 命令转换为临时 DXF。
3. 复用现有 `ezdxf` 解析逻辑，输出统一的实体摘要、图层、文字、块和几何数据。

运行前提：

- 安装 GNU LibreDWG 命令行工具。
- 确保 `dwg2dxf` 在 PATH 中，或设置 `LIBREDWG_DWG2DXF_BIN`。

限制：

- LibreDWG 对不同 DWG 版本的兼容性可能不完全一致。
- 转换失败时任务会进入 FAILED，前端可查看失败原因并重试。

## YOLOv8 图纸符号识别

当前状态：已新增 `vision_worker` 服务骨架。

目标能力：

- 识别焊接符号、箭头、剖切符号、索引标识等图纸视觉符号。
- 返回符号类别、置信度和检测框坐标。
- 后续由后端把检测结果与规则引擎、知识图谱和报告生成流程连接。

运行前提：

- 安装 `vision_worker/requirements.txt`。
- 准备 YOLOv8 模型权重，例如 `models/best.pt`。
- 设置 `YOLO_MODEL_PATH`。

仓库边界：

- 不提交真实船舶图纸。
- 不提交大型模型权重。
- 训练数据只提交结构说明、类别定义和少量可公开样例。

## Drools 复杂规则库

当前状态：已实现 Easy Rules，可迁移。

原因：当前阶段规则数量少，Easy Rules 更轻量，便于先形成稳定的规则输入输出契约。

落地路径：

1. 保持后端 `RuleEngine` 的输入输出契约不变。
2. 新增 Drools 适配器，规则包版本化管理。
3. 增加规则审批、灰度发布和回滚机制。

## DM8、Redis、MinIO

当前状态：H2 本地开发已接入 Flyway 版本化迁移；DM8 已提供生产 profile、JDBC/Dialect 依赖和 DIsql 脚本，但仍需要真实 DM8 实例完成兼容性认证。文件系统存储和内存任务队列仍是当前默认实现。

落地路径：

1. 使用真实 DM8 实例执行 `deploy/database/dm8` 脚本，验证约束、索引、事务和 Hibernate `validate`。
2. Redis 接管分布式任务状态和队列削峰。
3. MinIO 接管原始图纸、解析结果和报告对象存储。
