# Outbox 低延迟 Relay 需求

## 背景与目标

当前 Outbox 由固定间隔调度器驱动，默认每次执行完成后等待 5 秒。订单确认 Saga 的每一跳都会写入下一条 Outbox 记录，多跳等待会叠加并延迟支付单准备。

本变更在保持现有 at-least-once、顺序、幂等、租约、fencing、重试和死信语义的前提下，让已提交的 Outbox 工作尽快触发后台投递，并对高并发下的执行压力实施固定上限。

## 行为需求

- LR-R1：领域事件或集成消息 Outbox 记录只有在所属事务成功提交后，才能请求 Relay 立即处理；回滚事务不得触发有效投递。
- LR-R2：并发或重复的唤醒请求必须合并，不能按 Outbox 记录或业务请求创建无界后台任务。
- LR-R3：任一应用实例同时最多运行一个本地 drain；周期恢复调度与提交后即时唤醒必须复用同一并发门禁。
- LR-R4：一次 drain 必须连续领取事务执行期间新产生的 ready 记录，以降低多跳本地 Saga 延迟。
- LR-R5：一次 drain 必须受最大批次数限制；达到预算且仍有工作时，应重新请求后续 drain 并让出执行线程。
- LR-R6：没有待处理记录时必须停止 drain，不进行忙等；丢失唤醒、进程重启和外部写入仍由现有周期调度恢复。
- LR-R7：单条消息投递继续使用既有独立 delivery transaction、claim/lease/fencing token、失败重试和同 ordering key 前序屏障。
- LR-R8：即时调度失败不得影响已经提交的业务事务；记录仍保留在 Outbox 中等待周期恢复。

## 配置与兼容性

- 新增正整数配置 `jstore.outbox.max-batches-per-drain`，默认 10。
- 保留 `jstore.outbox.polling-interval` 及其当前默认值，语义调整为恢复扫描周期。
- 不修改 Outbox 表结构、消息契约、运维 API 或公开 HTTP API。
- 不引入 PostgreSQL trigger、LISTEN/NOTIFY、Kafka、Debezium 或新的外部运行时。

## 验收与质量目标

- 事务提交测试证明：提交后触发一次唤醒，回滚不触发，事务内多次发布可被协调器安全合并。
- 并发测试证明：大量并发 signal 只产生有界 executor 提交，并且 drain 不重叠。
- drain 测试证明：能处理调用期间出现的下一批记录，在空批次停止，并在达到批次预算时报告仍需继续。
- Spring 装配测试证明：两个 Outbox publisher 均接入提交信号，scheduler 通过协调器触发恢复处理。
- 相关 Outbox 单元、属性和 PostgreSQL 集成测试通过；质量门禁结果记录在总结中。

## 非范围

- 本次不重构订单 Saga 或新增 Checkout API。
- 不承诺同步完成支付准备；请求线程不等待 Relay。
- 不提高单条消息或单 ordering key 的并行度。
- 不将非事务发布自动提升为可靠发布。
