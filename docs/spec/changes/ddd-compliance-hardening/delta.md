# DDD 合规性加固 Delta

## 背景

全仓库 DDD 审查发现若干实现与 `docs/steering/ddd-guidelines.md` 不一致：订单快照可由默认值伪造交易事实，领域状态存在公开写入口，Command 承载业务校验，售后 Repository 执行业务分配规则，HTTP Controller 直接决定商户授权，认证 SDK 泄漏 User 上下文的全局 `UserId` 假设，部分跨上下文查询依赖另一个上下文的领域/应用内部模型，且现有治理测试未覆盖全部上下文。

## 目标

- 交易快照中的 Offer、Store、版本、履约节点和渠道必须来自可信输入，不得由领域默认值推断。
- 聚合及其内部实体只能通过领域行为改变状态。
- Command 只承载数据；业务校验由值对象、Factory、聚合或领域策略负责。
- Repository 只提供聚合持久化和并发访问能力，不决定退款额度等业务规则，也不同时持久化两个聚合。
- 所有入站适配器通过应用用例执行一致的身份与商户授权策略。
- 认证入口使用认证域与域内账号 ID 组成的 `AuthenticatedPrincipal`；裸数字 ID 不表示跨认证域身份。
- 跨上下文同步查询只依赖发布的 `-api` 契约，消费方通过 ACL/适配器转换为本地语言。
- 治理测试覆盖全部活跃业务上下文及构造参数形式的公开可变状态。

## 非目标

- 不改变现有订单、支付、履约、售后状态机的业务结果。
- 不引入远程 Site 路由、跨服务事务或兼容旧内部构造参数的默认值。
- 不执行生产迁移或部署。

## 验收标准

1. Order 的 Offer、Store、Offer 版本、履约节点和渠道字段均为显式必填，并有回归测试证明不会回退为 SKU/固定值。
2. domain 主代码不存在可从模块外直接赋值的领域状态属性。
3. domain Command 类型不声明业务行为方法。
4. AfterSale 退款容量判断由领域对象完成；持久化适配器只执行锁定、映射和保存。
5. Merchant 与初始 Owner Membership 由各自 Repository 在同一应用事务内保存。
6. Payment、Fulfillment 与 AfterSale 的 HTTP Controller 不直接依赖 Shop 的领域或应用类型；授权由应用层端口和用例执行。
7. Accounting 不直接依赖 Order domain；Payment 不反向依赖 Trade application；跨上下文查询使用发布 API。
8. Category、Cart Assessment/Receipt、SPU Snapshot 的 Aggregate/Entity/Projection 语义与 Repository 规则一致。
9. Authentication SDK 不依赖 User domain；访问令牌校验与会话状态通过认证端口表达，并有同号账号跨认证域隔离回归测试。
10. 扩展后的治理测试、相关模块测试、格式检查及仓库质量门禁通过；任何环境限制必须显式记录。

## 风险与恢复

这是内部开发期的直接契约收敛，仓库内调用方与测试在同一变更中更新，不保留旧参数和旧依赖兼容层。若合并前发现行为偏差，恢复方式为撤销本分支提交；不修改保护分支或生产数据。
