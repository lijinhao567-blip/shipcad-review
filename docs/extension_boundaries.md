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

当前状态：已新增 `vision_worker` 服务骨架，并通过后端版本渲染图检测、手动图片检测和审查任务自动编排写入 `YOLO_SYMBOL` 证据。规则层已实现 `YOLO_TITLE_BLOCK_CAD_MISSING` 作为最小消费点。

目标能力：

- 识别焊接符号、箭头、剖切符号、索引标识等图纸视觉符号。
- 返回符号类别、置信度和检测框坐标。
- 检测结果进入统一证据层后，由规则引擎、知识条款和报告生成流程消费；后续重点是扩展真实模型权重、符号类别和更多规则，而不是绕过证据链直接给结论。

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

## DM8、Redis/Valkey、MinIO

当前状态：H2 本地开发已接入 Flyway 版本化迁移；DM8 已完成 V1/V2 真实实例上的 DIsql 脚本、Hibernate 结构校验和 Golden E2E 兼容性验证，V3 对象存储元数据脚本已在本地 DM8 实例完成 DDL、版本记录、Hibernate `validate` 和健康检查验证；V4 报告对象存储元数据脚本已准备，需在下次 DM8 维护窗口执行。审查任务队列已抽象为 `ReviewTaskQueue`，默认本地模式仍使用进程内队列，Redis 协议模式已实现入队、后台消费、处理队列恢复和健康检查；真实 Redis 协议开发机 E2E 已验证 Redis 队列模式下的 golden 审查闭环，`deploy/run-task-retry-e2e.ps1` 已覆盖坏 DXF 失败、失败任务重试、完成任务拒绝重试和审计记录，部署骨架使用 Valkey 作为开源 Redis 协议服务，并已提供 `deploy/run-compose-e2e.ps1` 用于容器栈验收。文件存储已抽象为 `ObjectStorageService`，默认本地文件系统，S3 兼容模式可接 MinIO 或其它 S3-compatible 对象存储，并保留 Worker/下载本地缓存；真实 MinIO 开发机 E2E 已验证对象上传、缓存删除后下载恢复、审查解析链路和报告附件下载，Compose 验收脚本可通过 `-WithObjectStorage` 覆盖容器化 MinIO 链路。

落地路径：

1. 为 DM8 补充备份恢复、故障演练、性能基线和生产部署规范。
2. 在具备 Docker 的机器上执行 Valkey 容器 E2E，并补充容器化异常恢复演练；当前 Redis 协议开发机 E2E 已覆盖基础入队消费闭环，失败任务重试已有开发机 E2E，当前实现适合单后端副本或受控试点，多副本高可用后续应评估 Redis Streams、可靠 ACK 或专用消息队列。
3. 在具备 Docker 的机器上执行 MinIO 容器 E2E，并补充权限配置、bucket 生命周期、备份恢复和大文件性能验收；当前开发机 E2E 已覆盖基础 S3 链路和报告附件对象化。
