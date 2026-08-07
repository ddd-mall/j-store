# 仓库版权与许可证治理交付摘要

## 已交付能力

- 根目录使用 Apache-2.0 完整许可证，README 明确版权所有者为 `潘少峰 (Peter Pan)`，全部 Java/Kotlin 源码携带 SPDX 与标准许可证声明，并由 Spotless 持续检查。
- 所有 Gradle `Jar` 任务写入根 `LICENSE` 和 `THIRD_PARTY.md`；`verifyLicenseArtifacts` 对实际 ZIP 条目及内容进行确定性校验。
- 所有 JVM 子项目启用 Licensee，未知、缺失或未批准的直接及传递依赖许可证会使本地质量门禁和 GitHub Security Gate 失败。
- `config/licenses/file-ownership.toml` 将原创文件默认归属潘少峰，并将 Gradle Wrapper 明确分类为第三方文件；审计脚本输出机器可读报告。
- 标签或人工触发的 Release Evidence workflow 生成源码归档、JAR、SBOM、依赖许可证报告、归属报告、Git/工具链元数据、质量日志、SHA-256 清单和 GitHub provenance attestation，但不自动发布。
- `master` ruleset 的受版本控制配置要求 PR、required checks、review thread 解决，并禁止删除及非快进推送。

## 本地验收证据

- `./scripts/quality-gate.sh`：PASS。治理检查、规格测试、源码归属、Spotless、Licensee、全量 Gradle 测试和发布产物许可证校验全部通过。
- 治理测试：11 项通过；spec-dev 合同测试：28 项通过。
- 文件归属审计：952 个仓库文件全部分类，其中 4 个 Gradle Wrapper 文件分类为第三方。
- 依赖许可证审计：22 个 JVM 子项目全部通过。
- 发布产物校验：24 个普通或 Spring Boot JAR 均包含与根文件一致的 `META-INF/LICENSE` 和 `META-INF/THIRD_PARTY.md`。
- `git diff --check`、Python 编译、Shell 语法和 ruleset JSON 语法检查通过。

## GitHub 交付证据

- 候选已提交并推送到 `chore/license-governance`，PR 为 `https://github.com/ddd-mall/j-store/pull/9`。
- GitHub ruleset `20543677` 处于 active；分支规则解析接口确认 `master` 实际应用 PR、删除、非快进推送和 required status checks 规则。
- PR #9 的 `quality`、`static-analysis`、`dependency-vulnerability-scan`、`dependency-license-audit` 和 `secret-scan` 全部通过；额外的 CodeQL 与 Qodana 检查也通过。
- 首轮 `quality` 暴露默认浅克隆缺失 `origin/master` 的问题；新增回归合同并让 Quality Gate 使用完整 checkout 后，远端完整门禁在 run `31159485411` 中通过。

## 交付边界与剩余验证

- 当前候选位于独立分支 `chore/license-governance`，尚未合并；不创建标签、不创建 GitHub Release、不部署生产环境。
- 本机未安装 `actionlint`。PR 触发的 workflow 已由 GitHub runner 验证；仅由标签或人工触发的 Release Evidence workflow 未实际运行，因为本次不创建发布标签。
- PR #9 暂时堆叠在 PR #8 的 Kotlin 2.4.10 提交上；应先处理 PR #8，或在其合并后确认 PR #9 的 diff 再人工合并。
- 正式发布仍需人工确认签名标签、兼容性、迁移、配置、密钥、生产授权和回滚条件。
