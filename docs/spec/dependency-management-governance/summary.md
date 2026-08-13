# 依赖管理治理交付摘要

## 完成结果

- 所有项目构建脚本的外部库声明已统一使用 `libs.versions.toml`，项目脚本不再直接书写外部 GAV 或外部插件版本。
- 新增 `j-store-dependencies-platform`，把坐标目录与实际版本对齐职责分离，并覆盖主代码、测试和测试夹具 classpath。
- Spring Security、Jackson、Netty、SLF4J 等无批准依据的覆盖已移除并回归 Spring Boot 兼容矩阵；有漏洞修复证据的 OpenTelemetry 1.62.0 安全覆盖被保留。
- 清理 26 个未使用 alias，补齐实际使用坐标，修复 catalog 命名错误，并集中 Spotless 格式化工具版本。
- 新增 5 项可执行治理契约，阻止散落坐标、插件硬编码、无效 alias、Platform 缺失和关键策略回退。

## 验收映射

| 需求 | 主要证据 |
|---|---|
| DMG-R1、DMG-R5 | 依赖治理扫描测试、清理后的 catalog、所有模块 alias 迁移 |
| DMG-R2、DMG-R6 | `j-store-dependencies-platform`、根构建统一注入、模块 `project()` 与作用域保留 |
| DMG-R3、DMG-R4 | catalog 策略测试、`dependencyInsight` 实际解析结果、既有 OpenTelemetry 安全回归测试 |
| DMG-R7 | `settings.gradle.kts` 保留 Foojay Settings 插件声明 |
| DMG-R8 | 完整质量门禁、生产 SBOM、50 模块许可证审计与 53 个 JAR 验证 |

## 兼容性与恢复

本变更不升级既有版本、不改变业务代码或数据结构。解析图按受支持的 Spring Boot BOM 收敛，同时保留既有 JUnit 和 OpenTelemetry 版本。若候选在 CI 漏洞扫描或独立评审中出现问题，可整体 revert 本变更恢复原依赖图，无需数据迁移。

## 残余门禁

本地已使用 OSV Scanner 2.5.0 扫描生产 SBOM 的 198 个包，未发现漏洞。远端 required check 仍需使用其固定工具版本独立复核；依赖解析策略属于供应链范围，合并前仍需经过独立评审与既有 required checks。
