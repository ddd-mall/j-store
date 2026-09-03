# DDD 合规性加固验证摘要

## 已实现结果

- Order 交易快照删除 Offer、Store、Offer 版本、履约节点与渠道的伪造默认值，领域状态改为只读暴露。
- Command 收敛为数据载体，订单、商品与售后校验由领域 Validator、Factory、聚合和值对象承担。
- 售后退款额度由独立 `RefundCapacity` 聚合维护；Repository 只负责锁定、映射与持久化。
- Merchant 与初始 Membership 分别通过各自 Repository 保存，由应用事务保证原子性。
- Payment、Fulfillment、AfterSale 的商户授权移入应用用例；越权资源统一按不存在返回。
- 新增 `order-api`、`payment-api` 与商户授权查询契约，消除 Boot 模块对其它上下文 Domain/Application 内部模型的依赖。
- 认证主体改为“认证域 + 域内账号 ID”；Authentication SDK 不再依赖 User domain，同号跨认证域售后访问有回归保护。
- Category 收敛为稳定引用实体；SPU Snapshot、Cart Assessment/Receipt 按历史快照、派生试算与幂等回执语义使用专用 Store。

## 验证证据

- Windows 新 worktree：`python -m unittest discover -s tests/governance -p "test_*.py"`，66 项通过。
- 隔离开发环境：Gradle 全量测试 710 项，0 失败、0 错误、0 跳过。
- 开发机：`spotlessCheck`、`verifyDependencyResolution`、`licensee`、`test`、`verifyLicenseArtifacts` 联合执行成功；63 个 runtime classpath 的安全版本约束通过，66 个 JAR 的 Apache-2.0 制品许可证通过。
- `./scripts/quality-gate.sh` 的仓库治理、spec-dev 与 DDD governance 阶段通过；tooling 阶段 349 项中 4 项因开发机既有 Agentic CI/CD 主机基线失败而提前停止：Node 运行时未被打包器发现，以及 `/opt/jstore-agentic-controller` 的 repository image binding / required-checks 权威配置与当前仓库不一致。该基线与本变更文件无交集，脚本被提前跳过的 Gradle 阶段已按上一条独立执行通过。

## 回滚

本项目处于内部开发期，未引入兼容层或生产迁移。合并前可直接撤销本变更提交；合并后按同一提交反向恢复模块依赖和领域契约。
