# 依赖管理治理设计

## 职责划分

- `gradle/libs.versions.toml` 保存外部库、项目插件和工具版本的类型安全名称。
- `j-store-dependencies-platform` 使用 `java-platform` 导入 Spring Boot、JUnit、OpenTelemetry、Jackson 和 Netty BOM，并声明 PostgreSQL 与 Commons Lang 约束，负责影响所有 JVM 模块的解析约束。
- 根构建脚本把统一 Platform 加到每个 JVM 子项目的主代码与测试配置；Platform 模块本身除外。
- 模块构建脚本保留 `api`、`implementation`、`runtimeOnly` 等语义和所有 `project()` 依赖。

## 版本决策

- Spring Boot BOM 是 Spring Framework、Spring Security、SLF4J、Micrometer、H2、Mockito 与 Lombok 等已管理组件的默认兼容矩阵。
- JUnit 继续使用现有 `6.1.2`，通过 JUnit BOM 对齐 API、Engine 和 Platform Launcher；这属于本次不升级版本的兼容性保持。
- OpenTelemetry 继续使用现有 `1.62.0` BOM，因为可观测性规格和回归测试将其定义为安全下限。
- Jackson 使用 `2.21.5` BOM，规避 `GHSA-5gvw-p9qm-jgwh`、`GHSA-5jmj-h7xm-6q6v` 和 `GHSA-mhm7-754m-9p8w`。
- Netty 使用 `4.1.136.Final` BOM，规避 `GHSA-558v-64gr-wgg4`。
- PostgreSQL JDBC 使用 `42.7.12` 约束，规避 `GHSA-j92g-9f8w-j867`；Commons Lang 保持治理前的 `3.19.0`，高于 `GHSA-j288-q9x7-2f5v` 所需的 `3.18.0` 修复版本。
- 上述安全版本均不高于本分支治理前 catalog 已声明的版本。没有安全证据的 Boot 管理组件不单独覆盖。
- 其余 Boot 未管理的库继续由 catalog 中的显式版本管理。

## 可执行治理

`tests/governance/test_dependency_management.py` 扫描所有项目构建脚本和 catalog，阻止直接外部 GAV、项目插件硬编码版本、未使用 alias、统一 Platform 缺失及关键版本策略回退。

Settings 插件不能通过项目 Version Catalog alias 使用，因此 Foojay resolver 继续在 `settings.gradle.kts` 中声明。Spotless 所使用的 Google Java Format 与 ktfmt 版本作为构建工具版本进入 catalog。

## 风险与恢复

统一 Platform 会改变约束的所有权，但安全下限保持治理前已批准的版本。重点通过 `dependencyInsight` 核对 Spring Security、Jackson、Netty、PostgreSQL、Commons Lang 与 OpenTelemetry，并运行全量测试、许可证审计、制品验证及 CI 同版本 OSV 扫描。若发现兼容问题，整体 revert 本变更即可恢复原声明和解析图，不涉及数据迁移。
