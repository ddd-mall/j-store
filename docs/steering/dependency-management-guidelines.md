---
inclusion: fileMatch
fileMatchPattern: ['**/*.gradle.kts', 'gradle/libs.versions.toml', '.github/workflows/*.yml']
---

# 依赖管理规范 — j-store

本规范约束仓库内 Gradle 外部依赖的声明、版本解析、安全例外和验证。依赖治理的目标是让坐标只有一个可审计入口、解析策略只有一个统一所有者，并让安全修复能够被持续验证。

## 权威来源与职责

- `gradle/libs.versions.toml` 是外部库、项目插件和构建工具版本的统一坐标目录；它负责类型安全名称，不单独保证最终解析版本。
- `j-store-dependencies-platform` 是 JVM 依赖版本对齐的统一所有者；Spring Boot BOM、其它获批 BOM 和单组件约束必须从这里进入模块 classpath。
- 各模块 `build.gradle.kts` 只表达依赖作用域和模块关系，例如 `api`、`implementation`、`runtimeOnly`、`testImplementation` 与 `project()`，不得自行建立第二套版本策略。
- 当前获批的版本与安全基线以 catalog、统一 Platform、可执行治理测试及适用的 `docs/spec/` 为准；说明文档与解析结果冲突时，以 Gradle 实际解析结果和测试证据为当前事实，并修正文档漂移。

## 声明规则

1. 项目构建脚本中的外部库必须通过 version catalog alias 引用，不得直接书写 `group:artifact:version` 或无版本 GAV。
2. 可由项目 version catalog 管理的外部插件必须使用 `libs.plugins.*`；不得在项目构建脚本中硬编码插件版本。
3. Gradle 内建插件、`project()` 模块依赖、依赖作用域以及 Kotlin DSL 辅助方法不进入 catalog。
4. Settings 插件受 Gradle 能力边界限制，可以在 `settings.gradle.kts` 中显式声明版本；该例外不得扩展到普通项目插件。
5. catalog 只保留当前构建实际使用的 library/plugin alias。候选依赖在真正启用并完成兼容性评估时加入，不得把 catalog 当作未来依赖清单。
6. 不得使用动态版本、版本区间、`latest.*`、未批准的 snapshot/RC/milestone，或在模块中通过 `resolutionStrategy`、`force`、额外 `enforcedPlatform` 绕过统一 Platform。

## 版本与安全策略

- Spring Boot BOM 是其生态组件的默认兼容矩阵。Boot 已管理组件不得仅因“版本更新”而单独覆盖。
- 安全公告、已验证兼容性缺陷或明确产品约束可以形成例外。例外必须集中在 `j-store-dependencies-platform`，并同时记录依据、影响范围、回滚方式和自动化回归契约。
- 家族组件需要整体对齐时使用获批 BOM，例如 Jackson、Netty；单一组件使用 Platform constraint。不得在消费模块逐个钉版本。
- 新增或调整安全下限时，必须记录公告标识或等价证据，选择已修复且与当前 Spring Boot/Kotlin/Java 基线兼容的最小可接受版本。
- 普通依赖升级遵循一个依赖或一个不可拆分 BOM 一个 PR；major、RC、milestone 及高风险升级必须有独立规格和兼容性评估。不得启用自动依赖升级 PR。

## 变更流程

1. 先扫描 catalog、统一 Platform、模块脚本和传递依赖来源，区分“坐标声明变更”与“解析版本变更”。
2. 解析策略变化必须先更新 `tests/governance/test_dependency_management.py` 或等价契约，并确认测试能识别缺失策略或版本回退。
3. 修改后使用 `dependencyInsight` 核对受影响组件的最终版本、选择原因和约束来源；不能只检查 `libs.versions.toml` 文本。
4. 生成生产依赖 SBOM：

   ```bash
   ./gradlew :j-store-boot:cyclonedxDirectBom --no-daemon
   ```

5. 使用与 `.github/workflows/security.yml` 固定版本一致且校验过发布摘要的 OSV Scanner 扫描 SBOM。不同扫描器版本结果冲突时，以 CI 固定版本和上游漏洞记录复核，不得用较新的零结果直接判定安全。
6. 运行受影响模块测试，并在依赖治理或跨模块变更交付前运行：

   ```bash
   ./scripts/quality-gate.sh
   ```

7. PR 必须说明解析前后版本、BOM/constraint 所有权、兼容性证据、漏洞与许可证结果、回滚方式及未验证风险；required checks 未通过不得合并。

## 完成标准

- 外部坐标和项目插件没有散落硬编码，catalog 无未使用 alias。
- 所有 JVM 模块通过 `j-store-dependencies-platform` 获得一致约束，没有模块级旁路。
- `dependencyInsight` 结果符合批准的 Boot 兼容矩阵和安全下限。
- 治理契约、许可证审计、生产 SBOM、CI 同版本 OSV Scanner 和 `./scripts/quality-gate.sh` 均通过；任何跳过或工具差异已明确记录。

## OCI 镜像供应链

- 应用基础镜像必须使用可读 tag 加 OCI image-index digest，例如
  `name:version@sha256:digest`；正式 Kubernetes 清单只部署完整应用镜像 digest。
- 一个 Git 提交只构建一次 OCI 候选。环境晋级复制同一 manifest/index，不得按环境重建或
  使用 `latest`、浮动 tag、PVC JAR 代替正式制品。
- BuildKit 候选必须生成 SBOM 和 provenance；required 安全门禁同时扫描 Gradle 解析依赖与
  最终容器的 OS/应用包。生产晋级还必须验证签名或等价 attestation。
- registry mirror 是网络配置，不是新的制品版本。镜像复制前后 digest 不一致时必须停止，
  不能仅凭 tag 相同继续部署。
- 基础镜像升级属于显式供应链变更，必须记录原/新 digest、上游版本、架构 manifest、漏洞和
  许可证结果、兼容性验证与回滚 digest；不得由定时任务自动合并。
