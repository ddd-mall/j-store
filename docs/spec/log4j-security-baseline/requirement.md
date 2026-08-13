# Log4j 安全基线升级需求

## 背景与目标

Spring Boot 3.5.16 BOM 当前将 Log4j 家族解析为 2.24.3。OSV Scanner 2.4.0 因
`CVE-2026-49844 / GHSA-qv9r-c865-cp47` 阻断构建：Log4j API 在将非有限浮点数编码为
`MapMessage` JSON 时可能产生不符合 RFC 8259 的输出。

本变更必须以最小安全补丁版本 2.25.5 覆盖 Boot 默认值，并证明升级没有破坏 Spring Boot
3.5.16、Java 25、SLF4J/Logback 桥接或发布产物。

## 范围

- 在统一依赖 Platform 中使用 Log4j BOM 对齐整个 Log4j 组件家族到 2.25.5。
- 建立安全版本防回退契约和漏洞触发行为回归测试。
- 验证运行时依赖树、应用上下文、日志桥接、生产 SBOM、OSV 和许可证。

不升级 Spring Boot，不切换默认日志实现，不引入 Log4j Core，也不改变业务日志格式。

## 验收标准

1. `j-store-boot` 生产运行时中的全部 `org.apache.logging.log4j` 组件必须解析为 2.25.5，且
   选择来源为 `j-store-dependencies-platform`，不得混装 2.24.3。
2. `MapMessage` 包含 `NaN`、正无穷或负无穷时，JSON 输出必须能被 Jackson 严格解析；普通
   有限浮点值行为保持正常。
3. Spring Boot 应用上下文、现有 Logback/SLF4J 桥接、全仓测试和发布 JAR 必须通过验证，
   不得出现链接错误或日志桥接循环。
4. CI 同版 OSV Scanner 2.4.0 扫描生产 SBOM时不得再报告
   `GHSA-qv9r-c865-cp47`，也不得新增其它已知漏洞。
5. 许可证审计、依赖治理契约和全仓质量门禁必须通过。

## 回滚与风险

本变更作为独立依赖 PR 交付，可整体 revert。回滚会恢复已知漏洞，只能用于紧急运行故障，
随后必须采用其它已修复版本恢复安全基线。主要兼容性风险是 Log4j API 与
`log4j-to-slf4j` 混装，以及桥接启动时的二进制链接错误；通过 BOM 对齐、依赖解析契约和
应用启动测试控制。
