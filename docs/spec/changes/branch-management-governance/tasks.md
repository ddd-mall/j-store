# 分支管理治理任务

- [x] T1：为允许和拒绝的分支方向、命名及 PR 标题编写失败测试。
- [x] T2：实现分支策略检查器和 GitHub Actions 工作流。
- [x] T3：统一质量、安全、Qodana 和 Dependabot 的目标分支。
- [x] T4：补充 `master`、`develop` ruleset 期望配置与治理契约检查。
- [x] T5：编写完整分支管理运行手册并更新仓库索引和 PR 模板。
- [x] T6：运行 tooling 测试、治理检查和适用质量门禁，记录验证证据。

## 验证证据

- 分支策略按 TDD 实施：目标脚本缺失及宽松 SemVer 用例均先确认失败，随后 12 个分支策略场景全部通过；tooling 共 18 项通过。
- spec-dev 契约 28 项、治理契约 20 项全部通过；`scripts/check-agent-governance.sh` 通过。
- `scripts/check-file-ownership.py` 通过，1176 个仓库文件完成归属分类。
- workflow YAML 和 ruleset JSON 解析通过，`git diff --check` 通过。
- Ubuntu/WSL 使用 `/usr/share/java/jdk-25.0.1`：`spotlessCheck licensee` 构建成功，50 个 JVM 模块的许可证任务通过。
- Ubuntu/WSL：`test verifyLicenseArtifacts` 构建成功，193 个任务完成或复用，53 个 JAR 制品许可证验证通过。
- 统一 `scripts/quality-gate.sh` 已运行并推进到全仓测试，但首次工具等待在 30 分钟上限处结束；底层进程继续完成。为取得确定性退出码，随后分别复核全部阶段并全部通过。
