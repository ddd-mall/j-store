# 购物车实现交付摘要

## 已实现

- 新增 Cart API/domain/application/infrastructure/boot 五个模块，以及 Inventory ATP 只读 API。
- 实现多商户同 Settlement Scope 加购、Offer 合并、Selection 原子替换、内容版本和请求幂等记录。
- 实现 `CartRefreshRequestedEvent`、Catalog/Offer/ATP 批量 ACL、CartAssessment 决策表和基础金额试算。
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

## 剩余验证

Windows 本机仍因 JDK loopback 问题无法启动 Gradle daemon，本次改在目标 Linux 主机完成确定性构建。尚未执行整个仓库的 `scripts/quality-gate.sh`，也未使用真实登录买家数据执行 Cart 加购到 Checkout 的在线端到端场景；合并前仍应补跑完整门禁和带认证的特性 smoke。
