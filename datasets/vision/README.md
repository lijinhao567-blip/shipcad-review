# Vision Dataset

本目录是船舶 CAD 图纸符号检测数据集的公开仓库边界，格式兼容 Ultralytics YOLO detection。

当前状态是“数据结构与验收规则已建立，尚无可训练样本”。空目录不能用于训练，也不代表模型能力已经完成。

## Phase 1 类别

| ID | 类别 | 用途 |
|---:|---|---|
| 0 | `welding_symbol` | 焊接符号视觉证据 |
| 1 | `section_marker` | 剖切符号与剖面索引证据 |
| 2 | `title_block` | 与 CAD 标题栏解析做交叉校验 |
| 3 | `revision_block` | 与版次、修订记录和 OCR 结果做交叉校验 |

类别 ID 一旦产生正式标签和模型权重后不得重排。新增类别只能追加。

## 目录

```text
datasets/vision/
  classes.json
  data.yaml
  manifest.json
  images/{train,val,test}/
  labels/{train,val,test}/
```

每个图像必须有同名 `.txt` 标签，标签行格式为：

```text
class_id x_center y_center width height
```

坐标按图像宽高归一化到 `[0, 1]`。每个进入仓库的图像还必须在 `manifest.json` 中记录：

- `image`、`label`、`split`
- `sourceType`: `self_created`、`synthetic` 或 `public`
- `source`、`license` 和公开来源信息；公开样本还需要 `sourceUrl`、`licenseUrl`
- `publicReleaseApproved: true`
- `annotationStatus: reviewed`
- `groupId`：同一原图及其切片使用同一个值，且只能出现在一个 split
- 图像文件 `sha256`

真实船厂、客户或未经授权的图纸禁止进入本目录。公开网页“能下载”不等于允许再发布，许可证不明确的数据不能提交。

## 划分原则

- 同一原始图纸的切片必须全部放在同一个 split，避免数据泄漏。
- 首批目标至少每类 200 个实例，并准备包含相似线条但不属于目标类别的负样本。
- `test` 在模型方案确定后冻结，不用来调参。
- 标注完成后由第二人抽检；边界争议写入标注说明，不靠个人临场判断。

## 校验

结构校验允许空数据集：

```powershell
.\.venv\Scripts\python.exe tools\validate_vision_dataset.py
```

训练前必须要求至少存在一个样本：

```powershell
.\.venv\Scripts\python.exe tools\validate_vision_dataset.py --require-samples
```

CVAT 可作为标注工具，但导出后仍须转换或整理为本目录结构并通过上述校验。CVAT 的传统 YOLO 导出只支持 `train`、`valid` 子集；本项目最终使用 `train`、`val`、`test`，因此不能直接把未检查的导出压缩包提交进仓库。
