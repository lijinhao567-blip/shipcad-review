# 船舶CAD图纸AI智能审查与版本管理系统

面向船舶设计院审图组的 AGPL-3.0 开源智能审图平台。系统采用 Web 审图平台形态，支持 DXF 解析，并预留基于 LibreDWG 的 DWG 解析能力和基于 YOLOv8 的图纸符号识别能力，提供图纸上传、解析、规则审查、问题整改、版本对比、统计看板和审查报告导出能力。

## 技术栈

- 前端：Vue 3 + TypeScript + Vite + Canvas 图纸预览
- 后端：Spring Boot 3 + Spring Data JPA + OpenAPI
- CAD Worker：Python + FastAPI + ezdxf + LibreDWG 命令行适配
- Vision Worker：Python + FastAPI + Ultralytics YOLOv8
- 规则引擎：Easy Rules
- 开发数据库：H2；生产目标：达梦 DM8
- 部署目标：开源自托管、私有化内网部署

## 架构边界

当前仓库已经按前端、后端、CAD Worker、Vision Worker 拆分。上传文件先生成版本记录，审查任务进入后端任务队列，再由后台线程调用 CAD Worker 解析并执行规则审查。DWG 解析通过 LibreDWG `dwg2dxf` 适配，YOLOv8 识别通过独立 Vision Worker 接入，训练数据与模型权重不进入仓库。

## 本地启动

工具已统一放在当前项目 `.tools` 目录，Python 依赖放在 `.venv`。

```powershell
# 1. 启动 CAD Worker，用于 DXF / DWG 解析
.\.venv\Scripts\python.exe -m uvicorn cad_worker.app.main:app --host 127.0.0.1 --port 9000

# 2. 可选：启动 YOLOv8 Vision Worker
# 需要先安装 vision_worker/requirements.txt 并配置 YOLO_MODEL_PATH
.\.venv\Scripts\python.exe -m uvicorn vision_worker.app.main:app --host 127.0.0.1 --port 9100

# 3. 启动 Spring Boot 后端
$env:JAVA_HOME=(Resolve-Path .tools\jdk-17).Path
.\.tools\maven\bin\mvn.cmd -f backend-spring\pom.xml spring-boot:run

# 4. 启动 Vue 前端
cd frontend-vue
npm run dev
```

访问地址：

- 前端：http://127.0.0.1:5173
- 后端 API：http://127.0.0.1:8080
- OpenAPI：http://127.0.0.1:8080/swagger-ui.html
- CAD Worker：http://127.0.0.1:9000/docs
- Vision Worker：http://127.0.0.1:9100/docs

默认账号：

```text
admin / admin123
```

## 验证

```powershell
.\.venv\Scripts\python.exe -m pytest cad_worker\tests
$env:JAVA_HOME=(Resolve-Path .tools\jdk-17).Path
.\.tools\maven\bin\mvn.cmd -f backend-spring\pom.xml test
cd frontend-vue
npm run build
```

## 当前已实现能力

- DXF 上传、异步解析和实体几何提取
- DWG 上传入口和 LibreDWG 转 DXF 解析适配，需要本机安装 `dwg2dxf`
- 审查任务队列：支持 PENDING、RUNNING、FINISHED、FAILED 状态和失败重试
- Canvas 图纸预览，支持线、圆、弧、文字、块参照和多段线的基础显示
- 问题定位高亮：按问题关联图元或图层高亮显示
- YOLOv8 Vision Worker 骨架：支持上传渲染图并返回符号检测框，需配置模型权重
- Easy Rules 规则审查：图层命名、空图层、标题栏、版次格式、占位文本、实体数量
- 问题整改闭环、审查报告、统计看板和版本对比

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
```

云原生部署占位文件位于 `deploy/kubernetes/shipcad-review.yaml`。

## 开源许可证

本项目源码采用 GNU Affero General Public License v3.0，见 `LICENSE`。第三方依赖保留其各自许可证，详见 `THIRD_PARTY_LICENSES.md`。
