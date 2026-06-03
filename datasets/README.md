# Golden Dataset

本目录保存可公开提交的合成 DXF 验收数据，用于验证“上传 -> 解析 -> 规则审查 -> 问题生成 -> 文件预览接口”这条主链路。

## 当前数据集

```text
datasets/
  rules/
    cases/
      compliant_section.dxf
      invalid_layer_name.dxf
      empty_custom_layer.dxf
      missing_title_block.dxf
      placeholder_text.dxf
      low_entity_density.dxf
      invalid_version_format.dxf
    expected.json
```

`expected.json` 记录每个样例的上传版本号、预期规则命中、解析层级要求、问题定位证据和说明。系统不能按文件名返回结果，文件名只用于定位输入数据；验收脚本会真实上传 DXF 并检查后端审查结果。

## 重新生成

```powershell
.\.venv\Scripts\python.exe tools\generate_golden_dataset.py
```

## 端到端验收

先启动 CAD Worker 和 Spring Boot 后端：

```powershell
.\.venv\Scripts\python.exe -m uvicorn cad_worker.app.main:app --host 127.0.0.1 --port 9000
$env:JAVA_HOME=(Resolve-Path .tools\jdk-17).Path
.\.tools\maven\bin\mvn.cmd -f backend-spring\pom.xml spring-boot:run
```

然后执行：

```powershell
.\.venv\Scripts\python.exe tools\run_golden_e2e.py --keep-going
```

全部通过时应看到 `7/7 cases passed`。
