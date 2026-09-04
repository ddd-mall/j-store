# 变更需求：购物车目标状态收敛幂等

## 背景

当前 Cart 为每次成功的加购、选择替换和显式刷新永久保存请求回执。该记录没有保留期限或清理生命周期，容量随历史写请求无界增长；重复请求返回的也是当前 Cart，而不是首次响应，因此永久回执并未提供完整的响应重放语义。

本变更由用户批准，以条件目标状态更新替代普通 Cart 写操作的永久请求历史。Checkout、支付、库存预留等会创建独立业务身份或产生外部副作用的流程不在此变更范围内，继续使用各自的持久化幂等机制。

## MODIFIED

### CART-R1 加购绑定 Offer 的 SKU

替代 `docs/spec/cart/requirement.md` 中 CART-R1 的增量加购和请求幂等键要求：

1. Cart 商品写请求 MUST 携带认证买家、`skuId`、`offerId`、`expectedCartVersion` 和绝对目标数量 `targetQuantity`。
2. 当当前 Cart 版本等于 `expectedCartVersion` 时，系统 MUST 将该 Offer 行数量创建或设置为 `targetQuantity`，而不是在现有数量上累加。
3. 当版本不匹配但该 Offer 当前数量已经等于 `targetQuantity` 时，系统 MUST 将请求视为已收敛并成功返回当前 Cart，不得增加版本或重复发布刷新事件。
4. 当版本不匹配且目标尚未满足时，系统 MUST 返回 `Cart.VersionConflict`。
5. 新 Cart 的期望版本为 `0`；目标数量范围仍为 `1..999`，一个 Cart 最多 100 行，Settlement Scope 与 Offer/SKU 可信校验保持不变。

### CART-R4 重新选择

替代 CART-R4 中普通 Cart Selection 的请求回执幂等方式：

1. Selection 请求 MUST 携带 `expectedCartVersion` 和绝对目标行集合。
2. 当前选择已等于目标集合时 MUST 成功返回且不改变版本，即使请求携带的是该目标首次应用前的旧版本。
3. 当前选择不等于目标且版本不匹配时 MUST 返回 `Cart.VersionConflict`。

### CART-R2 显式刷新

显式刷新 MUST 以 `expectedCartVersion` 表达目标版本。同一 `cartId + sourceCartVersion` 已存在 Assessment 时直接返回已有结果；不再为刷新请求保存永久请求回执。

## REMOVED

1. Cart 普通写 API 不再接收 `requestId`。
2. 删除 `CartRequestReceipt` 领域类型、Store、JPA 映射和 `cart_request_receipts` 表。
3. Cart 普通写操作不再承诺重放第一次 HTTP 响应，只承诺目标状态收敛、无重复领域变化和无重复事件。

## 验收与质量目标

1. 相同目标的旧版本重试成功且不增加 `contentVersion`。
2. 不同目标的旧版本请求返回版本冲突。
3. 数量更新使用绝对目标值，不再累加。
4. 重复收敛不产生新的 `CartRefreshRequestedEvent`。
5. 并发写入导致乐观锁冲突时 MUST 使用新事务重试一次，使相同目标收敛成功、不同目标返回版本冲突；失败事务中的 Cart 和 Outbox 写入必须整体回滚。
6. 数据库基线和初始化快照不再创建无界请求回执表。
7. Cart domain、application、boot 和数据库结构相关测试通过；Spotless 检查通过。
8. `TransactionalCartUseCase` 与 `TransactionalCartCheckoutSourceQueryService` 中的 Offer、Catalog、Inventory 查询 MUST 在无数据库事务阶段执行；本地写事务只覆盖聚合保存、Assessment 条件保存和 Outbox 写入。
9. Cart 与 Outbox 已经成功提交后，Assessment 暂时不可用不得使数量或 Selection 命令返回失败；响应允许携带缺失或 `STALE` Assessment，刷新由独立流程收敛。

## 兼容与恢复

项目处于内部开发期，本次直接演进 Cart HTTP 和数据库基线，不保留旧请求字段、旧增量加购端点或双写兼容层。开发环境升级时按仓库策略重建数据库；恢复方式为回退代码和基线后重新初始化开发数据库。

## 验证证据

- Red：新增 Cart 目标数量与 Selection 收敛测试后，领域测试因缺少 `setItemQuantity` 和带版本的 `replaceSelection` 而编译失败；新增应用测试后，Offer 下架场景中的相同目标重试按旧编排失败；新增事务边界回归测试后，数量写入仍同步采集 Assessment 事实，且 boot 尚无分阶段事务操作。
- Green：Cart domain、application、infrastructure、boot 测试及根 `bootJar` 通过，共执行 137 个 Gradle task。
- 全量 Gradle 门禁：`spotlessCheck verifyDependencyResolution licensee test verifyLicenseArtifacts` 通过，共执行 309 个 task。
- 事务边界：boot 测试验证数量设置按 `read -> external -> write`、显式刷新按 `read -> external -> write`、Checkout 准备按 `read -> external` 执行；Spring 实现分别使用 `REQUIRES_NEW`、`NOT_SUPPORTED`、`REQUIRES_NEW`，乐观锁只重试短写阶段。
- 依赖解析：Cart Boot 的 `junit-platform-launcher` 由统一 Platform 解析为 `6.1.2`，没有模块级硬编码版本。
- Spec-dev 28 项、governance 66 项和文件所有权检查通过。
- 完整 Bash `quality-gate.sh` 因 PyPI 下载 `PyYAML` 超时未完成；Windows 下直接执行 tooling 测试的失败来自 POSIX 权限、`termios`、Bash/WSL 路径和临时文件锁限制，与 Cart 变更无关。
