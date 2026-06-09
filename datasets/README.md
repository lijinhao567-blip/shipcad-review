# Datasets

本目录保存可公开提交的合成验收数据。数据集只用于解析、审查、预览和模型数据工程验证，不包含真实船厂、客户或涉密图纸。

## Parser Dataset

复杂 DXF 解析与预览基线位于：

```text
datasets/
  parser/
    cases/
      complex_ship_section.dxf
      dense_deck_grid.dxf
    manifest.json
```

`manifest.json` 记录每个样例的来源、许可证、生成脚本、SHA-256、预期解析摘要和正式 `dxf-viewer` 兼容预期。当前样例均由 `tools/generate_complex_dxf_dataset.py` 自造生成，覆盖块参照、属性、尺寸、文字、多图层、HATCH 填充和较高实体数量。

常用命令：

```powershell
.\.venv\Scripts\python.exe tools\generate_complex_dxf_dataset.py
.\.venv\Scripts\python.exe tools\check_complex_dxf_dataset.py
node tools\check_dxf_viewer_dataset.mjs
```

`check_complex_dxf_dataset.py` 会用 CAD Worker parser 和 renderer 校验结构化解析、HATCH bounds、PNG 渲染和非空图像。`check_dxf_viewer_dataset.mjs` 会用 `dxf-viewer` 自带 parser 校验正式预览链路可接受这些 DXF；WebGL 预览 smoke 仍需在本地浏览器或演示环境中确认。

## External DXF Candidates

真实开源 DXF 候选清单位于：

```text
datasets/
  external/
    manifest.json
    README.md
```

仓库只保存来源、固定 Git commit、许可证、署名、SHA-256、文件大小和验收期望，不直接提交第三方 DXF。本地验证会下载到 `.run/external-dxf-candidates/`：

```powershell
.\.venv\Scripts\python.exe tools\check_external_dxf_candidates.py
node tools\check_external_dxf_viewer_candidates.mjs
```

当前候选包括 Baby AUV 复杂机械图（CC BY-SA 4.0）和 RC boat tunnel hull（MIT）。它们用于真实文件兼容性、性能和预览回归，不是船级社规则合规真值。

## Rule Golden Dataset

规则审查 golden dataset 位于：

```text
datasets/
  rules/
    cases/
      *.dxf
    expected.json
```

`expected.json` 记录每个样例的上传版本号、预期规则命中、问题数量、解析层级要求、问题定位证据和说明。系统不能按文件名返回结果，文件名只用于定位输入数据；验收脚本会真实上传 DXF 并检查后端审查结果。

常用命令：

```powershell
.\.venv\Scripts\python.exe tools\generate_golden_dataset.py
.\.venv\Scripts\python.exe tools\check_rule_golden_coverage.py
.\.venv\Scripts\python.exe tools\run_golden_e2e.py --keep-going
```

## Vision Dataset

视觉数据集工程结构位于 `datasets/vision/`。当前已建立四类首批 taxonomy、YOLO 目录、来源/许可证 manifest 和校验器，但尚未提交可训练图像；不能把目录存在误写成“已完成 YOLO 数据集”。

```powershell
.\.venv\Scripts\python.exe tools\validate_vision_dataset.py
```
