# Outbox 生产化加固任务

- [x] T1 修复 dead-letter requeue 重试预算，先补失败回归测试，再实现并验证可重新 claim。（R4）
- [x] T2 新增兼容 Flyway migration、fencing token 与死信审计模型。（R5、R7、R9、R10）
- [x] T3 实现确定性同聚合有序 claim、token 校验、续租与预取上限。（R1、R2、R5、R6）
- [x] T4 补真实 Flyway/PostgreSQL 并发、崩溃恢复、fencing、顺序和事务 E2E 测试。（R14、R15）
- [x] T5 扩展 lag、过期锁、scheduler 健康度与阈值告警指标。（R11-R13）
- [x] T6 实现死信审计、管理员 allowlist 和受保护的运维查询/requeue API，并补权限测试。（R7、R8、R16）
- [x] T7 更新投递语义文档，运行相关模块及全量回归，独立评审并记录残余风险。（R1-R16）
