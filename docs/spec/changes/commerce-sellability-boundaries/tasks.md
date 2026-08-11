# 实施任务：销售资格、ATP 与仓储边界重构

- [x] 以失败测试定义 Catalog、SalesOffer、ATP、WMS 版本同步和 Order Saga 行为。
- [x] 将 goods 收敛为 Catalog 生命周期，删除 SPU 销售许可和库存职责。
- [x] 在 shop 四层模块实现 Store、SalesOffer 与 SaleAuthorization。
- [x] 建立 Inventory/ATP 四层模块并迁移库存、预留、确认、释放职责。
- [x] 将 warehouse 骨架重组为 WMS 四层模块并发布版本化实物库存事实。
- [x] 演进集成契约、订单快照和 Saga 状态，完成失败补偿及支付时序。
- [x] 添加迁移、Boot 装配、模块治理和文档更新。
- [x] 运行相关测试和完整质量门禁，记录证据与残余风险。
- [ ] 由非实现者完成高风险独立评估与人工批准。
