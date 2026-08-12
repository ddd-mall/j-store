# 可观测性基础建设迭代计划

## 执行规则

- 按迭代一、二、三顺序推进；后一迭代不得绕过前一迭代的退出门禁。
- 所有行为变更遵循 TDD：先提交可复现缺口的失败测试，再提交最小实现和重构。
- 复选框表示实施状态，不替代测试输出、查询结果或审查记录。
- 当前状态：三个迭代的工程实现已完成；本机容器 smoke、独立安全/隐私评审和运维验收待执行。

## 迭代一：日志安全基线与敏感数据治理（最高优先级）

### 目标

在日志进入集中后端前消除已确认的原始载荷泄露路径，并建立后续日志代码必须遵守的字段边界。

### 任务

- [x] OBS-T1.1：通过引用审计确认旧 `TimerJob` 子系统只有测试入口、测试 handler 和压测脚本，没有业务调用者。（OBS-R1）
- [x] OBS-T1.2：删除完整死代码垂直切片，包括 Controller、handler、调度/JPA/Redis 实现、三张表定义、Lua、压测 SQL 和仅验证该死代码的测试。（OBS-R1）
- [x] OBS-T1.3：新增日志安全规范，定义允许、脱敏、禁止字段以及 HTTP、认证、任务、异常和集成消息示例。（OBS-R1、质量目标：安全与隐私）
- [x] OBS-T1.4：增加针对 JWT、密码、验证码、完整手机号及任务载荷的静态检查或聚焦契约测试，并执行安全自审。（OBS-R1）
- [x] OBS-T1.5：清理可观测性触达文件中的冗余依赖和无调用配置，验证运行时装配不变。（OBS-R1、质量目标：可维护性）

### 验证证据

- 全仓引用扫描证明已删除子系统没有业务调用方，删除后编译和回归测试通过。
- 数据库基线与初始化脚本不再创建无消费者的任务表，应用不再暴露 `/timer/job/**` 测试端点；内部开发环境按需重建。
- `git diff --check`、相关模块测试和敏感日志扫描结果。

### 退出门禁

- 已知原始任务载荷路径及其无业务价值的生产入口全部移除。
- 日志安全规范经过人工确认，且没有未决高风险隐私问题。
- 不以吞掉异常或移除必要诊断信息来换取“无敏感字段”。

## 迭代二：结构化日志与端到端上下文关联

### 目标

让每条关键边界日志具备稳定机器可读结构，并能从 HTTP 请求关联到本地处理、远程调用和集成消息。

### 依赖

迭代一退出门禁全部满足。

### 任务

- [x] OBS-T2.1：先补启动/日志契约测试，断言控制台输出为逐行 ECS JSON，并包含服务名、版本、环境、级别、logger 和消息。（OBS-R2）
- [x] OBS-T2.2：启用 Spring Boot 原生 ECS 结构化控制台日志，修正根组合应用服务标识，并提供本地可读与容器结构化 profile。（OBS-R2）
- [x] OBS-T2.3：先补 HTTP 契约测试，再实现受控 correlation header 的延续、非法值替换、自动生成、响应回传和上下文清理。（OBS-R3）
- [x] OBS-T2.4：接入 Micrometer Tracing/OpenTelemetry，使日志自动包含 `trace_id/span_id`，并验证 W3C Trace Context 在 MVC 与 WebClient/RestClient 调用中传播。（OBS-R3）
- [x] OBS-T2.5：将远程用户客户端改为注入 Spring 自动配置 Builder，补传播和既有认证头回归测试。（OBS-R3）
- [x] OBS-T2.6：先补连续消费与异常测试，再为集成消息 handler 建立并清理 `message_id/correlation_id/causation_id/transport_id` 日志作用域。（OBS-R4）

### 验证证据

- JSON 日志契约测试和 HTTP correlation 契约测试。
- 两个并发请求及同线程连续消息消费的无串扰测试。
- 跨一个远程客户端调用的 `traceparent` 传播证据。
- 相关 common、authentication、user-client、messaging、outbox 和 boot 模块测试。

### 退出门禁

- 任一合成请求均可用 correlation ID 定位入口、关键处理和出口日志。
- 启用追踪时可用 trace ID 关联跨 HTTP 调用日志。
- 消息异常路径不会遗留 MDC/作用域字段。
- 默认本地业务启动和既有 API 契约不回归。

## 迭代三：集中采集、查询与运行信号闭环

### 目标

提供一条仓库内可重复启动和验证的端到端链路，证明结构化日志和现有 Outbox 信号能够被实际采集、保存、查询和展示。

### 依赖

迭代二退出门禁全部满足。

### 任务

- [x] OBS-T3.1：新增独立可观测性 Compose 覆盖层，编排 Alloy、Loki、Prometheus 和 Grafana；默认数据库/Redis Compose 行为保持不变。（OBS-R5）
- [x] OBS-T3.2：配置容器发现、JSON 解析、低基数标签、位置记录、有限文件系统缓冲、重试和采集器健康指标。（OBS-R5）
- [x] OBS-T3.3：引入并最小暴露 Actuator/Prometheus registry，补装配测试证明 Outbox monitor 不再回退为 Noop，既有 `jstore.outbox.*` meters 可查询。（OBS-R6）
- [x] OBS-T3.4：通过 provisioning 创建 Loki/Prometheus 数据源和最小 Dashboard，覆盖错误日志、correlation/trace 查询及 Outbox lag、死信、过期锁和调度失败。（OBS-R5、OBS-R6）
- [x] OBS-T3.5：编写自动或半自动 smoke test：产生唯一合成日志，在限定时间内从 Loki 查询命中；重启采集器后验证缓冲内续传，并检查解析失败/丢弃信号。（OBS-R5）
- [x] OBS-T3.6：编写本地运行手册，说明启动、查询、健康检查、磁盘容量、保留、停止和可恢复清理方式，不包含真实凭据。（OBS-R5、OBS-R6）
- [ ] OBS-T3.7：运行相关模块回归、Compose smoke test、`git diff --check` 和 `./scripts/quality-gate.sh`，记录未运行项与残余风险。（OBS-R1-OBS-R6）

### 验证证据

- Compose 服务健康状态和 Grafana 数据源健康结果。
- Loki 对唯一合成 correlation ID/trace ID 的查询输出。
- Prometheus 中 Outbox meters 的查询结果和 Dashboard 截图或 JSON 证据。
- 后端短暂不可用、采集器重启以及缓冲上限场景的结果。
- 质量门禁输出及独立安全/运维评审记录。

### 退出门禁

- 新成员仅依据运行手册即可启动栈并完成一次日志与 Outbox 指标查询。
- 应用在日志后端不可用时不阻塞业务线程。
- 高基数业务标识没有被配置为 Loki 标签。
- Actuator 暴露遵循最小权限，仓库没有新增真实凭据。
- 所有适用验收标准有可定位证据，未决高风险问题为零。

## 后续候选迭代

完成前三个迭代并获得真实日志量、查询模式和故障数据后，再评估：

1. 生产 Loki/Elastic/托管服务选型、容量模型、高可用和长期归档。
2. 日志采样、速率限制、动态日志级别和成本预算。
3. 按故障场景补齐支付、库存、订单、履约和认证领域的关键业务事件日志。
4. SLO、告警路由、值班和事故响应 Runbook。

## 本次实施证据（2026-08-12）

- 死代码审计：全仓引用扫描确认旧订单过期任务只有 `/timer/job/**` 测试 Controller、`TestHandler` 和 `batch_insert_200k.sql`，没有真实业务 handler 或调用方；已删除 20 个 Java 类型、3 个 Lua、基线与初始化脚本中的 3 张表定义、压测 SQL、空 Redis 配置及其专用依赖。
- 依赖审计：删除根模块重复/未使用的 OpenTelemetry SDK、Redis、WebFlux、Commons Lang、FastExcel、Lombok/Kapt 和底层 Spring Data 声明；删除订单 infrastructure 中未使用的 Web、Redis、Commons Lang、Lombok 与重复 Spring Data 声明；Prometheus 未使用的 lifecycle/evaluation 配置亦已移除。
- 聚焦测试：ECS JSON 输出、HTTP correlation、敏感异常消息隔离、规范化路由路径、OpenTelemetry/W3C 传播、远程用户客户端、完整消息消费边界 MDC 清理和 Outbox meter 装配测试通过。
- 清理后回归：`:j-store-boot:test` 构建成功（106 个任务）；`:j-store-order-infrastructure:test :j-store-order-boot:test :j-store-user-boot:test` 构建成功（44 个任务）。
- 受影响模块回归：`:j-store-boot:test :j-store-messaging-local-spring:test :j-store-user-client-spring:test :j-store-outbox-spring:test` 构建成功，114 个任务完成或复用缓存。
- 远程用户档案部署兼容回归：`:j-store-user-boot:test --tests '*UserProfileQueryDeploymentConfigurationTest' :j-store-user-client-spring:test` 构建成功。
- 配置与制品：YAML/JSON 解析、PowerShell smoke 脚本语法、`j-store-boot:bootJar` 和 Alloy v1.18.0 `validate --stability.level=experimental` 均通过。
- 仓库治理契约、规格/治理/工具单元测试（28 + 22 + 19）及文件归属检查通过。
- 死代码清理后的 Windows 等价质量门禁通过：`spotlessCheck licensee test verifyLicenseArtifacts` 构建成功，248 个任务完成或复用缓存，53 个 JAR 制品许可证验证通过；治理脚本、69 项 Python 契约测试和文件归属检查亦通过。
- 未运行：本机未安装 Docker 或 Podman，无法执行 Compose `config`、服务健康检查与 `scripts/observability-smoke.ps1` 端到端场景；OBS-T3.7 因此保持未完成。
- 待人工：日志安全规范、隐私边界和本地栈运维性仍需独立安全/隐私与运维评审，实施者不自行批准退出门禁。
- 审查修复：异常访问日志只记录不含异常消息的类型与栈帧，并以合成手机号、密码、JWT、验证码和消息载荷验证无泄露；访问日志使用 MVC 路由模板；消息 MDC 覆盖 handler 校验和幂等 claim；本地端口仅绑定 loopback；运行手册明确旧基线 checksum 的破坏性重建步骤；全量 Flyway 回归通过 `to_regclass` 断言三张旧任务表不存在；PowerShell 契约验证 `.env` 非默认端口、Grafana 用户名、进程环境变量优先级和 loopback 端口绑定。
- 修复后回归：`spotlessCheck licensee test verifyLicenseArtifacts` 构建成功（250 个任务完成或复用缓存，53 个 JAR 制品验证通过）；治理检查、69 个 Python 契约测试、文件归属、PowerShell 语法与 3 个 Pester 契约测试通过；`git diff --check` 通过。
- 依赖漏洞修复：针对 `GHSA-rcgg-9c38-7xpx` 增加 OpenTelemetry 运行时最低安全版本回归测试，并通过 OpenTelemetry BOM 将 API、SDK 与 propagators 从 `1.49.0` 统一对齐到 `1.62.0`；Tracer、W3C `traceparent`（含 random flag）、Prometheus 和受观测 HTTP 客户端装配测试通过。重新生成的生产 SBOM 包含 197 个包，使用已校验发布摘要的 OSV Scanner 2.4.0 扫描结果为 0 个漏洞；完整 `./scripts/quality-gate.sh` 通过。
