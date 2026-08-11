# 领域事件批量入箱设计

## 接口兼容

`DomainEventPublisher` 保留抽象单事件方法，并新增具有逐条兼容默认实现的 `publishEvents(events)`。生产 Outbox 实现覆盖批量方法；单事件实现委托给单元素批次。这样仓库内现有匿名实现保持源码兼容，同时聚合路径获得真正的批量入口。

## 批量构造

`OutboxEventPublisher` 按以下阶段处理：

1. 对稳定输入列表完成类型注解、metadata 和注册类型校验，并完成序列化。
2. 根据每个事件的聚合引用生成 ordering key。
3. 按输入 stream 列表申请 sequence number。
4. 使用同一个入箱时间构造独立 Outbox 记录。
5. 调用仓储批量保存。

校验和序列化先于数据库顺序号分配，避免确定性坏事件推进 stream position。所有数据库操作仍要求调用方已有事务。

## 批量顺序号

核心端口新增带默认逐条实现的 `nextSequences(streams)`。PostgreSQL 实现先按 stream 统计批次数量，再对每个唯一 stream 原子增加相应数量并取得结束序号，最后按照原输入顺序展开连续区间。

一次批次包含多个相同 stream 时只执行一次该 stream 的 upsert。不同事务并发申请同一 stream 时依赖 PostgreSQL upsert 行锁保证区间不重叠。事务回滚同时回滚 stream position 和 Outbox 记录。

## 持久化

`OutboxEntryRepository` 新增兼容默认 `saveAll`；Spring/JPA 实现覆盖为 `JpaRepository.saveAll`。该变更建立批量持久化边界，但具体 JDBC 合批仍由部署环境的 Hibernate batch 配置决定。

## 回滚

本变更没有数据库迁移。候选失败时可整体回滚源码；既有 Outbox 数据无需处理。
