---
inclusion: always
---

# TDD Guidelines - j-store

本项目新功能与行为修复必须遵循 TDD：先写能失败的测试，再写最小实现，最后在测试保护下重构。

## 工作流

1. 明确行为边界：先定位所属有界上下文、聚合、应用服务或基础设施适配器。
2. 先写测试：新增业务规则、状态转换、边界条件、回归场景必须先进入测试。
3. 确认失败：运行最小相关测试，确认测试因目标行为缺失而失败。
4. 最小实现：只实现让测试通过的行为，避免顺手扩大范围。
5. 重构整理：在测试通过后再消除重复、调整命名或抽取局部结构。
6. 扩展回归：发现缺陷时先补能复现缺陷的测试，再修复。

## 测试分层

- 领域对象：优先使用快速单元测试，覆盖不变量、状态转换、错误分支、领域事件。
- 值对象：覆盖构造校验、不可变性、序列化/等价性等核心性质。
- 应用服务：使用 fake repository 或 mock ACL，验证编排、失败传播、保存行为和事件触发。
- 基础设施：用窄集成测试覆盖 PO 与领域对象转换、JPA 查询、事务边界、Outbox 持久化。
- Controller/Boot 配置：只覆盖接口契约、认证授权、参数校验和关键装配，不把业务规则写在接口测试里。

## 属性测试

已有测试大量使用 Kotest property。以下场景优先使用属性测试：

- 输入空间大且存在明确不变量
- 值对象校验、格式化、序列化往返
- PO 与领域模型转换往返
- 状态机非法转换、重复操作、边界数量

属性测试必须控制生成器范围，避免随机生成无业务意义的数据导致测试脆弱。

## DDD 与测试约束

- 测试应验证领域语言中的行为，而不是只验证字段 getter/setter。
- 不通过放宽领域模型封装来方便测试。
- 领域测试不得依赖 Spring 容器、数据库、JPA PO 或基础设施实现。
- 应用服务测试可以使用 fake repository，以保持用例测试快速稳定。
- 基础设施测试不得反向要求领域层暴露持久化细节。

## 执行建议

优先运行最小相关模块测试，完成后按影响范围扩大：

```bash
./gradlew :j-store-order-domain:test :j-store-order-application:test :j-store-order-boot:test
./gradlew :j-store-goods-domain:test :j-store-goods-application:test :j-store-goods-boot:test
./gradlew :j-store-accounting-domain:test :j-store-accounting-application:test :j-store-accounting-boot:test
./gradlew test
```

涉及数据库、迁移、JPA Repository、Outbox 或 Spring 装配时，必须运行对应 infrastructure/common-spring/boot 相关测试。

## 完成标准

- 新增或变更的业务行为有测试先行记录。
- 正常路径、关键失败路径、边界条件和回归场景均有覆盖。
- 测试命名能表达业务意图。
- 所有相关测试通过；若因外部环境无法运行，必须记录未验证项和原因。
