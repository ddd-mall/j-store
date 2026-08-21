# Agentic CI/CD 主机原生执行面设计

## 架构边界

```text
GitHub Issues / Responses API
             |
             v
k8s-master host: systemd -> Symphony -> Codex App Server (bubblewrap)
             |                    |
             |                    +-- task workspace/state/log
             +-- CandidateRevision + GateRequest (Local PV host paths)
                                      |
                                      v
Kubernetes agentic-cicd: Artifact Broker + Gate Dispatcher
                                      |
                                      v
Kubernetes agentic-cicd-gates: disposable offline Gate Job
                                      |
                                      +-- GateReceipt -> host path -> Symphony
```

Symphony不持有kubeconfig，也不直接创建Job。现有host-owned controller把请求写入Local PV宿主目录，Kubernetes Dispatcher继续以最小ServiceAccount消费请求并创建Gate Job。这个边界保留候选代码与控制面凭据隔离，同时避开Pod内user namespace限制。

## 制品与安装

`scripts/agentic-cicd-host-build.sh`从两个洁净Git revision构建bundle。Symphony源码按锁文件顺序应用phase bridge、phase routing和依赖锁，必须完成compile/test/escript；Codex从当前已安装稳定版npm主包及当前平台optional package构建自包含payload，并从该隔离payload执行版本与默认sandbox smoke。不得使用宿主全局Codex smoke替代最终payload验证。Level 2 state contract和runtime binding由可信helper生成并写入bundle。

Symphony依赖锁在编译前必须通过`mix hex.audit`。Bandit最低版本为`1.12.5`，以排除`CVE-2026-74836`和`CVE-2026-75484`；治理合同拒绝回退到受影响版本。

bundle目录包含：

- `payload/bin/`：Symphony escript、Node、Codex CLI和运行wrapper；
- `payload/controller/`：controller、Python package和固定Level 2 runtime profile；
- `payload/config/`、`payload/WORKFLOW.md`、`payload/runtime-revisions`；
- `deploy/`：静态systemd unit及非秘密runtime identity；
- `manifest.sha256`：全部普通文件摘要。

安装器先验证外部bundle摘要和内部清单，再创建不可变`/opt/jstore-agentic-cicd/releases/<bundle-digest-prefix>`；`current`只指向完整release。它可以创建固定UID 10001/GID 11001的专用身份和必要目录，但不会创建凭据、enable或start服务。

## 运行隔离

systemd service使用`ProtectSystem=strict`、`ProtectHome=yes`、`PrivateTmp=yes`、空capability bounding set和精确ReadWritePaths。它刻意不配置`RestrictNamespaces`，因为Codex默认Linux sandbox需要非特权user/mount namespace；这不等同于授予root或`CAP_SYS_ADMIN`。

dashboard由host专用WORKFLOW绑定`127.0.0.1:4000`。外部查看只允许通过操作者自行建立的SSH tunnel，本变更不创建监听公网的Service、Ingress或NodePort。

## 凭据模型

非秘密repository、GitHub App login和reviewer写入root管理的`runtime.env`。四个秘密/敏感文件通过`LoadCredential`进入私有`$CREDENTIALS_DIRECTORY`：

- `github-token`；
- `github-token-expires-at`；
- `codex-auth.json`；
- `codex-config.toml`。

wrapper只在exec前读取GitHub token到Symphony环境，并清除其它GitHub token别名。Codex auth/config通过专用HOME中的符号链接读取，不复制个人配置，不把值写入bundle、journal或命令行。

## 生命周期与双活防护

单元没有`[Install]`且`Restart=no`。`agentic-cicd-host-start.sh`只读检查当前context，拒绝非0的旧Deployment或Running Symphony Pod，然后用瞬时systemd unit执行同身份、同凭据的无模型preflight；全部通过后才显式start静态服务。

Kubernetes base删除执行面引用不会自动删除集群中既存对象，因此切换使用单独退休脚本：先幂等缩容为0，再只删除Symphony Deployment、Service、ServiceAccount和带Symphony标签的旧ConfigMap。它不删除PV/PVC、namespace、Broker、Dispatcher、Gate或NetworkPolicy。

## 失败与恢复

- build失败：无bundle，不改变运行时；
- install失败：服务保持inactive，旧release和状态保留；
- preflight失败：不启动Symphony，不产生模型调用；
- retirement失败：host启动门仍因Pod/replica证据拒绝；
- host运行失败：执行stop，保留状态；审查后将`current`切回上一已验证host release并重新preflight；
- 不回退到关闭Codex sandbox的Pod方案。若必须临时恢复旧Pod，需要新的安全决定和精确部署授权。

## 验证

- 静态合同：Kubernetes render、systemd hardening、凭据路径、旧入口fail-closed；
- shell语法：全部新增/变更脚本`bash -n`；
- 制品：洁净revision上的真实build、Symphony test、bundle清单和Codex sandbox smoke；
- 主机：安装后service inactive、专用身份、路径权限、`systemd-analyze security`与无模型preflight；
- 切换：旧Pod为0/不存在、host单PID、loopback dashboard、Broker/Dispatcher Ready；
- E2E：取得单独模型授权后，完成disposable Issue exact-candidate闭环并立刻停止。
