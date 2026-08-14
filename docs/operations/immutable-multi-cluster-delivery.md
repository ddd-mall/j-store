# 不可变 OCI 制品多集群交付运行手册

## 范围与前提

本流程从中央 CI/CD 服务器向物理隔离但能力同构的 development、integration、canary 和
production 集群交付 J-Store。每个集群提供 Kubernetes API、私有 registry、`jstore`
namespace、`jstore-runtime` ConfigMap 和 Secret，以及相同的数据库、Redis、监控和入口契约。
`jstore` namespace 由平台预置并执行 restricted Pod Security；应用清单只设置资源 namespace，
不会创建或修改 namespace。
平台还必须为承载外部流量的入口控制器 namespace 设置
`jstore.network/ingress-access=true` 标签；应用 NetworkPolicy 仅放行该入口和监控 namespace。
Redis 由平台部署在 `jstore` namespace，并保持 `app.kubernetes.io/name=redis` Pod 标签；
PostgreSQL 位于 `postgresql` namespace。

CI/CD 一次只建立一个目标集群隧道。隧道、registry 登录、Kubernetes 身份和审批由平台
配置提供，不进入仓库或 Job 日志。

## 构建一次

在干净提交上构建并推送候选：

```bash
./scripts/build-oci-candidate.sh \
  --repository "$BUILD_REPOSITORY" \
  --output-dir build/oci-candidate
```

脚本执行 `:j-store-boot:bootJar` 和一次 BuildKit 构建，附带 SBOM/provenance attestation，
并输出：

```text
build/oci-candidate/build-metadata.json
build/oci-candidate/candidate.env
```

后续阶段只读取 `JSTORE_IMAGE_DIGEST`，不得再次执行构建。中央 CI/CD 还必须使用批准且固定
版本的扫描器检查最终镜像，并把签名、漏洞结果、许可证结果和 provenance 与该 digest 绑定。
任何必需证据缺失或高危结果未获人工接受时停止晋级。

## 目标配置

[`config/cicd/cluster-target.schema.json`](../../config/cicd/cluster-target.schema.json)
定义 CI/CD 目标配置契约；`cluster-targets.example.json` 只提供无凭据示例。真实 cluster UID、
context、完整 repository 和 tunnel profile 保存在 CI/CD 受保护配置中。registry host 从
repository 解析，不设置第二个可能漂移的 registry 字段。

配置不得包含 kubeconfig、token、registry 密码、私钥或业务 Secret。development/integration
可以自动部署；schema 强制 canary/production 的 `requiresApproval=true`，流水线还必须使用
受保护环境审批和单环境并发锁，不能只把该布尔字段当作授权本身。

## 数据库迁移边界

development 和 integration 面向可重建的内部数据库，允许应用启动时执行 Flyway。canary 和
production overlay 固定 `SPRING_FLYWAY_ENABLED=false`，通用部署脚本不会执行生产迁移。
容器显式 `env` 的优先级高于 `jstore-runtime` Secret 的 `envFrom`，Secret 中即使误含同名键
也不能重新启用 Flyway。

development overlay 使用 `local,observability,outbox-observability` profile，并启用只记录脱敏
手机号的开发验证码发送器。integration 及更高环境使用 `production`，必须预置真实短信发送
适配器，否则应用按设计拒绝启动。

若候选包含数据库变更，CI/CD 必须在应用部署前进入独立迁移阶段：确认精确数据库目标、取得
人工审批、执行已批准迁移、验证目标 schema 版本并保存恢复决策。迁移失败或证据缺失时停止
晋级。应用 digest 回滚只恢复应用代码，不能撤销已经执行的数据库变更。

## 制品晋级

1. 为单一目标建立隧道，只暴露 Kubernetes API、registry 和必要验证入口。
2. 使用能保留 manifest 的 OCI copy 工具，把已验证候选复制到目标 registry；禁止重建。
3. 比较源和目标 manifest digest，必须完全相同。
4. 在目标 registry 上验证签名和 provenance subject digest。
5. 只在上述步骤通过后执行应用部署。

若 registry mirror 或复制过程改变 manifest digest，该目标不满足晋级契约。多架构候选必须
复制完整 image index，而不是只复制当前 CI 节点架构的 manifest。

## 渲染与部署

离线检查生产渲染结果：

```bash
./scripts/render-kubernetes-application.sh \
  --environment production \
  --image "$TARGET_REPOSITORY@$JSTORE_IMAGE_DIGEST"
```

隧道建立且目标配置加载后部署：

```bash
./scripts/deploy-kubernetes-application.sh \
  --context "$TARGET_CONTEXT" \
  --expected-cluster-uid "$EXPECTED_CLUSTER_UID" \
  --environment "$ENVIRONMENT" \
  --namespace "$NAMESPACE" \
  --image "$TARGET_REPOSITORY@$JSTORE_IMAGE_DIGEST"
```

部署脚本执行以下安全检查：

- 当前 context 必须等于显式目标；
- `kube-system` namespace UID 必须等于 CI/CD 配置；
- namespace 必须显式为平台预置的 `jstore`；
- 镜像必须是无 tag 的 `repository@sha256:digest`；
- `jstore-runtime` ConfigMap 和 Secret 必须已经存在，但脚本不读取 Secret 内容；
- canary/production 渲染结果必须保持 `SPRING_FLYWAY_ENABLED=false`；
- 写入前必须通过 server-side dry-run；
- rollout 和 readiness 必须通过。

完成后 CI/CD 记录 commit、源/目标 digest、context、cluster UID、environment、配置版本和
验证结果，然后关闭隧道并销毁短期凭据。

## 灰度与生产

canary overlay 是 production-like 高可用部署。只有统一外部流量控制面确实向 canary 集群
分配受控真实流量时，才能执行灰度判定。流量切换是独立的生产写入动作，必须人工批准，
不能由应用部署脚本自动完成。

## 回滚

选择目标环境上一个已验证 digest，重复制品验证和部署命令。不得移动 tag、重新构建旧提交
或恢复 PVC JAR。应用回滚不删除数据库、Secret 或 namespace，也不会重新运行 Flyway；
数据库迁移回滚必须走独立迁移和人工审批流程。

## 当前未验证项

仓库测试可以验证 Kustomize 渲染、digest 输入、安全边界和部署脚本结构，但不能替代真实
隧道、registry manifest copy、签名、漏洞扫描、生产迁移审批、目标 server-side dry-run、
rollout、灰度流量与回滚演练。这些证据必须由中央 CI/CD 和隔离集成环境补齐。
