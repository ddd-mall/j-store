# Outbox 低延迟 Relay 任务

- [x] T1 以失败测试固定事务提交后唤醒和回滚不唤醒。（LR-R1、LR-R8）
- [x] T2 以失败测试固定高并发信号合并、单飞执行、结束竞态和 executor 拒绝恢复。（LR-R2、LR-R3、LR-R8）
- [x] T3 以失败测试固定连续排空、空批停止和最大批次预算。（LR-R4～LR-R6）
- [x] T4 实现 transaction-aware signal、relay coordinator、有界 drain 和配置校验。（LR-R1～LR-R8）
- [x] T5 更新 Spring 装配和 scheduler，使即时路径与恢复路径共享 coordinator。（LR-R2、LR-R3、LR-R6）
- [x] T6 运行 Outbox 单元、属性、Spring 和 PostgreSQL 集成测试并修复回归。（LR-R7）
- [x] T7 运行质量门禁，核对规格、实现和证据并完成 summary。（全部）
