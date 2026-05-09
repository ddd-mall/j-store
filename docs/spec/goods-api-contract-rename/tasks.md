# 实现计划：goods-api-contract-rename

## 概述

本计划将商品 provider 侧快照查询契约从 `j-store-goods-acl` / `com.jstore.goods.acl` 迁移为 `j-store-goods-api` / `com.jstore.goods.api`，并更新商品应用、订单基础设施、启动装配和相关测试中的依赖与 import。实施顺序遵循 DDD 分层和依赖方向：先迁移独立发布契约模块，再更新商品应用实现，再更新订单基础设施适配器，最后更新启动装配和验证任务。

技术约束：使用现有 Gradle Kotlin DSL、Kotlin/JVM 21、Kotest/Mockito 测试栈；不新增远程调用协议、不修改数据库、不改变快照查询字段语义；不迁移 `com.jstore.goods.acl.event`、`OssService`、`com.jstore.order.acl.GoodsService`，也不让 `j-store-order` 直接依赖 `j-store-goods-api`。

## Tasks

- [x] 1. 迁移商品 provider 发布契约模块
  - [x] 1.1 创建 `j-store-goods-api` 模块目录并迁移构建脚本
    - 在 `j-store-goods-api/build.gradle.kts` 中创建
    - 以现有 `j-store-goods-acl/build.gradle.kts` 为基础保留 `plugins { alias(libs.plugins.kotlin.jvm) }`、`repositories { mavenCentral() }`、`api(libs.kotlin.stdlib)`、`api(project(":j-store-common-core"))`、`tasks.withType<Test> { useJUnitPlatform() }`、`kotlin { jvmToolchain(21) }`
    - 预期结果：`:j-store-goods-api` 提供与旧快照契约模块相同的 common-core 依赖表面，不包含 Spring Bean、JPA 或消费方 ACL 类型
    - _需求: 1.1, 1.3, 2.1_
  - [x] 1.2 创建商品 API 快照查询契约源码
    - 在 `j-store-goods-api/src/main/kotlin/com/jstore/goods/api/GoodsSnapshotQueryService.kt` 中创建
    - 声明 `package com.jstore.goods.api`
    - 保留 `import com.jstore.common.properties.Price`
    - 定义 `interface GoodsSnapshotQueryService { fun queryLatestSnapshots(spuIds: List<Long>): List<GoodsSnapshotInfo> }`
    - 定义 `data class GoodsSnapshotInfo(val spuId: Long, val snapshotVersion: Long, val spuName: String, val skuSnapshots: List<GoodsSkuSnapshotInfo>)`
    - 定义 `data class GoodsSkuSnapshotInfo(val skuId: Long, val skuName: String, val attributes: List<Pair<String, String>>, val price: Price)`
    - 预期结果：快照契约字段、类型和查询方法签名保持不变，仅模块名和包名变为 goods api
    - _需求: 1.3, 2.1, 5.4_
  - [x] 1.3 更新 Gradle settings 模块注册
    - 在 `settings.gradle.kts` 中修改
    - 将 `include("j-store-goods-acl")` 替换为 `include("j-store-goods-api")`
    - 不保留 `include("j-store-goods-acl")` alias 或旧模块入口
    - 预期结果：Gradle 只注册 `:j-store-goods-api` 作为商品 provider 快照契约模块
    - _需求: 1.1, 1.2_
  - [x] 1.4 移除旧快照契约模块入口
    - 在 `j-store-goods-acl/src/main/kotlin/com/jstore/goods/acl/GoodsSnapshotQueryService.kt` 和 `j-store-goods-acl/build.gradle.kts` 所在旧模块路径中处理
    - 删除或迁出旧 `GoodsSnapshotQueryService`、`GoodsSnapshotInfo`、`GoodsSkuSnapshotInfo` 声明，确保 `com.jstore.goods.acl` 不再承载 provider 侧快照查询契约
    - 不创建 `com.jstore.goods.acl` 到 `com.jstore.goods.api` 的桥接类或 typealias
    - 预期结果：旧 goods acl 模块不再作为项目依赖入口，旧包不再声明 provider 快照契约
    - _需求: 1.2, 2.2_
  - [x] 1.5 更新商品应用模块依赖
    - 在 `j-store-goods/build.gradle.kts` 中修改
    - 将 `api(project(":j-store-goods-acl"))` 替换为 `api(project(":j-store-goods-api"))`
    - 保留现有 `api(project(":j-store-common-core"))`、Kotest、Mockito 和 Kotlin 测试依赖
    - 预期结果：`j-store-goods` 通过新 goods api 模块实现并对上层暴露快照查询契约
    - _需求: 1.2, 1.3, 5.1_

- [x] 2. 更新商品应用服务对发布契约的实现
  - [x] 2.1 修改 `CommodityService` 的 goods api imports 和接口实现
    - 在 `j-store-goods/src/main/kotlin/com/jstore/goods/service/CommodityService.kt` 中修改
    - 将 `com.jstore.goods.acl.GoodsSnapshotInfo`、`com.jstore.goods.acl.GoodsSnapshotQueryService`、`com.jstore.goods.acl.GoodsSkuSnapshotInfo` imports 替换为 `com.jstore.goods.api.*` 对应类型
    - 保持 `class CommodityService(...): GoodsSnapshotQueryService` 的接口实现关系
    - 保持 `override fun queryLatestSnapshots(spuIds: List<Long>): List<GoodsSnapshotInfo>` 方法签名不变
    - 保持查询逻辑：`spuIds.distinct()` 后调用 `snapshotRepository.findLatestBySpuId(SpuId(spuId))`，缺失快照过滤，存在快照映射为 `GoodsSnapshotInfo` 和 `GoodsSkuSnapshotInfo`
    - 预期结果：商品应用层实现 `com.jstore.goods.api.GoodsSnapshotQueryService`，快照查询行为等价
    - _需求: 2.3, 5.1, 5.4_
  - [x] 2.2 保留商品侧非目标 ACL 包现状
    - 在 `j-store-goods/src/main/kotlin/com/jstore/goods/acl/OssService.kt` 和 `j-store-goods/src/main/kotlin/com/jstore/goods/acl/event/*.kt` 中不做迁移
    - 保留 `j-store-goods/src/main/kotlin/com/jstore/goods/service/InventoryEventHandler.kt`、`InventoryConfirmEventHandler.kt`、`InventoryReleaseEventHandler.kt` 对 `com.jstore.goods.acl.event` 的引用
    - 预期结果：本需求只迁移快照查询 provider 契约，不扩大到库存事件或 OSS 服务命名
    - _需求: 2.2, 5.2, 5.3_

- [x] 3. 更新订单基础设施适配器
  - [x] 3.1 更新订单基础设施模块依赖
    - 在 `j-store-order-infrastructure/build.gradle.kts` 中修改
    - 将 `implementation(project(":j-store-goods-acl"))` 替换为 `implementation(project(":j-store-goods-api"))`
    - 保持 `api(project(":j-store-order"))`，不要把 goods api 依赖提升到 `j-store-order`
    - 预期结果：只有订单基础设施层依赖商品 provider 发布契约并承担适配职责
    - _需求: 1.2, 4.1, 4.3_
  - [x] 3.2 修改 `GoodsServiceImpl` 的 goods api import
    - 在 `j-store-order-infrastructure/src/main/kotlin/com/jstore/order/acl/GoodsServiceImpl.kt` 中修改
    - 将 `import com.jstore.goods.acl.GoodsSnapshotQueryService` 替换为 `import com.jstore.goods.api.GoodsSnapshotQueryService`
    - 保持 `class GoodsServiceImpl(private val goodsSnapshotQueryService: GoodsSnapshotQueryService) : GoodsService`
    - 保持 `override fun queryGoods(goodsId: List<GoodsId>): List<GoodsInfo>` 方法签名不变
    - 保持适配逻辑：输入 `GoodsId` 提取去重 `spuId`，调用 `queryLatestSnapshots(spuIds)`，按 `spuId` 和 `skuId` 匹配快照，只返回能匹配的订单本地 `GoodsInfo`
    - 预期结果：订单基础设施消费 `com.jstore.goods.api` DTO，并只向订单领域暴露 `com.jstore.order.acl.GoodsInfo`
    - _需求: 2.3, 4.3, 4.4_
  - [x] 3.3 确认订单领域 ACL 端口不迁移
    - 在 `j-store-order/src/main/kotlin/com/jstore/order/acl/GoodsService.kt` 中保持现状
    - 保持 `package com.jstore.order.acl`
    - 保持 `interface GoodsService { fun queryGoods(goodsId: List<GoodsId>): List<GoodsInfo> }`
    - 保持 `data class GoodsId(val spuId: Long, val skuId: Long)` 和 `data class GoodsInfo(...)` 字段语义
    - 不引入 `com.jstore.goods.api` import，不移动到商品上下文
    - 预期结果：订单领域继续通过消费方本地 ACL 表达商品能力需求
    - _需求: 3.1, 3.2, 3.3, 4.2_

- [x] 4. 更新启动装配引用
  - [x] 4.1 修改 `OrderBootConfiguration` 的 goods api import
    - 在 `j-store-boot/src/main/kotlin/com/jstore/order/config/OrderBootConfiguration.kt` 中修改
    - 将 `import com.jstore.goods.acl.GoodsSnapshotQueryService` 替换为 `import com.jstore.goods.api.GoodsSnapshotQueryService`
    - 保持 `@Configuration`、`@Bean fun goodsService(goodsSnapshotQueryService: GoodsSnapshotQueryService): GoodsService`
    - 保持返回 `GoodsServiceImpl(goodsSnapshotQueryService)`
    - 不修正该文件既有 package 命名问题，避免扩大范围
    - 预期结果：启动装配使用新 provider API 契约类型注入订单 ACL 实现
    - _需求: 2.3, 6.1_

- [x] 5. 更新相关测试引用
  - [x] 5.1 修改商品快照查询测试中的 provider DTO 引用
    - 在 `j-store-goods/src/test/kotlin/com/jstore/goods/service/CommodityServiceGoodsSnapshotQueryTest.kt` 中修改
    - 将测试中的 `com.jstore.goods.acl.GoodsSkuSnapshotInfo` 引用改为 `com.jstore.goods.api.GoodsSkuSnapshotInfo`，必要时新增 import
    - 将测试名称中的 “published ACL DTO” 调整为 “published API DTO” 或等价表述，避免旧语义继续扩散
    - 保持断言字段：`spuId`、`snapshotVersion`、`spuName`、`skuId`、`skuName`、`attributes`、`price`
    - 预期结果：测试验证迁移后的 goods api DTO 映射，同时不改变快照查询行为
    - _需求: 2.1, 2.3, 5.4, 6.2_
  - [x] 5.2 修改订单基础设施测试中的 goods api 引用
    - 在 `j-store-order-infrastructure/src/test/kotlin/com/jstore/order/acl/GoodsServiceImplTest.kt` 中修改
    - 将 `com.jstore.goods.acl.GoodsSnapshotInfo`、`GoodsSnapshotQueryService`、`GoodsSkuSnapshotInfo` imports 替换为 `com.jstore.goods.api.*` 对应类型
    - 保持匿名 `GoodsSnapshotQueryService` 测试桩和 `queryLatestSnapshots(spuIds: List<Long>): List<GoodsSnapshotInfo>` 签名
    - 保持断言：`capturedSpuIds shouldBe listOf(1001L)`，重复匹配输入返回两个订单本地 `GoodsInfo`，缺失 SKU 被过滤
    - 预期结果：订单基础设施测试验证 goods api DTO 到订单本地 ACL DTO 的转换
    - _需求: 4.3, 4.4, 6.2_

- [x] 6. 验证任务
  - [x] 6.1 编写属性测试：新 goods api 模块是唯一 provider 快照契约入口
    - **Property 1: 新 goods api 模块是唯一 provider 快照契约入口**
    - 在 `docs/spec/goods-api-contract-rename/tasks.md` 对应实施验证中执行静态扫描，或在现有测试基础上补充构建文件扫描验证
    - 测试策略：运行 `rg -n 'project\\(":j-store-goods-acl"\\)|include\\("j-store-goods-acl"\\)' --glob '*.kts'`，断言没有旧模块 include 或 dependency declaration；同时确认 `settings.gradle.kts`、`j-store-goods/build.gradle.kts`、`j-store-order-infrastructure/build.gradle.kts` 使用 `j-store-goods-api`
    - **验证: 需求 1.1, 1.2, 1.3**
  - [x] 6.2 编写属性测试：provider 快照契约只存在于 goods api 包
    - **Property 2: provider 快照契约只存在于 goods api 包**
    - 在 `j-store-goods-api/src/main/kotlin/com/jstore/goods/api/GoodsSnapshotQueryService.kt`、`j-store-goods/src/main/kotlin/com/jstore/goods/service/CommodityService.kt`、`j-store-order-infrastructure/src/main/kotlin/com/jstore/order/acl/GoodsServiceImpl.kt`、`j-store-boot/src/main/kotlin/com/jstore/order/config/OrderBootConfiguration.kt` 和相关测试文件中验证
    - 测试策略：运行 `rg -n 'com\\.jstore\\.goods\\.acl\\.(GoodsSnapshotQueryService|GoodsSnapshotInfo|GoodsSkuSnapshotInfo)|package com\\.jstore\\.goods\\.acl$' --glob '*.kt'`，断言快照契约旧包声明和旧包 import 不存在；允许 `com.jstore.goods.acl.event` 和 `OssService` 留存
    - **验证: 需求 2.1, 2.2, 2.3, 6.1, 6.2**
  - [x] 6.3 编写属性测试：订单本地 ACL 端口保持稳定
    - **Property 3: 订单本地 ACL 端口保持稳定**
    - 在 `j-store-order/src/main/kotlin/com/jstore/order/acl/GoodsService.kt` 和订单领域相关测试中验证
    - 测试策略：静态检查 `GoodsService.kt` 仍声明 `package com.jstore.order.acl`、`interface GoodsService`、`fun queryGoods(goodsId: List<GoodsId>): List<GoodsInfo>`；确认没有被移动、重命名或替换为 `GoodsSnapshotQueryService`
    - **验证: 需求 3.1, 3.2, 3.3, 4.2**
  - [x] 6.4 编写属性测试：订单领域不直接依赖商品发布 API
    - **Property 4: 订单领域不直接依赖商品发布 API**
    - 在 `j-store-order/build.gradle.kts` 和 `j-store-order/src/main` 中验证
    - 测试策略：运行 `rg -n 'com\\.jstore\\.goods\\.api|j-store-goods-api' j-store-order/src/main j-store-order/build.gradle.kts`，断言无匹配；确认 goods api 只出现在 `j-store-order-infrastructure` 适配层
    - **验证: 需求 4.1, 4.2, 4.3**
  - [x] 6.5 编写 `GoodsServiceImpl` 单元测试
    - 在 `j-store-order-infrastructure/src/test/kotlin/com/jstore/order/acl/GoodsServiceImplTest.kt` 中修改
    - 覆盖场景：输入包含重复 `GoodsId`、缺失 SKU、同一 SPU 多次出现；goods api 返回 `GoodsSnapshotInfo` / `GoodsSkuSnapshotInfo`；断言输出为订单本地 `GoodsInfo` 且字段 `snapshotVersion`、`spuName`、`skuName`、`attributes`、`price` 完全映射
    - _需求: 4.3, 4.4, 6.2_
  - [x] 6.6 编写属性测试：订单基础设施完成 provider DTO 到本地 ACL DTO 的转换
    - **Property 5: 订单基础设施完成 provider DTO 到本地 ACL DTO 的转换**
    - 在 `j-store-order-infrastructure/src/test/kotlin/com/jstore/order/acl/GoodsServiceImplTest.kt` 中补充或保留现有测试
    - 测试策略：使用包含匹配 SPU/SKU、缺失 SKU、重复输入的测试数据；断言 `GoodsService.queryGoods` 返回类型为订单本地 `GoodsInfo`，不在接口签名中暴露 `GoodsSnapshotInfo` 或 `GoodsSkuSnapshotInfo`
    - **验证: 需求 4.3, 4.4**
  - [x] 6.7 编写属性测试：商品应用实现发布契约且不反向依赖订单
    - **Property 6: 商品应用实现发布契约且不反向依赖订单**
    - 在 `j-store-goods/src/main/kotlin/com/jstore/goods/service/CommodityService.kt` 和 `j-store-goods/src/main/kotlin/com/jstore/goods/domain` 中验证
    - 测试策略：确认 `CommodityService` 实现 `com.jstore.goods.api.GoodsSnapshotQueryService`；运行 `rg -n 'com\\.jstore\\.order|com\\.jstore\\.order\\.acl\\.GoodsService' j-store-goods/src/main/kotlin/com/jstore/goods/domain`，断言商品领域层没有订单上下文或消费方 ACL import
    - **验证: 需求 5.1, 5.2, 5.3**
  - [x] 6.8 编写 `CommodityService` 单元测试
    - 在 `j-store-goods/src/test/kotlin/com/jstore/goods/service/CommodityServiceGoodsSnapshotQueryTest.kt` 中修改
    - 覆盖场景：输入 SPU ID 包含重复项和不存在项；`SpuSnapshotRepository.findLatestBySpuId(SpuId)` 返回存在快照或 `null`；断言只返回存在快照的 `GoodsSnapshotInfo`，并完整映射 `GoodsSkuSnapshotInfo` 字段
    - _需求: 2.1, 5.1, 5.4, 6.2_
  - [x] 6.9 编写属性测试：快照查询行为保持等价
    - **Property 7: 快照查询行为保持等价**
    - 在 `j-store-goods/src/test/kotlin/com/jstore/goods/service/CommodityServiceGoodsSnapshotQueryTest.kt` 中补充或保留现有测试
    - 测试策略：生成或构造包含多个 `SpuSnapshot`、多个 `SkuSnapshot`、重复 SPU ID、缺失快照的输入；断言迁移后返回的 `GoodsSnapshotInfo` 和 `GoodsSkuSnapshotInfo` 字段值与迁移前语义一致，仅 Kotlin 包名变化
    - **验证: 需求 1.3, 5.4**
  - [x] 6.10 执行编译、装配和相关测试验证
    - **Property 8: 构建、装配和相关测试通过**
    - 在根目录执行 Gradle 验证
    - 测试策略：运行 `./gradlew --no-daemon :j-store-goods:compileKotlin :j-store-order-infrastructure:compileKotlin :j-store-boot:compileKotlin`，再运行 `./gradlew --no-daemon :j-store-goods:test :j-store-order:test :j-store-order-infrastructure:test`
    - 预期结果：新模块解析成功，`com.jstore.goods.api` imports 编译通过，`OrderBootConfiguration.goodsService(GoodsSnapshotQueryService)` 装配签名编译通过，相关测试无回归
    - **验证: 需求 6.1, 6.2, 6.3, 6.4**

- [x] 7. 检查点 — 模块、包名与边界迁移完成
  - 确保所有测试通过，如有问题请向用户确认。

- [x] 8. 检查点 — 范围排除项确认完成
  - 确保 `com.jstore.goods.acl.event`、`OssService`、`com.jstore.order.acl.GoodsService` 未被误迁移，如有问题请向用户确认。

## 备注

- 标记为 `*` 的任务表示可选任务；当前计划没有可选任务，全部任务都属于本需求完成条件。
- 每个实现和验证任务都引用了 requirement.md 中的需求编号，便于逐项评审和追踪。
- 检查点用于在逻辑边界处确认编译、测试、静态扫描和范围控制结果，发现边界或范围问题时应先向用户确认。
- 验证任务单独列在第 6 组；其中 Property 1、2、4、6 主要通过静态扫描验证架构边界，Property 5、7 通过现有单元测试/属性测试验证行为等价，Property 8 通过 Gradle 编译和相关测试验证集成结果。
- 架构约定：订单领域模块 `j-store-order` 只依赖本地 `com.jstore.order.acl.GoodsService`；订单基础设施模块 `j-store-order-infrastructure` 负责适配 `com.jstore.goods.api`；商品应用模块 `j-store-goods` 实现 provider 发布契约；商品领域层不得反向依赖订单上下文。
