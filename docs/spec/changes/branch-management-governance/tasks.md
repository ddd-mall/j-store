# 分支管理治理任务

- [x] T1：为允许和拒绝的分支方向、命名及 PR 标题编写失败测试。
- [x] T2：实现分支策略检查器和 GitHub Actions 工作流。
- [x] T3：统一质量、安全和 Qodana 的目标分支，并禁用定时依赖升级 PR。
- [x] T4：补充 `master`、`develop` ruleset 期望配置与治理契约检查。
- [x] T5：编写完整分支管理运行手册并更新仓库索引和 PR 模板。
- [x] T6：运行 tooling 测试、治理检查和适用质量门禁，记录验证证据。
- [x] T7：验证首次治理 bootstrap PR 可以进入 `master`，且策略落地后普通 `codex/* -> master` 仍被拒绝。

## 验证证据

- 分支策略按 TDD 实施：目标脚本缺失、宽松 SemVer、Dependabot 分支拒绝和一次性 bootstrap 用例均先确认失败，随后 13 个分支策略场景和全部 tooling 测试通过。
- spec-dev 契约 28 项、治理契约 11 项全部通过；`scripts/check-agent-governance.sh` 通过。
- `scripts/check-file-ownership.py` 通过，961 个仓库文件完成归属分类。
- workflow YAML 和 ruleset JSON 解析通过，`git diff --check` 通过。
- Windows JDK 25 执行 `spotlessCheck licensee test verifyLicenseArtifacts` 构建成功：117 个任务通过，22 个 JVM 模块完成依赖许可证审计，24 个 JAR 制品许可证验证通过。
