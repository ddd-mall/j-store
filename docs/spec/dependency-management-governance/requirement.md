# 依赖管理治理需求

## 目标

j-store 需要为 50 个 Gradle 模块建立单一、可审计的外部依赖声明入口，并把“便于声明的坐标目录”与“影响解析结果的版本平台”分开治理，避免模块脚本散落 GAV、无依据覆盖 Spring Boot BOM，以及 catalog 长期积累未使用候选。

## 验收标准

- DMG-R1：所有项目构建脚本必须通过 `libs.versions.toml` 引用外部库和可用于项目脚本的外部插件，不得直接声明外部 GAV 或项目插件版本。
- DMG-R2：仓库必须提供统一 Java Platform，集中导入 Spring Boot、JUnit 和经安全证据批准的 OpenTelemetry BOM；各 JVM 模块通过该 Platform 获得一致约束。
- DMG-R3：OpenTelemetry 运行时必须保持 `1.62.0` 或更高，以继续满足 `GHSA-rcgg-9c38-7xpx` 回归契约。
- DMG-R4：Jackson、Netty、SLF4J、PostgreSQL 和 Spring Security 等由 Spring Boot 管理的组件不得在 catalog 中无依据覆盖 Boot BOM。
- DMG-R5：catalog 不得保留当前构建未使用的 library/plugin alias；未来能力在实际启用时随兼容性评估加入。
- DMG-R6：项目模块依赖、Kotlin 插件依赖辅助方法、Gradle 内建插件和依赖作用域继续在各模块脚本中表达。
- DMG-R7：Settings 插件遵循 Gradle 能力边界，可以继续在 `settings.gradle.kts` 中声明版本。
- DMG-R8：治理契约必须由自动化测试保护，依赖解析、许可证、全量测试和制品验证均通过。

## 非目标

- 本次不升级业务库、Spring Boot、Kotlin、JUnit、OpenTelemetry 或 Gradle 版本。
- 本次不引入自动依赖升级 PR，不修改 DDD 模块依赖方向，也不改变业务运行行为。
- 本次不把 `project(":...")` 依赖隐藏到 catalog 或约定插件中。

## 质量目标

- **一致性**：同一外部坐标只有一个声明入口，解析约束由统一 Platform 提供。
- **安全性**：保留有漏洞修复证据的 OpenTelemetry 覆盖，移除无批准依据的高风险覆盖。
- **可维护性**：删除未使用 alias、修复命名错误，并用测试阻止回归。
- **可恢复性**：变更只涉及构建元数据，可通过整体 revert 恢复原依赖图。
