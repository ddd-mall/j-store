# 需求文档：分布式定时任务调度中心

## 简介

将现有嵌入式超时中心（timer job center）升级为独立的分布式调度服务。当前超时中心以嵌入式方式运行在 `j-store-boot` 中，业务逻辑与调度逻辑耦合在同一进程内。本次改造将其拆分为三个独立 Gradle 模块：`timer-job-api`（共享接口与数据模型）、`timer-job-client`（业务服务集成的 SDK）、`timer-job-server`（独立部署的调度中心服务），通过 HTTP 通信实现跨服务的任务提交与回调派发，支持在 K8s 集群中独立部署和水平扩展。

## 术语表

- **Timer_Job_Server**: 独立部署的分布式定时任务调度中心服务，负责任务存储、调度和到期派发
- **Timer_Job_Client**: 集成到业务服务中的 SDK 模块，负责向 Timer_Job_Server 提交任务、接收回调并路由到对应的 Handler
- **Timer_Job_API**: 共享接口模块，定义 Timer_Job_Server 与 Timer_Job_Client 之间的通信契约（数据模型、接口定义）
- **Business_Service**: 集成了 Timer_Job_Client 的业务服务（如 j-store-boot），通过实现 TimerJobHandler 接口来处理到期任务
- **TimerJobHandler**: 业务服务实现的任务处理接口，每个实现对应一个 topic，负责处理到期任务的具体业务逻辑
- **Topic**: 任务主题标识，用于区分不同类型的定时任务，每个 topic 对应一个 TimerJobHandler 实现
- **Callback_Endpoint**: Timer_Job_Client 在 Business_Service 中暴露的 HTTP 端点，用于接收 Timer_Job_Server 的到期任务回调
- **WaitingQueue**: Redis Sorted Set 实现的等待队列，存放尚未到期的任务，score 为执行时间戳
- **PrepareQueue**: Redis Sorted Set 实现的预备队列，存放已取出正在处理的任务，用于二阶段消费保证可靠性
- **Slot**: 基于 CRC32 哈希的任务分片单元，用于将任务均匀分布到多个队列中，支持并行消费
- **Dead_Letter_Queue**: 死信队列，存放重试次数耗尽仍未成功处理的任务
- **Service_Registration**: Business_Service 启动时通过 Timer_Job_Client 向 Timer_Job_Server 注册自身支持的 topic 列表和回调地址的过程

## 需求

### 需求 1：模块拆分与共享接口定义

**用户故事：** 作为开发者，我希望将超时中心拆分为 api、client、server 三个独立 Gradle 模块，以便业务服务可以通过引入 client 依赖来集成定时任务能力，而无需与调度中心部署在同一进程中。

#### 验收标准

1. THE Timer_Job_API 模块 SHALL 定义 TimerJobHandler 接口，包含 `topic(): String` 和 `handle(job: TimerJobCallback): Boolean` 两个方法
2. THE Timer_Job_API 模块 SHALL 定义任务提交请求模型（TimerJobSubmitRequest），包含 topic、content、executeTime 字段
3. THE Timer_Job_API 模块 SHALL 定义任务回调模型（TimerJobCallback），包含 jobId、topic、content、executeTime 字段
4. THE Timer_Job_API 模块 SHALL 定义任务提交响应模型（TimerJobSubmitResponse），包含 jobId、topic、executeTime 字段
5. THE Timer_Job_API 模块 SHALL 不依赖 Spring Framework 或任何基础设施框架，仅包含纯 Kotlin/Java 接口和数据类
6. THE Timer_Job_Client 模块 SHALL 依赖 Timer_Job_API 模块和 Spring Boot Starter Web
7. THE Timer_Job_Server 模块 SHALL 依赖 Timer_Job_API 模块、Spring Boot Starter Web、Spring Data JPA 和 Spring Data Redis

### 需求 2：服务注册与发现

**用户故事：** 作为开发者，我希望业务服务启动时自动向超时中心注册自身支持的 topic 和回调地址，以便超时中心知道任务到期后应该回调哪个服务实例。

#### 验收标准

1. WHEN Business_Service 启动完成时，THE Timer_Job_Client SHALL 自动收集本服务中所有 TimerJobHandler 实现的 topic 列表，并通过 HTTP POST 请求向 Timer_Job_Server 发送注册信息（包含 topic 列表和回调地址）
2. THE Timer_Job_Client SHALL 通过配置属性 `timer-job.server.base-url` 获取 Timer_Job_Server 的地址
3. THE Timer_Job_Client SHALL 通过配置属性 `timer-job.client.callback-url` 获取本服务的回调基础地址，若未配置则根据本服务的 IP 和端口自动生成
4. WHILE Business_Service 处于运行状态，THE Timer_Job_Client SHALL 以可配置的间隔（默认 30 秒）定期向 Timer_Job_Server 发送心跳续约请求，以维持注册信息的有效性
5. WHEN Timer_Job_Server 收到注册请求时，THE Timer_Job_Server SHALL 将该 Business_Service 的 topic 列表和回调地址存储到 Redis 中，并设置 TTL（默认 90 秒，为心跳间隔的 3 倍）
6. WHEN Timer_Job_Server 在 TTL 时间内未收到某个 Business_Service 的心跳续约时，THE Timer_Job_Server SHALL 自动移除该 Business_Service 的注册信息
7. WHEN 同一 topic 有多个 Business_Service 实例注册时，THE Timer_Job_Server SHALL 保留所有实例的回调地址，并在回调时采用轮询策略选择目标实例

### 需求 3：任务提交

**用户故事：** 作为开发者，我希望通过 Timer_Job_Client 提供的 API 向超时中心提交定时任务，以便任务到期后被自动调度执行。

#### 验收标准

1. THE Timer_Job_Client SHALL 提供 TimerJobRemoteService 类，暴露 `submit(topic: String, content: String, delay: Duration): TimerJobSubmitResponse` 方法和 `submitAt(topic: String, content: String, executeTime: Date): TimerJobSubmitResponse` 方法
2. WHEN TimerJobRemoteService 的 submit 或 submitAt 方法被调用时，THE Timer_Job_Client SHALL 通过 HTTP POST 请求将任务提交到 Timer_Job_Server 的 `/api/timer-job/submit` 端点
3. WHEN Timer_Job_Server 收到任务提交请求时，THE Timer_Job_Server SHALL 将任务持久化到 PostgreSQL 数据库，并同时写入 Redis WaitingQueue，返回包含 jobId 的响应
4. WHEN Timer_Job_Server 收到任务提交请求时，THE Timer_Job_Server SHALL 使用 CRC32 哈希算法根据任务内容计算 slot，将任务分配到对应的 WaitingQueue 分片中
5. IF Timer_Job_Client 提交任务时 Timer_Job_Server 不可达，THEN THE Timer_Job_Client SHALL 抛出 TimerJobServerUnavailableException 异常，包含错误详情
6. IF Timer_Job_Server 收到的任务提交请求中 topic 为空或 executeTime 为空，THEN THE Timer_Job_Server SHALL 返回 HTTP 400 错误响应，包含具体的参数校验失败信息

### 需求 4：任务调度与到期派发

**用户故事：** 作为开发者，我希望超时中心在任务到期后自动通过 HTTP 回调通知业务服务执行任务，以便实现跨服务的定时任务调度。

#### 验收标准

1. THE Timer_Job_Server SHALL 保留现有的 DB + Redis 双存储调度架构，使用 Redis Sorted Set 作为调度层，Lua 脚本保证原子操作
2. THE Timer_Job_Server SHALL 保留现有的二阶段消费机制（WaitingQueue → PrepareQueue → 完成/回滚）
3. WHEN 任务到期（WaitingQueue 中 score 小于等于当前时间戳）时，THE Timer_Job_Server SHALL 通过 Lua 脚本原子地将任务从 WaitingQueue 移动到 PrepareQueue
4. WHEN 任务被移入 PrepareQueue 后，THE Timer_Job_Server SHALL 根据任务的 topic 查找已注册的 Business_Service 回调地址，通过 HTTP POST 请求将 TimerJobCallback 发送到 Callback_Endpoint
5. IF 回调请求返回成功（HTTP 200 且响应体中 success 为 true），THEN THE Timer_Job_Server SHALL 将任务从 PrepareQueue 中移除，将数据库中的任务记录移动到已处理表，标记为 HANDLED
6. IF 回调请求失败（网络异常、HTTP 非 200、或响应体中 success 为 false），THEN THE Timer_Job_Server SHALL 将任务从 PrepareQueue 回滚到 WaitingQueue，TTL 减 1，等待下次重试
7. IF 任务的 TTL 降至 0（已重试 16 次仍失败），THEN THE Timer_Job_Server SHALL 将任务移入 Dead_Letter_Queue，不再重试
8. IF 任务到期时其 topic 没有任何已注册的 Business_Service，THEN THE Timer_Job_Server SHALL 将任务回滚到 WaitingQueue，记录警告日志，等待 Business_Service 注册后重新调度

### 需求 5：回调接收与路由

**用户故事：** 作为开发者，我希望 Timer_Job_Client 自动接收超时中心的回调请求并路由到对应的 TimerJobHandler 实现，以便业务逻辑与调度通信解耦。

#### 验收标准

1. THE Timer_Job_Client SHALL 自动注册一个 Spring MVC Controller，暴露 `POST /timer-job/callback` 端点用于接收 Timer_Job_Server 的回调请求
2. WHEN Callback_Endpoint 收到回调请求时，THE Timer_Job_Client SHALL 根据请求中的 topic 字段查找本服务中对应的 TimerJobHandler 实现，并调用其 handle 方法
3. WHEN TimerJobHandler 的 handle 方法返回 true 时，THE Timer_Job_Client SHALL 向 Timer_Job_Server 返回 HTTP 200 响应，响应体中 success 为 true
4. WHEN TimerJobHandler 的 handle 方法返回 false 或抛出异常时，THE Timer_Job_Client SHALL 向 Timer_Job_Server 返回 HTTP 200 响应，响应体中 success 为 false，并包含错误信息
5. IF 回调请求中的 topic 在本服务中没有对应的 TimerJobHandler 实现，THEN THE Timer_Job_Client SHALL 返回 HTTP 200 响应，响应体中 success 为 false，错误信息为 "未找到 topic 对应的 handler"

### 需求 6：Slot 分片与分布式调度

**用户故事：** 作为运维人员，我希望超时中心支持多实例部署时的 slot 分片独占消费，以便实现水平扩展和高可用。

#### 验收标准

1. THE Timer_Job_Server SHALL 保留现有的 CRC32 slot 分片机制，slot 数量通过配置属性 `timer-job.slot-amount`（默认 8）控制
2. THE Timer_Job_Server SHALL 保留现有的基于 Redis 的分布式 slot 分配机制，每个 Timer_Job_Server 实例通过 Redis 锁竞争 slot 所有权
3. WHILE Timer_Job_Server 实例持有某个 slot 的锁时，THE Timer_Job_Server 实例 SHALL 每 10 秒续约一次锁，锁 TTL 为 30 秒
4. WHEN 某个 Timer_Job_Server 实例宕机时，THE Timer_Job_Server 集群中的其他实例 SHALL 在锁 TTL 过期后（最多 30 秒）自动接管该实例持有的 slot
5. THE Timer_Job_Server SHALL 保留现有的 JobLoader 补偿扫描机制，定期将数据库中 UNHANDLED 状态的任务加载到 Redis WaitingQueue 中
6. THE Timer_Job_Server SHALL 保留现有的 JobTimeoutMonitor 超时监控机制，将 PrepareQueue 中超时未完成的任务回滚到 WaitingQueue

### 需求 7：优雅启停与生命周期管理

**用户故事：** 作为运维人员，我希望超时中心在 K8s 环境中支持优雅启停，以便在滚动更新时不丢失任务。

#### 验收标准

1. WHEN Timer_Job_Server 收到关闭信号时，THE Timer_Job_Server SHALL 立即释放所有持有的 slot 锁，使其他实例可以尽快接管
2. WHEN Timer_Job_Server 收到关闭信号时，THE Timer_Job_Server SHALL 停止从 WaitingQueue 取出新任务，等待当前正在处理的任务完成（最多等待 30 秒），然后关闭
3. WHEN Timer_Job_Client 所在的 Business_Service 收到关闭信号时，THE Timer_Job_Client SHALL 向 Timer_Job_Server 发送注销请求，移除自身的注册信息
4. WHEN Timer_Job_Server 启动完成时，THE Timer_Job_Server SHALL 执行一次补偿扫描，将数据库中遗留的 HANDLING 状态且已超时的任务重新加载到 Redis WaitingQueue

### 需求 8：配置与自动装配

**用户故事：** 作为开发者，我希望 Timer_Job_Client 支持 Spring Boot 自动装配，以便业务服务只需引入依赖和添加少量配置即可使用定时任务能力。

#### 验收标准

1. THE Timer_Job_Client SHALL 提供 Spring Boot Auto-Configuration 类，在 Business_Service 引入 timer-job-client 依赖后自动装配所有必要的 Bean（TimerJobRemoteService、回调 Controller、注册组件）
2. THE Timer_Job_Client SHALL 通过 `@ConfigurationProperties` 暴露以下配置属性：`timer-job.server.base-url`（必填）、`timer-job.client.callback-url`（选填）、`timer-job.client.heartbeat-interval`（默认 30 秒）
3. THE Timer_Job_Server SHALL 通过 `@ConfigurationProperties` 暴露以下配置属性：`timer-job.slot-amount`（默认 8）、`timer-job.registration.ttl`（默认 90 秒）、`timer-job.thread-pool.core-size`（默认 3）、`timer-job.thread-pool.max-size`（默认 20）
4. THE Timer_Job_Client SHALL 在 Spring Boot Auto-Configuration 中自动扫描并收集所有实现了 TimerJobHandler 接口的 Bean

### 需求 9：序列化与通信契约

**用户故事：** 作为开发者，我希望 Timer_Job_Server 与 Timer_Job_Client 之间的 HTTP 通信使用统一的 JSON 序列化格式，以便保证数据传输的正确性和兼容性。

#### 验收标准

1. THE Timer_Job_API 模块 SHALL 定义统一的 HTTP 响应包装模型 TimerJobResponse<T>，包含 success（Boolean）、data（T）、errorMessage（String）字段
2. WHEN Timer_Job_Client 向 Timer_Job_Server 发送任务提交请求时，THE Timer_Job_Client SHALL 使用 JSON 格式序列化 TimerJobSubmitRequest，Content-Type 为 application/json
3. WHEN Timer_Job_Server 向 Business_Service 发送回调请求时，THE Timer_Job_Server SHALL 使用 JSON 格式序列化 TimerJobCallback，Content-Type 为 application/json
4. FOR ALL TimerJobCallback 对象，将其序列化为 JSON 再反序列化 SHALL 产生与原始对象等价的结果（往返一致性）
5. FOR ALL TimerJobSubmitRequest 对象，将其序列化为 JSON 再反序列化 SHALL 产生与原始对象等价的结果（往返一致性）
