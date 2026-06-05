# 船舶CAD图纸AI智能审查与版本管理系统

面向船舶设计院审图组的 AGPL-3.0 开源智能审图平台。系统采用 Web 审图平台形态，支持 DXF 解析，并预留基于 LibreDWG 的 DWG 解析能力、基于 YOLOv8 的图纸符号识别能力和基于 OCR 的文字证据能力，提供图纸上传、解析、规则审查、问题整改、版本对比、统计看板和审查报告导出能力。

## 技术栈

- 前端：Vue 3 + TypeScript + Vite + dxf-viewer WebGL正式预览，Canvas仅作诊断视图，工作台提供审图流程状态和当前上下文选择
- 后端：Spring Boot 3 + Spring Data JPA + OpenAPI
- CAD Worker：Python + FastAPI + ezdxf + matplotlib 渲染 + LibreDWG 命令行适配
- Vision Worker：Python + FastAPI + Ultralytics YOLOv8
- OCR Worker：Python + FastAPI + Tesseract OCR
- 规则引擎：Easy Rules
- 开发数据库：H2；生产目标：达梦 DM8
- 部署目标：开源自托管、私有化内网部署

## 架构边界

当前仓库已经按前端、后端、CAD Worker、Vision Worker、OCR Worker 拆分。上传文件先生成版本记录，审查任务进入后端任务队列，再由后台线程调用 CAD Worker 解析并执行规则审查。审查任务可选自动采集视觉/OCR证据：先将图纸版本渲染为 PNG，再调用 YOLOv8 和 OCR Worker，最后统一进入规则引擎。每个审查任务会记录当前阶段和 PARSE、RENDER、VISION、OCR、RULES 步骤状态，前端提供任务详情页用于查看每一步的状态、时间和错误细节。DWG 解析通过 LibreDWG `dwg2dxf` 适配；训练数据、模型权重和真实图纸不进入仓库。

## 本地启动

工具已统一放在当前项目 `.tools` 目录，Python 依赖放在 `.venv`。

推荐使用开发脚本启动核心链路：

```powershell
# 启动 CAD Worker、Spring Boot 后端和 Vue 前端
.\deploy\start-dev.ps1

# 启动时同时拉起 Vision/OCR Worker
.\deploy\start-dev.ps1 -WithVision -WithOcr

# 如果只需要跑后端验收，不启动前端
.\deploy\start-dev.ps1 -NoFrontend
```

常用运维脚本：

```powershell
# 检查后端、OpenAPI、CAD Worker、前端和可选 Worker 健康状态
.\deploy\test-health.ps1
.\deploy\test-health.ps1 -IncludeVision -IncludeOcr

# 跑完整 golden dataset 演示验收
.\deploy\run-demo.ps1

# 生成单样例 Markdown 演示走查摘要（输出到 .run/）
.\deploy\run-demo-walkthrough.ps1

# 跑 golden dataset + mock Vision/OCR 多模态验收
.\deploy\run-demo.ps1 -Multimodal

# 停止由 start-dev.ps1 启动的开发服务
.\deploy\stop-dev.ps1
```

`start-dev.ps1` 会把运行状态和日志写入 `.run/`，该目录仅用于本地运行，不进入仓库。

手动启动方式如下：

```powershell
# 1. 启动 CAD Worker，用于 DXF / DWG 解析
.\.venv\Scripts\python.exe -m uvicorn cad_worker.app.main:app --host 127.0.0.1 --port 9000

# 2. 可选：启动 YOLOv8 Vision Worker
# 需要先安装 vision_worker/requirements.txt 并配置 YOLO_MODEL_PATH
.\.venv\Scripts\python.exe -m uvicorn vision_worker.app.main:app --host 127.0.0.1 --port 9100

# 3. 可选：启动 OCR Worker
# 需要先安装 ocr_worker/requirements.txt 和 Tesseract OCR
.\.venv\Scripts\python.exe -m uvicorn ocr_worker.app.main:app --host 127.0.0.1 --port 9200

# 4. 启动 Spring Boot 后端
$env:JAVA_HOME=(Resolve-Path .tools\jdk-17).Path
.\.tools\maven\bin\mvn.cmd -f backend-spring\pom.xml spring-boot:run

# 5. 启动 Vue 前端
cd frontend-vue
npm run dev
```

访问地址：

- 前端：http://127.0.0.1:5173
- 后端 API：http://127.0.0.1:8080
- OpenAPI：http://127.0.0.1:8080/swagger-ui.html
- CAD Worker：http://127.0.0.1:9000/docs
- Vision Worker：http://127.0.0.1:9100/docs
- OCR Worker：http://127.0.0.1:9200/docs

健康检查：

- 核心健康接口：http://127.0.0.1:8080/api/health
- 前端系统状态页：登录前后均可在“系统状态”查看后端、数据库、OpenAPI、CAD Worker 和可选 Vision/OCR Worker 状态
- PowerShell 检查：`.\deploy\test-health.ps1`

默认账号：

```text
admin / admin123
```

## 验证

```powershell
.\.venv\Scripts\python.exe -m pytest cad_worker\tests
.\.venv\Scripts\python.exe -m pytest ocr_worker\tests
$env:JAVA_HOME=(Resolve-Path .tools\jdk-17).Path
.\.tools\maven\bin\mvn.cmd -f backend-spring\pom.xml test
cd frontend-vue
npm run build
```

Golden dataset 端到端验收需要后端和 CAD Worker 已启动：

```powershell
.\.venv\Scripts\python.exe tools\run_golden_e2e.py --keep-going
# 或
.\deploy\run-demo.ps1

# 生成可人工查阅的单样例演示走查摘要
.\deploy\run-demo-walkthrough.ps1
```

Multimodal evidence E2E needs the backend and CAD Worker running. By default it starts deterministic mock Vision/OCR workers on `127.0.0.1:9100` and `127.0.0.1:9200`, so it can validate review-task orchestration for CAD rendering, YOLO evidence, OCR evidence, rule consumption, and report output without real YOLO weights or Tesseract:

```powershell
.\.venv\Scripts\python.exe tools\run_multimodal_evidence_e2e.py
```

If Windows blocks `9100/9200`, start the backend with matching ports and pass the same ports to the script:

```powershell
.\deploy\start-dev.ps1 -NoFrontend -VisionPort 9110 -OcrPort 9210
.\deploy\run-demo.ps1 -Multimodal -VisionPort 9110 -OcrPort 9210
```

## 当前已实现能力

- DXF 上传、异步解析和实体几何提取
- DWG 上传入口和 LibreDWG 转 DXF 解析适配，需要本机安装 `dwg2dxf`
- CAD Worker 图纸渲染：支持将 DXF/DWG 版本渲染为 PNG，并缓存到 `data/rendered/{versionId}`
- 审查任务队列：支持 PENDING、RUNNING、FINISHED、FAILED 状态、阶段/步骤进度、失败重试和可选自动 Vision/OCR 证据采集
- 系统状态页和审查任务详情页：支持查看组件健康、必需/可选 Worker 状态、任务步骤时间线和失败细节
- 审图流程工作台：显示系统、登录、项目图纸、版本、审查、问题、报告的当前进度，并可在项目、图纸、版本列表中设置当前上下文
- dxf-viewer DXF正式预览，支持图层查看；Canvas仅用于人工诊断解析实体
- 问题定位高亮：按问题关联图元或图层高亮显示
- YOLOv8 Vision Worker 骨架：支持使用版本渲染图或手动上传图像生成符号检测框，需配置模型权重
- OCR Worker 骨架：支持使用版本渲染图或手动上传图像生成文字区域，需安装 Tesseract OCR
- Easy Rules 规则审查：图层命名、空图层、标题栏、版次格式、占位文本、实体数量、OCR占位文本、YOLO/CAD标题栏交叉校验
- 问题整改闭环、按来源分组的证据链展示、审查报告、服务端 Markdown 附件下载、统计看板和版本对比

## 下一阶段重点

- 开源工具集成策略：`docs/integration_strategy.md`
- 多来源证据模型：`docs/evidence_model.md`
- CAD Viewer 预研：`docs/experiment_cad_viewer_integration.md`
- 工具调研清单：`docs/open_source_tools_survey.md`

## 容器部署骨架

```powershell
cd deploy
docker compose up --build

# 启动包含 YOLOv8 Vision Worker 的 profile
docker compose --profile vision up --build

# 启动包含 OCR Worker 的 profile
docker compose --profile ocr up --build
```

云原生部署占位文件位于 `deploy/kubernetes/shipcad-review.yaml`。

## 开源许可证

本项目源码采用 GNU Affero General Public License v3.0，见 `LICENSE`。第三方依赖保留其各自许可证，详见 `THIRD_PARTY_LICENSES.md`。
