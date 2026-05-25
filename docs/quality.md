# 质量保证计划

## 必测场景

- Worker：正常DXF和异常DXF解析。
- DWG：安装 LibreDWG 后，验证 DWG 转 DXF 失败和成功两类路径。
- Vision Worker：未配置模型时返回明确错误；配置模型后能返回检测框结构。
- 后端：登录、项目创建、图纸创建、版本上传、异步审查任务、任务失败重试、问题整改、报告生成、版本对比。
- 前端：构建通过，API调用路径可配置，dxf-viewer 能加载上传DXF并显示图层；Canvas 仅作为手动诊断视图，不能自动掩盖正式预览失败。
- Golden dataset：`datasets/rules/expected.json` 中每个合成 DXF 样例都要通过 `tools/run_golden_e2e.py`，覆盖合规样例、图层命名、空图层、标题栏、版本号、占位文字和实体数量异常。
- 安全：Token鉴权、文件类型限制、20MB限制、审计日志。
- 开源合规：依赖许可证记录、模型权重不入库、真实图纸不入库。

## 验收目标

- 一台Windows开发机可启动前端、后端、Worker。
- 上传 `invalid_ship_section.dxf` 后可通过审查任务队列生成规则问题。
- 问题状态可以流转到整改中、待复核和关闭。
- 可导出审查报告。
- OpenAPI文档可访问。
