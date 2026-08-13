# 依赖管理治理任务

- [x] DMG-T1：新增治理契约测试，并在当前散落声明状态确认失败。
- [x] DMG-T2：清理并规范 `libs.versions.toml`，补齐所有实际使用的外部库、项目插件和构建工具版本。
- [x] DMG-T3：新增统一依赖 Platform，并让所有 JVM 子项目的主代码、测试与测试夹具配置使用它。
- [x] DMG-T4：将所有模块直接外部坐标替换为 catalog alias，保留模块依赖与作用域语义。
- [x] DMG-T5：验证关键依赖解析结果、OpenTelemetry 安全下限、全量测试、许可证和发布制品。
- [x] DMG-T6：记录验收映射、执行证据、偏差与残余风险。

## 实施证据（2026-08-13）

- TDD 红灯：新增的 5 项依赖治理契约在实施前全部失败，分别识别 44 处直接外部坐标、根构建插件硬编码版本、26 个未使用 alias、统一 Platform 缺失和未批准版本覆盖；实施后全部通过。
- Catalog：删除 Spring Modulith、Spring Cloud/Alibaba、Redisson、OpenAI 与 DashScope 等 26 个未使用 alias；补齐 Spring、Micrometer、H2、JUnit 与 embedded-postgres 等实际坐标；修复 `spirng-boot-boot` 拼写。
- Platform：新增 `j-store-dependencies-platform`，导入 Spring Boot 3.5.16、JUnit 6.1.2 和 OpenTelemetry 1.62.0 BOM；根构建为主代码、测试和 `java-test-fixtures` 配置统一注入约束。
- 解析证据：Spring Security Crypto 解析为 6.5.11、Jackson Databind 2.21.4、Netty Common 4.1.135.Final、SLF4J API 2.0.18，均跟随 Spring Boot；JUnit Jupiter API 保持 6.1.2；OpenTelemetry API 保持安全基线 1.62.0。
- 聚焦验证：27 项治理测试通过；`:j-store-common-core:test :j-store-boot:test :j-store-outbox-spring:test` 通过；订单 `testFixturesClasses` 单独验证通过。
- 完整门禁：`./scripts/quality-gate.sh` 全部 6 阶段通过，包括 28 项 spec-dev、27 项治理契约、36 项工具测试、1225 个文件归属检查、Spotless、50 个 JVM 模块许可证审计、全量 Gradle 测试和 53 个 JAR 制品许可证验证。
- 供应链验证：`:j-store-boot:cyclonedxDirectBom` 成功生成生产依赖 SBOM；本地 Homebrew OSV Scanner 2.5.0 扫描 198 个包，退出码为 0，发现 0 个漏洞。GitHub `dependency-vulnerability-scan` required check 继续使用其固定版本独立复核。
- 门禁可移植性：首次完整门禁发现系统 Python 3.9 缺少 `tomllib`；`quality-gate.sh` 已让文件归属检查复用声明依赖的 uv 环境，并由许可证治理契约守护。
