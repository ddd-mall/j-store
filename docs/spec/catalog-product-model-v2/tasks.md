# Catalog 商品模型 v2 实施计划

## Iteration 1：发布一致性

- [x] T1.1 先添加草稿 SKU 深复制与稳定身份恢复的失败测试。
- [x] T1.2 实现 `sourceSkuId`、深复制和草稿合并 ID 映射。
- [x] T1.3 先添加素材草稿复制、直接编辑拒绝和快照完整性的失败测试。
- [x] T1.4 实现版本化素材编排和快照扩展。
- [x] T1.5 添加发布事件、乐观锁和唯一草稿约束。

## Iteration 2：结构化商品资料

- [x] T2.1 先添加 Product Type 类型、必填、枚举和变体轴校验测试。
- [x] T2.2 实现 Product Type 聚合、仓储端口和持久化映射。
- [x] T2.3 先添加 SKU 修改、删除、状态保护和编码唯一性测试。
- [x] T2.4 实现完整 SKU 草稿管理和发布前校验。

## Iteration 3：内容基础

- [x] T3.1 添加 LocalizedText、MediaAsset、Category、Brand 不变量测试。
- [x] T3.2 实现内容基础领域类型并接入 SPU/快照。
- [x] T3.3 更新数据库基线、初始化快照、公共查询契约和领域建模文档。

## Iteration 4：验证与收敛

- [x] T4.1 运行 Goods domain/application/infrastructure/boot 测试。
- [x] T4.2 运行 Order contract 回归和数据库相关测试。
- [x] T4.3 运行 `./scripts/quality-gate.sh`。
- [x] T4.4 写入 `summary.md`，记录验收映射、命令和残余风险。

## 后续候选

- Bundle/Kit 与商品关系。
- 商品搜索投影、facets 和类目浏览。
- 物流/合规属性模板与第三方平台 schema 映射。
- 商品内容审核、定时发布和版本回滚。
