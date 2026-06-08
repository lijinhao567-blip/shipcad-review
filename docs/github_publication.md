# GitHub 首次发布清单

当前本地仓库尚未配置 GitHub remote。首次公开发布前按本清单执行，避免把“能 push”误认为“已经具备开源维护条件”。

## 1. 本地预检

```powershell
# 本地准备阶段允许没有 remote；正式发布前去掉该参数
.\.venv\Scripts\python.exe tools\check_release_readiness.py --allow-missing-remote
```

预检覆盖：必需开源文件、工作树状态、remote、分支、禁止提交的模型/密钥/数据库文件、GitHub 100MB 文件限制、Python 依赖锁定、Action SHA 固定、Vision 数据集来源元数据和完整 Git 历史秘密扫描。

## 2. 登录和创建仓库

GitHub CLI 已下载到项目 `.tools/github-cli-2.93.0`，其压缩包已按官方 SHA-256 校验。登录需要由仓库所有者亲自完成：

```powershell
.\.tools\github-cli-2.93.0\bin\gh.exe auth login
```

确认 GitHub 所有者、仓库名和公开可见性后，再创建并推送。推荐仓库名 `shipcad-review`：

```powershell
.\.tools\github-cli-2.93.0\bin\gh.exe repo create OWNER/shipcad-review `
  --public `
  --source . `
  --remote origin `
  --push
```

不要在未确认所有者和可见性时运行创建命令。

## 3. 首次云端验收

等待下列工作流真实运行：

- CI：Python workers、Spring backend、Vue frontend、Live API E2E
- Dependency Review
- CodeQL：Java、JavaScript/TypeScript、Python
- Secret Scan
- SBOM：后端、前端、CAD/Vision/OCR Worker

GitHub 托管环境全部通过后，才能把对应检查加入 required status checks。检查在首次运行前不会出现在规则集候选列表中。

## 4. 仓库设置

- 开启 Issues 和 private vulnerability reporting。
- 确认公共仓库 Secret scanning 与 push protection 状态。
- 开启 Dependency graph、Dependabot alerts 和 Dependabot security updates。
- 为 `main` 或 `master` 建立 active ruleset：
  - 禁止删除和强制推送。
  - 要求 pull request。
  - 要求代码对话解决。
  - 要求 CI、Dependency Review、CodeQL 和 Secret Scan 通过。
- 添加 topics：`cad`、`dxf`、`ship-design`、`computer-vision`、`yolov8`、`spring-boot`、`vue`。

## 5. 发布后复核

```powershell
.\.venv\Scripts\python.exe tools\check_release_readiness.py
git status --short
git remote -v
```

检查 README、许可证、SBOM 工件、安全策略和 Issue 模板在 GitHub 页面中显示正常。公开后不要上传客户图纸、私有数据集或模型权重。
