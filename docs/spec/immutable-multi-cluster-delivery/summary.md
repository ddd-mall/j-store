# 不可变制品与多集群交付完成摘要

## 已交付

- 一个 Git 提交通过 `build-oci-candidate.sh` 构建并推送一次带 SBOM/provenance 的 OCI 候选，
  后续环境只消费完整 `repository@sha256:digest`。
- Kubernetes 应用收敛为一个不创建 namespace、Secret、数据库或 Redis 的 base，以及
  development、integration、canary、production 四个 overlay。
- 渲染和部署入口拒绝 tag、绑定显式 context、严格 cluster UID、`jstore` namespace 和
  外部 `jstore-runtime` ConfigMap/Secret，并在 apply 前执行 server-side dry-run。
- canary/production 使用双副本、RollingUpdate、PDB、拓扑分散、优雅终止和受控入口
  NetworkPolicy；development 保留显式 `local` 开发适配器，PVC/JAR 交付路径已经移除。
- canary/production 通过容器显式环境变量关闭启动时 Flyway，数据库迁移必须走独立审批；
  目标 schema 强制高风险环境审批并只保留无 tag、无凭据的完整 repository。
- CI 安全门禁生成带最大 provenance 和 SBOM attestation 的 OCI archive，并用固定版本 OSV
  Scanner 扫描最终容器包；Corretto 25 基础镜像已固定到 OCI image-index digest。

## 验证证据

- `python3 -m unittest tests.tooling.test_immutable_multi_cluster_delivery tests.tooling.test_kubernetes_application_deployment`：20 项通过。
- `python3 -m unittest discover -s tests/tooling -p 'test_*.py'`：113 项通过。
- `python3 -m unittest discover -s tests/governance -p 'test_*.py'`：43 项通过。
- `python3 -m unittest discover -s tests/skills/spec-dev -p 'test_*.py'`：28 项通过。
- 四个环境均通过 `render-kubernetes-application.sh` 使用同一测试 digest 渲染。
- 三个交付脚本通过 `bash -n`，`git diff --check` 通过。
- `./scripts/quality-gate.sh` 全部通过，包括 Gradle 回归测试、55 个模块许可证审计和
  58 个发布 JAR 的 Apache-2.0 制品校验。
- `docker build --tag j-store-immutable-delivery-check j-store-boot` 成功；验证镜像随后已从本机删除。

## 环境外证据

本地仓库无法代替中央 CI/CD 和隔离集群证明 registry copy、签名/attestation 远端验证、
生产迁移审批、目标 server-side dry-run、rollout、真实灰度流量或上一 digest 回滚。首个集成
流水线必须用同一候选 digest 完成这些演练并保存 commit、源/目标 digest、cluster UID、数据库
schema 版本、配置版本和结果；
在证据齐备前，canary overlay 只能视为 production-like，不能宣称已完成真实灰度发布。

本机执行与 CI 相同的 attested OCI archive 构建时，Docker daemon 拉取
`docker/buildkit-syft-scanner:stable-1` 超时，未生成 archive，因此 OSV archive 扫描也未在
本机实跑。普通固定 digest 镜像构建已通过；attestation 与容器扫描仍必须以联网 CI 结果为准。
