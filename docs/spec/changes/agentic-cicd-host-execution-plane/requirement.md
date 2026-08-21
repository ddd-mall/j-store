# Agentic CI/CD 主机原生执行面变更需求

## 背景

Symphony 在 Kubernetes `restricted` Pod 内启动 Codex `0.148.0` 时，Codex 的默认 Linux sandbox 需要 bubblewrap 创建 user/mount namespace，而当前容器运行边界返回 `bwrap: No permissions to create new namespace`。同一版本在 `k8s-master` 宿主机执行 `codex sandbox -- /bin/true` 已通过。继续在 Pod 内运行只能通过 `privileged`、`SYS_ADMIN`、放宽 seccomp/AppArmor 或关闭 Codex sandbox 等方式绕过，这会扩大权限并破坏当前治理目标。

Symphony 是单一长驻调度器，负责轮询 Issue、管理 workspace 并通过 stdio 启动 Codex App Server；Artifact Broker、Gate Dispatcher 和一次性 Gate Job 才属于 Kubernetes 隔离验证面。两者无需部署在同一 Pod 或 namespace。

## 目标

将唯一 Symphony/Codex 执行面迁移为 `k8s-master` 上的 host-native systemd 服务，同时保留 Kubernetes 中的 Artifact Broker、Gate Dispatcher、Gate Job、NetworkPolicy 和 Local PV。Codex 必须继续使用默认 bubblewrap sandbox，不增加主机或 Pod 的高权限能力。

## 范围

- 构建绑定 j-store revision、Symphony revision、patch/lock hash、WORKFLOW hash、Codex稳定版和 Level 2 disposable runtime binding 的不可变主机 bundle。
- 使用专用非登录 Unix 用户运行静态 systemd 服务；服务不自动 enable，不自动 restart。
- 通过 systemd credentials 注入短期 GitHub token、到期时间、Codex auth和裁剪后的provider配置；不读取个人 `~/.codex`。
- host controller直接使用现有Local PV宿主目录交换CandidateRevision、GateRequest和GateReceipt，不取得kubeconfig或Kubernetes API token。
- Kubernetes base和既有overlay不再渲染Symphony Deployment、Service、ServiceAccount、ConfigMap或Secret引用。
- 提供幂等停止、无模型preflight、显式启动和集群Supervisor退休入口；切换前必须证明不存在双活。

## 非目标

- 不把Artifact Broker、Gate Dispatcher或Gate Job迁出Kubernetes。
- 不修改NetworkPolicy、CNI、主机路由、代理服务或sing-box配置。
- 不关闭Codex sandbox，不授予`SYS_ADMIN`、privileged或unconfined seccomp/AppArmor。
- 不开放新的GitHub、合并、发布、生产或模型调用能力。
- 不执行本次仓库候选之外的主机安装、用户创建、凭据迁移、集群删除或付费模型调用；这些动作分别需要精确授权。

## 验收标准

### AC-HEP-01 单一执行面

所有受支持的Kubernetes kustomization都不得渲染Symphony Deployment或Service；旧Kubernetes部署生成入口必须fail-closed。host启动入口必须在启动前确认集群中Symphony期望副本为0且没有Running Pod。

### AC-HEP-02 不可变主机制品

构建必须要求洁净的完整j-store revision和锁定的洁净Symphony revision，验证两段patch与依赖锁摘要，运行Symphony compile/test和无模型Codex sandbox smoke。bundle必须有外部SHA-256、内部逐文件清单和runtime revision记录，不包含凭据。

### AC-HEP-03 最小主机身份

服务使用专用UID/GID和nologin shell，以非root运行；systemd必须启用`NoNewPrivileges`、只读系统和最小可写路径。不得限制Codex所需user namespace，也不得授予`CAP_SYS_ADMIN`。

### AC-HEP-04 凭据隔离

GitHub和Codex凭据只能从root管理的systemd credential文件进入服务。wrapper必须拒绝缺失、含空白或即将过期的GitHub token，清除不受信token别名，不打印秘密，并只把固定auth/config文件映射到专用`CODEX_HOME`。

### AC-HEP-05 无模型启动门

服务启动前必须运行`codex --version`、`codex sandbox -- /bin/true`和`codex login status`，该preflight不得发起Responses API调用。systemd单元保持static且`Restart=no`，避免重启或开机自动触发外部付费行为。

### AC-HEP-06 状态与Gate互操作

迁移后继续使用`/var/lib/jstore-agentic-cicd`、candidate、gate request、gate receipt和lease目录；停止、安装和Kubernetes Supervisor退休不得删除PVC、候选、日志或receipt。Broker/Dispatcher保持独立运行。

### AC-HEP-07 可恢复切换

切换顺序固定为：停止Pod Supervisor、安装但不启动host bundle、注入凭据、运行无模型preflight、删除旧Kubernetes执行对象、取得模型调用授权后显式启动。任一步失败都停止在无双活状态，保留数据和前一host release。
