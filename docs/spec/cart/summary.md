# 购物车实现交付摘要

## 已实现

- 新增 Cart API/domain/application/infrastructure/boot 五个模块，以及 Inventory ATP 只读 API。
- 实现多商户同 Settlement Scope 的 Offer 目标数量设置、Selection 原子替换、内容版本和目标状态收敛幂等；普通 Cart 写入不再永久保存请求历史。
- 实现 `CartRefreshRequestedEvent`、Catalog/Offer/ATP 批量 ACL、CartAssessment 决策表和基础金额试算。
- Cart 的 Offer/事实采集已与数据库事务分离；数量设置、显式刷新和 Checkout 准备使用分阶段短事务编排。
- 实现 Cart JPA 持久化、Flyway/初始化结构、认证 HTTP API 和买家隔离。
- 扩展 Trade Checkout 支持 Cart 来源，冻结 Cart ID/版本/摘要，并保持按商户与履约节点拆分、金额守恒和既有失败补偿。
- 增加多商户三商品形成一个 Trade、两个计划及两个订单的测试，以及 Cart 领域规则测试。

## 验证证据

- `git diff --check` 和新增文件尾随空白检查通过。
- 在 Linux/JDK 25 上执行 Cart domain/application/infrastructure/boot、Trade application/boot 相关测试及 `:j-store-boot:bootJar`，Gradle 构建成功，共执行 133 个 task。
- 修复 Cart Boot 缺少 Goods、Shop、Inventory API 直接依赖的问题，真实 Gradle 编译通过。
- 恢复已经部署的 `V20260809`、`V20260814` 迁移原文，新增 `V20260814.1__trade_saga_evolution.sql` 向前演进 Trade Saga，未执行 Flyway repair，也未删除旧交易数据。
- development 集群成功校验 15 个迁移并执行 `20260814.1` 与 `20260815`，数据库到达 `v20260815`；重启后 Flyway 报告 schema 已是最新。
- Kubernetes `jstore/j-store` revision 13 运行 OCI digest `sha256:9785eb808242835f843a9882b1f4ef8afaf1f5a22586ceee50b86123517a382a`，Deployment 为 `1/1 Ready`，Pod 零重启。
- Actuator 返回 `UP`，未认证访问 `/api/carts/current` 返回 HTTP 401。
- 本次提交未包含工作区中已有的认证 SDK、旧 `.kiro` DDD 指南和 Gradle 审计文档改动。
- GitHub PR #56 在提交 `9e417998` 上完整执行 `scripts/quality-gate.sh` 并通过；branch policy、Qodana、静态分析、许可证审计和 secret scan 同时通过。
- CI 首轮发现的新增 Kotlin 文件 SPDX 头和 Spotless 格式问题已经修复；Linux `spotlessCheck` 与文件所有权治理测试通过。

## 剩余验证

Windows 本机仍因 JDK loopback 问题无法启动 Gradle daemon，本次改在 Linux 主机和 GitHub Actions 完成确定性验证。尚未使用真实登录买家数据执行 Cart 加购到 Checkout 的在线端到端场景。

PR 的依赖漏洞门禁当前被既有 Alpine 3.24 基础镜像中的 `openssl/libcrypto3 3.5.7-r0` 阻塞；Gradle SBOM 无漏洞发现。基础镜像升级按仓库供应链规则应独立评估和提交，不能在 Cart PR 中顺带修改或绕过门禁。

已部署 OCI 来自格式化和 SPDX 修复前的同语义候选，因此运行行为与本 PR 一致，但不与最终 Git 提交保持字节级同一；后续晋级应从最终获批提交重新形成不可变候选。

## 业务字面量重构验证（2026-09-15）

- 初次将数量范围和行数限制集中命名；该方案随后被下一节的配置注入方案替代。
- 刷新原因改用 `CartRefreshReason`，应用查询状态改用 `CartAssessmentViewStatus`，保持原 JSON 字段和值。
- 重构前先补数量边界、满车更新及当前/过期试算回归用例，运行 domain/application 测试通过，建立现有行为基线。
- 重构后购物车 domain/application/infrastructure/boot 测试通过，包含新增响应状态序列化及两种刷新原因 JSON 往返验证。
- `./gradlew spotlessApply` 和 `./scripts/quality-gate.sh` 通过：治理与规格/工具测试、格式、依赖解析、许可证审计、全仓库 Gradle 回归及 66 个 JAR 许可证验证均成功。Gradle 复用了未受影响任务的缓存或 up-to-date 结果。
- 本次为保持行为的内部类型重构，无数据库变更；独立人工审查及远端 CI 尚未执行。恢复可撤销本次候选 diff。

## 配置注入与 Nacos 动态刷新（2026-09-16）

- `CartLimitsProperties` 按 Starter 属性初始化方式集中提供 1、999、100 的缺省值；配置可部分覆盖。领域 `CartLimits` 和应用 `CartLimitsProvider` 均无 Spring/Nacos 依赖，领域调用显式接收策略。
- Nacos SDK 读取与监听独立 properties 文档，合法更新原子替换不可变快照；无效、删除及空文档保留最近有效快照。一次数量请求的检查、提交和乐观锁重试共用一个快照。
- 用户明确授权接入 Nacos；适用规格为 `docs/spec/changes/cart-configurable-limits/delta.md`。数据库基线改为只校验正数量，未触碰现有数据库。
- Red：领域自定义策略测试先因缺少类型/参数编译失败；PostgreSQL 测试先因数量 1500 违反旧 CHECK 失败；HTTP 安全约束治理测试先因缺失版本策略失败。
- Green：Cart 四模块和主 Boot 测试、完整 `./scripts/quality-gate.sh` 通过；覆盖默认/部分覆盖、非法启动配置、监听回调、保留旧快照、事务重试快照、数量 1500 入库及零数量拒绝。全仓库回归 236 个任务，117 执行、119 up-to-date；66 个 JAR 许可证验证通过。
- 生产 SBOM 成功生成，共 227 个包；dependencyInsight 确认 Nacos 3.2.4 来自统一 Platform，新增 HTTP Components 修复约束解析为 httpclient5 5.6.3、httpcore5/httpcore5-h2 5.4.3。
- 同步 develop ecce81c5（包含 Netty 4.1.137.Final 修复）后，完整质量门禁再次通过。使用发布 SHA-256 验证的 OSV Scanner 2.4.0（与 CI 相同版本）重新扫描 227 个生产依赖包：退出码 0、无漏洞发现；此前 HTTP Components 和 Netty 公告均已消除。
- Nacos 适配器测试使用官方 ConfigService 的 mock 驱动监听回调，尚未进行真实 Nacos 服务端联调及独立审查。远端 CI 状态以 PR 最新提交检查为准。启动和配置方式见 README 的“购物车数量限制与 Nacos”。
