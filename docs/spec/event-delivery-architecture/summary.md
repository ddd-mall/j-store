# 事件投递架构重构交付摘要

## 完成结果

- 原 `DomainEventBus` 已破坏性重命名为只表达本进程语义的 `LocalDomainEventBus`。
- 引入独立的 `IntegrationCommand` / `IntegrationEvent`、版本注册、JSON 序列化、handler bus 和消费幂等。
- Outbox 记录新增消息类别、投递目标、逻辑目的地、分区键、相关/因果/租户元数据。
- relay 改为 `OutboxDeliveryRouter` + 唯一目标通道，并支持 `local`、`broker`、`hybrid` 三种规划模式。
- `broker` / `hybrid` 缺少或存在多个 `BrokerIntegrationMessageTransport` 时启动失败，不做隐式降级。
- 新增 `j-store-integration-contracts`，订单、库存、支付、履约和会计的跨上下文写协作已迁移为稳定集成契约。
- 删除旧的库存 ACL 领域事件类型，避免跨上下文继续共享领域事件类。
- 新增 `V20260807__event_delivery_targets.sql`，并同步更新全新数据库初始化脚本。

## 验证证据

- `./gradlew.bat spotlessCheck`：通过（格式修复后由 `spotlessApply` 生成标准格式）。
- `./gradlew.bat :j-store-common-core:test :j-store-common-spring:test :j-store-order:test :j-store-goods:test :j-store-payment:test :j-store-fulfillment:test :j-store-accounting:test :j-store-boot:test --no-daemon --console=plain`：通过。
- `./gradlew.bat test --no-daemon --console=plain`：通过，覆盖全部 Gradle 模块。
- `scripts/check-agent-governance.sh`：通过。
- spec-dev 与 governance Python 契约测试：分别 28 项、5 项通过。
- `scripts/quality-gate.sh` 已执行；前两阶段通过，WSL Bash 环境因未安装 Linux JDK 在第三阶段停止。其第三阶段等价命令已在 Windows JDK 25 下单独完整通过。
- `git diff --check`：通过。

## 残余风险与后续边界

- 公共模块只交付 Broker 出站 SPI，没有绑定具体 Kafka/RabbitMQ adapter；选择 `broker` 或 `hybrid` 前必须提供唯一实现及对应入站 consumer adapter。
- 项目既有库存应用服务尚缺生产 `InventoryRepository`、`ReservationRecordRepository` 和 `InventoryLock` 装配。现在本地库存命令会显式重试/死信，不再被无监听器状态静默标记成功；补齐库存基础设施是独立后续工作。
- 本次不实现 Saga/Process Manager，也不承诺跨数据库 exactly-once。
