# Agentic CI/CD 主机原生执行面任务

- [x] `HEP-01` 记录Pod内bubblewrap失败与同版本host sandbox PASS，拒绝privileged、`SYS_ADMIN`和关闭sandbox方案。
- [x] `HEP-02` 增加主机执行面需求、设计、验收和切换边界。
- [x] `HEP-03` Kubernetes base/overlay不再渲染Symphony Deployment、Service、ServiceAccount、ConfigMap或Secret引用；旧部署生成器fail-closed。
- [x] `HEP-04` 增加锁定revision、patch、依赖锁、Codex版本、Level 2 runtime binding和逐文件摘要的host bundle构建器。
- [x] `HEP-05` 增加不可变安装器、专用非登录身份、静态systemd unit、systemd credentials边界和host凭据白名单裁剪器；安装不得启动服务。
- [x] `HEP-06` 增加无模型preflight、双活拒绝、显式start、stop/status和Kubernetes Supervisor退休入口。
- [x] `HEP-07` 增加host/Kubernetes合同测试，并运行Agentic CI/CD完整tooling回归。
- [ ] `HEP-08` 从提交后的洁净revision真实构建host bundle，记录bundle SHA-256、Symphony测试和Codex sandbox smoke。
  - 当前阻塞：工作区包含本次候选，构建器按设计拒绝dirty source；先审查并提交候选。
- [ ] `HEP-09` 经精确主机写授权，在`k8s-master`安装bundle但保持service inactive，配置专用runtime identity和四个credential文件。
- [ ] `HEP-10` 经精确集群写授权，确认旧Deployment为0后删除Kubernetes Symphony执行对象；Broker、Dispatcher、PV/PVC和NetworkPolicy保持不变。
- [ ] `HEP-11` 在专用身份下完成无模型preflight并记录systemd hardening、loopback bind和单实例证据。
- [ ] `HEP-12` 取得新的模型次数/费用授权后显式启动，完成disposable Issue闭环；随后stop并生成退出摘要。

只有HEP-08至HEP-12都有实机证据，才可把“执行面已迁出Pod”声明为运行态完成。仓库候选通过测试不等同于主机或集群已经切换。
