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
- 治理测试：10 项通过；spec-dev 合同测试：28 项通过。
- 文件归属审计：951 个仓库文件全部分类，其中 4 个 Gradle Wrapper 文件分类为第三方。
- 依赖许可证审计：22 个 JVM 子项目全部通过。
- 发布产物校验：24 个普通或 Spring Boot JAR 均包含与根文件一致的 `META-INF/LICENSE` 和 `META-INF/THIRD_PARTY.md`。
- `git diff --check`、Python 编译、Shell 语法和 ruleset JSON 语法检查通过。

## 交付边界与剩余验证

- 当前候选位于独立分支 `chore/license-governance`，不自动合并、不创建标签、不创建 GitHub Release、不部署生产环境。
- 本机未安装 `actionlint`；GitHub Actions workflow 的平台语义将由 PR checks 验证。
- GitHub 分支推送、PR、ruleset 应用和远端状态回读在提交后执行，并在本摘要中补充最终证据。
- 正式发布仍需人工确认签名标签、兼容性、迁移、配置、密钥、生产授权和回滚条件。
