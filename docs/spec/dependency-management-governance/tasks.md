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
- Platform：新增 `j-store-dependencies-platform`，导入 Spring Boot 3.5.16、JUnit 6.1.2、OpenTelemetry 1.62.0、Jackson 2.21.5 和 Netty 4.1.136.Final BOM，并集中约束 PostgreSQL 42.7.12 与 Commons Lang 3.19.0；根构建为主代码、测试和 `java-test-fixtures` 配置统一注入约束。
- 解析证据：Spring Security Crypto 继续跟随 Spring Boot 解析为 6.5.11；Jackson Databind 解析为 2.21.5、Netty Codec 4.1.136.Final、PostgreSQL JDBC 42.7.12、Commons Lang 3.19.0；OpenTelemetry API 保持安全基线 1.62.0。
- 聚焦验证：27 项治理测试通过；`:j-store-common-core:test :j-store-boot:test :j-store-outbox-spring:test` 通过；订单 `testFixturesClasses` 单独验证通过。
- 完整门禁：rebase 到最新 `origin/develop` 后，`./scripts/quality-gate.sh` 全部 6 阶段通过，包括 28 项 spec-dev、36 项治理契约、63 项工具测试、1249 个文件归属检查、Spotless、50 个 JVM 模块许可证审计、全量 Gradle 测试和 53 个 JAR 制品许可证验证。
- 供应链验证：PR 首次运行的 OSV Scanner 2.4.0 发现 Jackson、Netty、PostgreSQL JDBC 和 Commons Lang 共 6 项漏洞，证明移除治理前安全覆盖会造成回退；恢复集中安全下限后，`:j-store-boot:cyclonedxDirectBom` 生成包含 199 个包的生产 SBOM，经校验发布 SHA-256 的 OSV Scanner 2.4.0 扫描，退出码为 0，发现 0 个漏洞。
- 工具差异：Homebrew OSV Scanner 2.5.0 曾对存在漏洞的同一 CI SBOM 返回 0，不能作为本次验收证据；最终验收固定使用与 workflow 一致的 2.4.0。
- 门禁可移植性：首次完整门禁发现系统 Python 3.9 缺少 `tomllib`；`quality-gate.sh` 已让文件归属检查复用声明依赖的 uv 环境，并由许可证治理契约守护。
