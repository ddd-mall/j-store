# 实现计划：Transactional Outbox（事务性发件箱）

## 概述

将设计文档中的 Transactional Outbox 模式分解为增量实现步骤。从领域层核心模型开始，逐步构建序列化、持久化、事件发布、轮询投递、清理和自动配置，最后通过 DDL 脚本和集成测试完成端到端验证。所有代码使用 Kotlin，遵循项目 DDD 架构规范。

## Tasks

- [x] 1. 在 j-store-common-core 中创建 Outbox 领域模型和接口
  - [x] 1.1 创建 OutboxEntryStatus 枚举和 OutboxEntry 数据类
    - 在 `j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/` 包下创建
    - `OutboxEntryStatus` 枚举包含 PENDING、PUBLISHED、FAILED、DEAD_LETTER 四种状态
    - `OutboxEntry` data class 包含 id、eventType、payload、aggregateType、aggregateId、status、createdAt、updatedAt、retryCount 字段
    - _需求: 1.4_

  - [x] 1.2 创建 OutboxEntryRepository 接口
    - 在同一包下创建仓储接口，定义 `save`、`findPendingAndRetryable`、`deletePublishedBefore` 方法
    - 方法签名仅使用领域对象，不依赖任何框架类型
    - _需求: 1.1, 2.1, 6.1_

  - [x] 1.3 创建 EventSerializer 接口
    - 在同一包下定义 `serialize(event: DomainEvent): String` 和 `deserialize(payload: String, eventType: String): DomainEvent` 方法
    - _需求: 4.1, 4.2_

  - [x] 1.4 创建 OutboxSerializationException 自定义异常类
    - 在同一包下创建，继承 RuntimeException，包含 message 和 cause 参数
    - _需求: 4.4, 4.5_

- [x] 2. 在 j-store-common-spring 中实现 JacksonEventSerializer
  - [x] 2.1 实现 JacksonEventSerializer 类
    - 在 `j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/` 包下创建
    - 使用 Jackson ObjectMapper 实现 serialize 和 deserialize 方法
    - deserialize 中处理 ClassNotFoundException 抛出包含事件类型信息的 OutboxSerializationException
    - deserialize 中处理 JSON 解析异常抛出包含载荷摘要（截取前 200 字符）的 OutboxSerializationException
    - _需求: 1.5, 4.1, 4.2, 4.4, 4.5_

  - [x] 2.2 编写 Property 2 的属性测试：序列化/反序列化 Round-Trip
    - **Property 2: 序列化/反序列化 Round-Trip**
    - 使用 Kotest property testing 生成随机 DomainEvent 对象（包括各种 OrderDomainEvent 子类），验证 serialize 后 deserialize 产生等价对象
    - **验证: 需求 4.1, 4.2, 4.3, 1.5**

  - [x] 2.3 编写 JacksonEventSerializer 单元测试
    - 测试未知事件类型抛出 OutboxSerializationException 且包含类型信息
    - 测试格式错误 JSON 抛出 OutboxSerializationException 且包含载荷摘要
    - _需求: 4.4, 4.5_

- [x] 3. 在 j-store-common-spring 中实现 OutboxEventPublisher
  - [x] 3.1 创建 OutboxProperties 配置属性类
    - 使用 `@ConfigurationProperties(prefix = "jstore.outbox")` 注解
    - 包含 enabled、pollingInterval、batchSize、maxRetryCount、retentionDays、cleanupBatchSize、cleanupCron 属性及默认值
    - _需求: 2.5, 3.4, 5.5, 6.2, 6.3_

  - [x] 3.2 实现 OutboxEventPublisher 类
    - 实现 `DomainEventPublisher` 接口
    - `publishEvent` 方法中创建 OutboxEntry（状态为 PENDING），通过 EventSerializer 序列化事件，调用 OutboxEntryRepository.save 持久化
    - 提取 aggregateType 和 aggregateId 的辅助方法
    - _需求: 1.1, 1.2, 1.4, 5.1_

  - [x] 3.3 编写 Property 1 的属性测试：事件持久化为 PENDING 状态
    - **Property 1: 事件持久化为 PENDING 状态**
    - 使用 mock OutboxEntryRepository，验证 publishEvent 后 save 被调用且 entry 的 status 为 PENDING、eventType 为全限定类名
    - **验证: 需求 1.1**

  - [x] 3.4 编写 OutboxEventPublisher 单元测试
    - 验证 publishEvent 正确创建 OutboxEntry 并调用 save
    - 验证序列化失败时异常向上传播（确保业务事务回滚）
    - _需求: 1.1, 1.2, 1.3_

- [x] 4. 检查点 - 确保核心模型和序列化层测试通过
  - 确保所有测试通过，如有问题请向用户确认。

- [x] 5. 在 j-store-common-spring 中实现 JPA 持久化层
  - [x] 5.1 创建 OutboxEntryPO JPA 实体类
    - 在 `j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/persistence/` 包下创建
    - 使用 `@Entity`、`@Table(name = "outbox_entry")` 注解
    - 字段映射与设计文档一致，status 使用 `@Enumerated(EnumType.STRING)`
    - _需求: 1.4_

  - [x] 5.2 创建 OutboxEntryPOJpaRepository 接口
    - 继承 `JpaRepository<OutboxEntryPO, String>`
    - 实现 `findPendingAndRetryable` 查询：SELECT status=PENDING OR (status=FAILED AND retryCount < maxRetryCount) ORDER BY createdAt ASC
    - 实现 `deletePublishedBefore` 删除查询：DELETE WHERE status=PUBLISHED AND createdAt < before
    - _需求: 2.1, 2.4, 6.1_

  - [x] 5.3 实现 OutboxEntryRepositoryImpl 仓储实现
    - 包含 Converter 对象实现 PO ↔ 领域模型转换
    - 实现 save、findPendingAndRetryable、deletePublishedBefore 方法
    - _需求: 1.1, 2.1, 6.1_

- [ ] 6. 在 j-store-common-spring 中实现 OutboxPublisher 轮询投递
  - [x] 6.1 实现 OutboxPublisher 类
    - 注入 OutboxEntryRepository、EventSerializer、DomainEventBus、OutboxProperties
    - `pollAndPublish` 方法：轮询 findPendingAndRetryable，逐条反序列化并投递到 DomainEventBus
    - 投递成功更新状态为 PUBLISHED；失败时 retryCount+1，达到上限标记为 DEAD_LETTER
    - 投递完成后记录 INFO 日志（投递数量和失败数量）
    - DEAD_LETTER 状态变更时记录 WARN 日志
    - 投递失败时记录 ERROR 日志
    - 顶层异常捕获，记录 ERROR 日志但不中断调度
    - _需求: 2.1, 2.2, 2.3, 2.4, 2.5, 3.1, 3.2, 3.3, 3.5, 7.1, 7.2, 7.3_

  - [x] 6.2 编写 Property 3 的属性测试：成功投递后状态变更为 PUBLISHED
    - **Property 3: 成功投递后状态变更为 PUBLISHED**
    - 生成 PENDING 状态的 OutboxEntry，模拟成功投递，验证状态更新为 PUBLISHED
    - **验证: 需求 2.2, 2.3**

  - [x] 6.3 编写 Property 4 的属性测试：事件按创建时间升序投递
    - **Property 4: 事件按创建时间升序投递**
    - 生成多条不同 createdAt 的 OutboxEntry，验证投递到 DomainEventBus 的顺序与 createdAt 升序一致
    - **验证: 需求 2.4**

  - [x] 6.4 编写 Property 5 的属性测试：批次大小限制
    - **Property 5: 批次大小限制**
    - 生成超过 batchSize 数量的待投递条目，验证每次轮询获取的条目数量不超过 batchSize
    - **验证: 需求 2.5**

  - [x] 6.5 编写 Property 6 的属性测试：失败处理与死信转换
    - **Property 6: 失败处理与死信转换**
    - 生成不同 retryCount 的 OutboxEntry，模拟投递失败，验证 retryCount+1 < maxRetryCount 时状态为 FAILED，否则为 DEAD_LETTER
    - **验证: 需求 3.1, 3.3**

  - [x] 6.6 编写 OutboxPublisher 单元测试
    - 验证轮询、投递、状态更新的完整流程
    - 验证异常不中断调度（顶层异常捕获）
    - 验证日志输出（INFO/WARN/ERROR 级别）
    - _需求: 2.1, 2.2, 2.3, 3.1, 3.5, 7.1, 7.2, 7.3_

- [x] 7. 检查点 - 确保轮询投递逻辑测试通过
  - 确保所有测试通过，如有问题请向用户确认。

- [x] 8. 在 j-store-common-spring 中实现 OutboxCleaner 和仓储属性测试
  - [x] 8.1 实现 OutboxCleaner 类
    - 注入 OutboxEntryRepository 和 OutboxProperties
    - `cleanup` 方法：计算保留期限，调用 deletePublishedBefore 删除过期已发布条目
    - 记录清理日志
    - _需求: 6.1, 6.2, 6.3, 6.4_

  - [x] 8.2 编写 Property 7 的属性测试：重试资格查询
    - **Property 7: 重试资格查询**
    - 生成混合状态和重试次数的条目集合，验证 findPendingAndRetryable 仅返回 PENDING 或 (FAILED AND retryCount < maxRetryCount) 的条目
    - **验证: 需求 3.2**

  - [x] 8.3 编写 Property 8 的属性测试：清理仅删除符合条件的已发布条目
    - **Property 8: 清理仅删除符合条件的已发布条目**
    - 生成不同状态和创建时间的条目集合，验证清理操作仅删除 PUBLISHED 且过期的条目，不删除 DEAD_LETTER/PENDING/FAILED 条目
    - **验证: 需求 6.1, 6.3, 6.4**

  - [x] 8.4 编写 OutboxCleaner 单元测试
    - 验证清理逻辑的正确性
    - 验证不删除 DEAD_LETTER 状态的条目
    - _需求: 6.1, 6.4_

- [x] 9. 创建 OutboxAutoConfiguration 自动配置和 DDL 脚本
  - [x] 9.1 创建 OutboxAutoConfiguration 自动配置类
    - 使用 `@ConditionalOnProperty(prefix = "jstore.outbox", name = ["enabled"], havingValue = "true")` 条件注解
    - 注册 EventSerializer、OutboxEntryRepository、DomainEventPublisher（OutboxEventPublisher）、OutboxPublisher、OutboxCleaner 的 Bean
    - 使用 `@EnableScheduling` 启用调度
    - OutboxPublisher.pollAndPublish 使用 `@Scheduled(fixedDelayString)` 配置轮询间隔
    - OutboxCleaner.cleanup 使用 `@Scheduled(cron)` 配置清理 cron
    - _需求: 5.1, 5.2, 5.3, 5.5_

  - [x] 9.2 在 j-store-boot 资源目录中创建 DDL 迁移脚本
    - 在 `j-store-boot/src/main/resources/db/migration/` 下创建 outbox_entry 表的 DDL 脚本
    - 包含表创建语句和三个索引（轮询索引、清理索引、聚合根维度索引）
    - _需求: 8.4_

  - [x] 9.3 更新 j-store-common-spring 的 build.gradle.kts 依赖
    - 确保 jackson-databind 和 jackson-module-kotlin 依赖可用（通过 j-store-common-core 的 api 传递或显式添加）
    - _需求: 8.2_

- [x] 10. 集成验证与功能开关测试
  - [x] 10.1 编写集成测试：验证 enabled=true 时注册 OutboxEventPublisher
    - 启动 Spring 上下文，配置 `jstore.outbox.enabled=true`，验证 DomainEventPublisher Bean 类型为 OutboxEventPublisher
    - _需求: 5.2, 5.5_

  - [x] 10.2 编写集成测试：验证 enabled=false 时回退到 SpringDomainEventPublisher
    - 启动 Spring 上下文，配置 `jstore.outbox.enabled=false` 或不配置，验证 DomainEventPublisher Bean 类型为 SpringDomainEventPublisher
    - _需求: 5.4, 5.5_

  - [ ] 10.3 编写集成测试：验证事务原子性
    - 验证业务数据和 Outbox 条目在同一事务中提交/回滚
    - _需求: 1.2, 1.3_

- [x] 11. 最终检查点 - 确保所有测试通过
  - 确保所有测试通过，如有问题请向用户确认。

## 备注

- 标记 `*` 的任务为可选任务，可跳过以加速 MVP 交付
- 每个任务引用了具体的需求编号，确保可追溯性
- 检查点任务确保增量验证
- 属性测试使用 Kotest property testing 模块验证正确性属性
- 单元测试验证具体示例和边界情况
- 所有代码遵循项目 DDD 架构规范：领域模型在 j-store-common-core（无框架依赖），Spring 集成在 j-store-common-spring，DDL 在 j-store-boot
