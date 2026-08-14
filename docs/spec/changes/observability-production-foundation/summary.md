# 生产可观测性应用闭环交付摘要

## 交付结果

本切片关闭了生产计划迭代 1 的应用侧配置、健康、告警和模块复用缺口：

1. 根应用不再默认激活 `local`；新增显式 `production` profile，并自动组合 `observability`。production 的连接地址、用户名、密码、JWT 和手机号验证 secret 必须由环境提供，Hikari identity 统一为 `j-store`。
2. `j-store-outbox-spring` 条件注册 `outbox` HealthIndicator。状态映射为 `UP/DEGRADED/DOWN/UNKNOWN`，details 仅包含低敏运行摘要；liveness/readiness 明确排除 Outbox，独立 `operations` group 承载诊断。
3. Outbox monitor 新增低基数 `scheduler_failure` alert gauge；该 gauge 直接读取调度器内存状态而不触发仓储查询。Prometheus 增加 ready lag、dead letter、expired lock 和 scheduler failure 四条规则，不重复硬编码应用阈值。
4. 新增 `j-store-observability-spring`，统一 Actuator、Micrometer Tracing、Prometheus 和 HTTP correlation 自动配置。模块不依赖项目模块，不在运行时引入 Spring Web；组件专属 health 留在组件所有者。

本结果不等于生产就绪：尚无 OTLP Trace 后端、HA 应用/日志平台、真实 Alertmanager 通知路由、恢复演练或批准的 SLO/RPO/RTO。

## 验收映射

| 需求 | 实现与证据 |
|---|---|
| OPF-R1 | `application.properties`、`application-local.properties`、`application-production.properties`；`ProductionProfileContractTest` |
| OPF-R2 | `OutboxHealthIndicator`、operations/liveness/readiness health group；`OutboxHealthIndicatorTest` |
| OPF-R3 | `scheduler_failure` gauge、四条 Prometheus 告警、Kubernetes tooling test、Prometheus 3.13.2 `promtool` |
| OPF-R4 | `j-store-observability-spring`、Servlet/非 Servlet 自动配置测试、Outbox 无 Actuator 退化测试、模块边界治理契约 |

## 验证结果

- PASS：同步 `origin/develop@beed6dad16bbaa28c884ddba6ee8753f8b68d7d8` 后，Kubernetes application/observability/agentic CI/CD tooling tests，24 tests。
- PASS：spec-dev 28、governance 42、tooling 77 tests；file ownership 识别 1368 个文件。
- PASS：新模块、Outbox、production profile/health 聚焦测试；缺少 Web 或 Actuator 的过滤 classloader 场景通过。
- PASS：全仓 `test`、Spotless、依赖解析、Licensee、制品许可证和 SBOM，Gradle 276 个 actionable tasks 中 11 executed、265 up-to-date。
- PASS：55 个 runtime classpath 的 Log4j 2.25.5 解析一致性；全部模块 Licensee；58 个 JAR 的 Apache-2.0 制品验证。
- PASS：根应用 Actuator 由 `j-store-observability-spring` 传递解析；新模块 runtimeClasspath 不含 Web/Tomcat，Outbox runtimeClasspath 不含 Actuator。
- PASS：CI 固定且 SHA-256 校验通过的 OSV Scanner 2.4.0 扫描 SBOM 中 205 个包，无已知问题。
- PASS：远程开发主机缓存的 `prom/prometheus:v3.13.2` 执行 `promtool check rules`，识别并通过 9 条规则。
- PASS：`git diff --check`。
- 环境偏差：直接从 WSL 执行 `./scripts/quality-gate.sh` 时，WSL Git 无法解析 Windows worktree `.git` 中的 `C:/...` 路径；失败发生在 `git ls-files`，不是候选测试失败。随后以 Windows 原生 Python/Git/Gradle 逐项执行同等阶段并全部通过。

## 残余风险与下一切片

1. 当前告警规则已能加载，但尚未接入真实 Alertmanager 接收、抑制、升级和恢复演练。
2. `operations` group 暴露无敏感 payload 的 Outbox 摘要；生产网络访问边界仍需 production overlay 和 NetworkPolicy 数据面证据。
3. 下一切片应完成 OpenTelemetry Java Agent/当前 Micrometer bridge 重复埋点 spike，形成唯一 exporter owner ADR，再接入 HA Collector 与 Trace 后端。
4. 后续仍需不可变镜像、至少双副本、RollingUpdate、PDB、连接预算和恢复演练；本切片不批准生产发布。
