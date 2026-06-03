# 用户手册

## 默认账号

admin / admin123

## 演示流程

1. 启动 CAD Worker、Spring Boot 后端和 Vue 前端；需要符号识别时额外启动 Vision Worker，需要文字识别时额外启动 OCR Worker。
2. 登录系统。
3. 创建项目和图纸。
4. 上传 `samples/dxf/invalid_ship_section.dxf`，系统先创建图纸版本记录。DWG 文件需要本机安装 LibreDWG `dwg2dxf`。
5. 发起审查任务，等待任务从 PENDING/RUNNING 进入 FINISHED；如果进入 FAILED，可查看错误并重试。
6. 在“问题闭环”中查看 dxf-viewer 正式DXF预览；Canvas诊断视图只在需要排查解析结果时手动打开。
7. 查看问题清单并完成整改流转。
8. 生成审查报告。报告页会展示审查摘要、解析证据摘要、问题清单和问题证据详情，并支持复制/下载 Markdown。
9. 上传 `valid_ship_section.dxf` 作为新版本并做版本对比。

## YOLOv8 Vision Worker

Vision Worker 当前提供独立接口，用于后续图纸符号识别。运行前需要准备模型权重并设置 `YOLO_MODEL_PATH`。模型权重和真实训练数据不进入仓库。

启动 Vision Worker 后，可在“问题闭环”的预览工作区上传 PNG/JPG 图像并触发视觉检测。后端会把检测结果保存为当前版本的 `YOLO_SYMBOL` 证据。该证据目前只是视觉识别输入，不会自动生成审查问题；后续规则引擎和知识图谱会继续消费这些证据。

## OCR Worker

OCR Worker 当前提供独立接口，用于后续图纸文字识别。运行前需要安装 Tesseract OCR；中文图纸文字需要额外安装 `chi_sim` 语言数据并设置 `OCR_LANG=eng+chi_sim`。

启动 OCR Worker 后，可在“问题闭环”的预览工作区上传 PNG/JPG 图像并触发 OCR 识别。后端会把识别结果保存为当前版本的 `OCR_TEXT` 证据。该证据目前只是文字识别输入，不会自动生成审查问题；后续规则引擎和知识图谱会继续消费这些证据。
