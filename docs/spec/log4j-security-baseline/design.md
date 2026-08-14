# Log4j 2.25.5 升级设计

## 决策

- 选择 2.25.5：这是包含目标漏洞修复的最小补丁版本，相比 2.26.1 变更面更小。
- 在 `gradle/libs.versions.toml` 声明 Log4j BOM 坐标，由
  `j-store-dependencies-platform` 导入；消费模块不声明版本、不使用 `force`。
- 使用 Log4j BOM 对齐家族，而不是只约束 `log4j-api`，避免 API、适配器和可选组件混装。
- 保持 Spring Boot 默认 Logback 实现；Log4j API 继续通过 `log4j-to-slf4j` 路由。
- 由根 Gradle 任务 `verifyDependencyResolution` 遍历所有生产 `runtimeClasspath`，校验实际
  解析的 Log4j 家族版本，并由 `quality-gate.sh` 强制执行；catalog 文本断言只负责声明治理。

## 验证矩阵

| 风险 | 验证 |
|---|---|
| 版本未覆盖或混装 | `verifyDependencyResolution`、`dependencyInsight`、生产 SBOM |
| 漏洞行为仍存在 | `MapMessage` 非有限浮点 JSON 回归测试 |
| Boot/桥接二进制不兼容 | `j-store-boot` 测试、上下文测试、`bootJar` |
| 新漏洞或许可证变化 | OSV Scanner 2.4.0、`licensee` |
| 版本策略将来回退 | Python 契约守护声明与门禁接线，Gradle 任务守护最终解析结果 |
| 跨模块回归 | `./scripts/quality-gate.sh` |
