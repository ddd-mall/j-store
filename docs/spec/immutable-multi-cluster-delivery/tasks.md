# 不可变制品与多集群交付任务

- [x] 建立规格和可执行治理测试，覆盖四环境、digest、生产策略和 Secret 边界。
- [x] 将应用 Kubernetes base 改为 OCI 应用镜像，移除 PVC/JAR、内置 Redis 和开发配置。
- [x] 增加 development、integration、canary、production overlay 及生产 PDB/拓扑策略。
- [x] 增加确定性渲染和部署脚本，校验 context、cluster UID、digest 与 server-side dry-run。
- [x] 增加容器候选构建接口和供应链证据契约，确保 build once/promote by digest。
- [x] 删除无引用的旧 `latest + Never` 部署清单并更新运行手册。
- [x] 分离 canary/production 数据库迁移与应用 rollout，并强制高风险目标审批配置。
- [x] 用可执行 fake `kubectl` 测试 context、cluster UID、Secret 和 dry-run/apply 顺序。
- [x] 运行定向测试和 `./scripts/quality-gate.sh`，记录未执行的真实 registry/隧道验证。
