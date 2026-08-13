# 依赖管理治理设计

## 职责划分

- `gradle/libs.versions.toml` 保存外部库、项目插件和工具版本的类型安全名称。
- `j-store-dependencies-platform` 使用 `java-platform` 导入 Spring Boot BOM、JUnit BOM 和 OpenTelemetry BOM，负责影响所有 JVM 模块的解析约束。
- 根构建脚本把统一 Platform 加到每个 JVM 子项目的主代码与测试配置；Platform 模块本身除外。
- 模块构建脚本保留 `api`、`implementation`、`runtimeOnly` 等语义和所有 `project()` 依赖。

## 版本决策

- Spring Boot BOM 是 Spring Framework、Spring Security、Jackson、Netty、SLF4J、PostgreSQL、Micrometer、H2、Mockito 与 Lombok 等已管理组件的默认兼容矩阵。
- JUnit 继续使用现有 `6.1.2`，通过 JUnit BOM 对齐 API、Engine 和 Platform Launcher；这属于本次不升级版本的兼容性保持。
- OpenTelemetry 继续使用现有 `1.62.0` BOM，因为可观测性规格和回归测试将其定义为安全下限。
- 其余 Boot 未管理的库继续由 catalog 中的显式版本管理。

## 可执行治理

`tests/governance/test_dependency_management.py` 扫描所有项目构建脚本和 catalog，阻止直接外部 GAV、项目插件硬编码版本、未使用 alias、统一 Platform 缺失及关键版本策略回退。

Settings 插件不能通过项目 Version Catalog alias 使用，因此 Foojay resolver 继续在 `settings.gradle.kts` 中声明。Spotless 所使用的 Google Java Format 与 ktfmt 版本作为构建工具版本进入 catalog。

## 风险与恢复

统一 Platform 会改变此前被显式 BOM 覆盖的组件解析结果。重点通过 `dependencyInsight` 核对 Spring Security、Jackson、Netty、SLF4J 与 OpenTelemetry，并运行全量测试、许可证审计和制品验证。若发现兼容问题，整体 revert 本变更即可恢复原声明和解析图，不涉及数据迁移。
