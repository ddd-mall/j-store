# 生产可观测性应用闭环任务

## 当前现状

- 基线：`origin/develop@beed6dad16bbaa28c884ddba6ee8753f8b68d7d8`
- 当前阶段：迭代 1 首个应用侧切片
- 不涉及：生产写入、远端集群变更、Secret/权限变更、OTLP 后端与 HA 平台

## OPF-T1：显式 profile 与 production 配置

- [x] RED：增加配置契约测试，证明根配置仍默认激活 `local` 且 production profile 缺失。
- [x] GREEN：移除默认 active profile，新增 production profile group/config，修正 Hikari 身份。
- [x] REFACTOR：集中断言敏感配置无默认值，并验证 Kubernetes 开发清单仍显式选择 local。
- 证据：`ProductionProfileContractTest`。

## OPF-T2：Outbox Actuator health

- [x] RED：增加四状态映射、sanitized details 和条件装配测试。
- [x] GREEN：实现 `OutboxHealthIndicator` 与条件配置。
- [x] REFACTOR：明确 operations/liveness/readiness health group，避免重复阈值计算。
- 证据：`OutboxHealthIndicatorTest`、`ProductionProfileContractTest`。

## OPF-T3：Outbox Prometheus 告警

- [x] RED：扩展 Kubernetes observability tooling test，要求四条规则和低基数表达式。
- [x] GREEN：补充 ready lag、dead letter、expired lock、scheduler failure 规则。
- [x] REFACTOR：运行 Kustomize 与 Prometheus 规则语法检查，确保恢复语义不依赖动态标签。
- 证据：`tests.tooling.test_kubernetes_observability`、promtool（环境可用时）。

## OPF-T4：收敛与门禁

- [x] 运行 `:j-store-boot:test` 和相关 Outbox 测试。
- [x] 运行 Kubernetes application/observability tooling tests。
- [x] 运行 `git diff --check` 与质量门禁的 Windows 原生等价步骤；WSL wrapper 路径限制单独记录。
- [x] 更新本任务状态和 `summary.md`，记录 PASS/FAIL/SKIPPED、残余风险和下一切片。

## OPF-T5：可观测性模块化迁移

- [x] RED：增加独立模块的 Servlet/非 Servlet 自动配置测试，以及 Outbox 组件自有 HealthIndicator 条件装配测试。
- [x] GREEN：新增 `j-store-observability-spring`，迁移 correlation filter 和通用运行依赖；将 Outbox HealthIndicator 下沉到 `j-store-outbox-spring`。
- [x] REFACTOR：拆分通用 `observability` 与应用选择的 `outbox-observability` profile，移除根应用重复依赖和实现。
- [x] VERIFY：运行新模块、Outbox、根应用、Kubernetes/tooling、依赖治理及格式门禁，更新交付摘要。
