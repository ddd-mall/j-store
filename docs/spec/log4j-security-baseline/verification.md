# Log4j 2.25.5 升级验证记录

## 结论

升级满足验收标准。Log4j 家族由统一 Platform 对齐为 2.25.5，漏洞触发行为、日志桥接、
应用上下文、发布制品、许可证、生产 SBOM、漏洞扫描和全仓回归均通过。

## 验证证据

- TDD RED：升级前治理契约因缺少 `log4j` 版本和 BOM 失败；行为测试夹具编译完成后由
  2.25.5 实现满足非有限浮点 JSON 断言。
- `dependencyInsight`：`log4j-api` 和 `log4j-to-slf4j` 均解析为 2.25.5，选择来源包含
  `org.apache.logging.log4j:log4j-bom:2.25.5`；Spring Boot 3.5.16 请求的 2.24.3 被覆盖。
- 行为兼容：`MapMessage` 对有限值保持 JSON 数字，对 `NaN` 与正负无穷输出可严格解析的
  JSON 字符串；Log4j API 工厂仍为 `SLF4JLoggerContextFactory`。
- Boot 与制品：可观测性 `ApplicationContextRunner` 测试、`j-store-boot` 测试、
  `bootJar` 和生产 CycloneDX SBOM 生成均通过。
- SBOM：`log4j-bom`、`log4j-api`、`log4j-to-slf4j` 均为 2.25.5，无 2.24.3 组件。
- 漏洞扫描：校验 SHA-256 后运行与 CI 相同的 OSV Scanner 2.4.0，扫描 200 个生产包，
  结果为 0 个漏洞。
- 全量门禁：`./scripts/quality-gate.sh` 六阶段全部通过，包括 135 项 Python 契约测试、
  Spotless、50 个模块许可证审计、全仓 Gradle 回归与 53 个发布 JAR 许可证验证。

## 回滚与残余风险

可整体 revert 本独立变更，但回滚会恢复已知漏洞。当前没有观察到二进制、启动、桥接、
许可证或供应链回归。剩余风险是本地环境为 macOS，而 CI 使用 Linux；通过使用同一 SBOM
生成任务和同版 OSV Scanner 缩小差异，最终仍以远端 required checks 为准。
