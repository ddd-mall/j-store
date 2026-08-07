# 仓库版权与许可证治理设计

## 权属模型

仓库原创文件默认归 `潘少峰 (Peter Pan)` 所有，并按 Apache-2.0 授权。`config/licenses/file-ownership.toml` 使用默认规则覆盖原创文件，用显式 override 标注第三方文件；当前已知仓库内第三方交付物是 Gradle Wrapper。

Java/Kotlin 文件直接携带 SPDX 标识和 Apache-2.0 标准声明。其他原创配置、文档和资源由根 `LICENSE`、README 和文件归属清单共同覆盖，避免给 JSON、二进制或迁移脚本强行插入可能破坏格式的注释。

## 发布产物

根 Gradle 构建对所有 `Jar` 任务增加 `META-INF/LICENSE`。`verifyLicenseArtifacts` 依赖所有 JAR 任务，并逐个读取 ZIP 条目，校验内容与根许可证逐字一致；因此普通模块 JAR 与 Spring Boot JAR 使用同一约束。

## 依赖许可证

Licensee 1.14.1 应用于所有 JVM 子项目。允许列表只包含经确认可用于当前 Apache-2.0 项目的许可证；缺失或错误元数据必须使用精确到坐标和版本的例外，并记录理由，不允许按组织或整个依赖树静默忽略。

每个模块在 `build/reports/licensee/` 输出 `artifacts.json` 与 `validation.txt`。本地质量门禁和 GitHub `dependency-license-audit` job 均运行所有 `licensee` 任务。

## 发布证据

`scripts/create-release-evidence.sh` 只接受指向当前 `HEAD` 的标签，并要求工作区没有受版本控制的改动。脚本执行完整质量门禁，生成 boot JAR、CycloneDX SBOM、Licensee 报告和源码归档，复制根许可证与归属清单，记录 Git/工具链元数据，最后对证据包内文件生成 SHA-256 清单。

标签工作流只生成和上传候选证据，不创建 Release、不部署、不迁移数据库。GitHub Artifact Attestation 为 JAR 和源码归档增加可验证的构建来源；正式发布仍由人工决定。

## GitHub 保护

`.github/rulesets/master.json` 是可审计的期望配置，并通过 GitHub Rulesets API 应用。规则要求 PR、解决 review thread、禁止删除和非快进推送，并要求以下 check context：

- `quality`
- `static-analysis`
- `dependency-vulnerability-scan`
- `dependency-license-audit`
- `secret-scan`

当前仓库只有一位明确所有者，因此规则要求 PR 但不要求作者无法自行满足的审批数量；人工合并仍是必需动作。

## 恢复与例外

- Licensee 误判时只能增加带理由的精确例外，不能关闭整个审计。
- GitHub Actions 故障时不得绕过 required checks；先修复工作流或由管理员临时调整规则并留下审计记录。
- 证据脚本失败时不产生正式发布；修复后从同一提交重新生成。
- 根许可证或所有者变化属于法律授权变化，必须单独批准并重新审计所有声明和产物。
