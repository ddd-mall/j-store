# 领域事件批量入箱交付摘要

## 已交付行为

- `DomainEventPublisher` 新增保持输入顺序的批量发布接口，现有单事件实现通过默认方法保持源码兼容。
- `publishPendingEvents` 将稳定事件快照作为一个批次提交，并且只在整个批次返回成功后确认快照。
- `OutboxEventPublisher` 在顺序号分配前完成整批校验和序列化，为每个事件生成独立 Outbox 记录并批量保存。
- `OutboxEntryRepository` 新增批量保存端口，Spring/JPA 实现使用 `JpaRepository.saveAll`。
- `OutboxStreamSequenceAllocator` 新增有序批量分配端口；PostgreSQL 实现按唯一 stream 原子申请连续区间，同一 stream 在一个批次中只执行一次 upsert。
- 空批次为 no-op；relay、重试、死信、幂等和至少一次投递语义未改变。

## 验收证据

- common-core 测试覆盖单次批量调用、成功确认和批量失败保留全部待发布事件。
- Outbox 单元测试覆盖单事件兼容、批量记录顺序、共同入箱时间、序列化失败前不分配序号以及空批次。
- PostgreSQL 集成测试覆盖交错 stream 连续区间、批量保存、并发批次区间不重叠和事务回滚后区间复用。
- `./gradlew.bat :j-store-common-core:test :j-store-outbox-core:test :j-store-outbox-spring:test`：通过。
- 所有使用 `DomainEventPublisher` 的业务 application 模块测试：通过。
- `./gradlew.bat test --no-daemon --console=plain`：通过，190 个任务完成或命中缓存。
- 本次变更文件的定向 `spotlessCheck`：通过。
- `./gradlew.bat licensee verifyLicenseArtifacts`：通过，验证 53 个 JAR 制品。
- `python scripts/check-file-ownership.py`：通过，分类 1166 个仓库文件。

## 门禁限制

`scripts/quality-gate.sh` 已执行，但仓库治理测试会扫描被 Git 忽略的 `.gradle-home` 和各模块 `bin` 生成目录，因此在进入 Gradle 格式、许可和回归步骤前失败。临时移开 `.gradle-home` 后，治理测试继续被 `*/bin/**/*.kt` 生成文件拦截。

另外单独执行全仓库 `spotlessCheck` 时，唯一格式失败是实施前已经存在的用户修改：`j-store-order-application/src/main/kotlin/com/jstore/order/service/OrderService.kt`。本次变更未修改或格式化该文件。上述门禁组成步骤已在不触碰用户修改的前提下分别执行，结果见验收证据。

## 迁移与回滚

本变更不需要数据库迁移。回滚源码即可恢复原有逐条入箱路径，既有 Outbox 数据无需转换。
