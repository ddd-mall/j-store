# 不可变 OCI 制品多集群交付运行手册

## 范围与前提

本流程从中央 CI/CD 服务器向物理隔离但能力同构的 development、integration、canary 和
production 集群交付 J-Store。每个集群提供 Kubernetes API、私有 registry、`jstore`
namespace、`jstore-runtime` Secret 以及相同的数据库、Redis、监控和入口契约。
`jstore` namespace 由平台预置并执行 restricted Pod Security；应用清单只设置资源 namespace，
不会创建或修改 namespace。
平台还必须为承载外部流量的入口控制器 namespace 设置
`jstore.network/ingress-access=true` 标签；应用 NetworkPolicy 仅放行该入口和监控 namespace。

CI/CD 一次只建立一个目标集群隧道。隧道、registry 登录、Kubernetes 身份和审批由平台
配置提供，不进入仓库或 Job 日志。

## 构建一次

在干净提交上构建并推送候选：

```bash
./scripts/build-oci-candidate.sh \
  --repository "$BUILD_REGISTRY/j-store/application" \
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
context、registry 和 tunnel profile 保存在 CI/CD 受保护配置中。

配置不得包含 kubeconfig、token、registry 密码、私钥或业务 Secret。development/integration
可以自动部署；canary/production 必须使用受保护环境审批和单环境并发锁。

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
  --image "$TARGET_REGISTRY/j-store/application@$JSTORE_IMAGE_DIGEST"
```

隧道建立且目标配置加载后部署：

```bash
./scripts/deploy-kubernetes-application.sh \
  --context "$TARGET_CONTEXT" \
  --expected-cluster-uid "$EXPECTED_CLUSTER_UID" \
  --environment "$ENVIRONMENT" \
  --namespace "$NAMESPACE" \
  --image "$TARGET_REGISTRY/j-store/application@$JSTORE_IMAGE_DIGEST"
```

部署脚本执行以下安全检查：

- 当前 context 必须等于显式目标；
- `kube-system` namespace UID 必须等于 CI/CD 配置；
- namespace 必须显式为平台预置的 `jstore`；
- 镜像必须是无 tag 的 `repository@sha256:digest`；
- `jstore-runtime` Secret 必须已经存在，但脚本不读取其内容；
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
或恢复 PVC JAR。应用回滚不删除数据库、Secret 或 namespace；数据库迁移回滚必须走独立
迁移和人工审批流程。

## 当前未验证项

仓库测试可以验证 Kustomize 渲染、digest 输入、安全边界和部署脚本结构，但不能替代真实
隧道、registry manifest copy、签名、漏洞扫描、目标 server-side dry-run、rollout、灰度流量
与回滚演练。这些证据必须由中央 CI/CD 和隔离集成环境补齐。
