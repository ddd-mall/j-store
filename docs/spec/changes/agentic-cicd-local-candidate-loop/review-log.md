# Level 1 本地候选闭环评审记录

## 2026-08-15 CandidateRevision 首个切片

独立安全评估首次结论为 FAIL，识别两个阻塞问题：受信 Snapshotter 的 `git add` / filtered `hash-object` 可执行候选影响的 Git clean/process filter；物化入口会解析并跟随调用方提供的 destination符号链接。

修复后：

- Snapshotter改用安全文件枚举、`git hash-object --no-filters`、临时 index和 `update-index --cacheinfo`构造 raw tree，不执行候选 Git filter或修改真实 index；
- materialize拒绝既存目标和 destination符号链接，在可信父目录的临时目录中先校验全部 archive member，再以 `O_EXCL` / `O_NOFOLLOW`写入并原子替换目标；
- `CandidateRevisionStore`默认 fail closed，CLI只有在机器合同 `freeze_local_candidate=true`时才开放冻结入口；Level 0仍为 false；
- 新增恶意 clean filter、destination符号链接、路径穿越、越界符号链接、submodule、special file、嵌套仓库、runtime metadata和 archive篡改负向测试。

独立复评结论为 PASS：恶意 filter未执行，destination符号链接被拒绝且外部目录保持为空，Level 0冻结入口保持关闭；复评范围内没有残留阻塞 finding。Gate/Reviewer完整绑定同一 CandidateRevision仍属于 LC-10、LC-13和 LC-14，不计为本切片完成。

验证证据：

- `python -m unittest tests.tooling.test_agentic_cicd_candidate tests.tooling.test_agentic_cicd_runtime_controller tests.governance.test_agentic_cicd_contract`：PASS；
- `python scripts/check-agentic-cicd.py`：PASS；
- 完整仓库规模 CandidateRevision freeze：PASS；
- `git diff --check`：PASS。

## 2026-08-15 Gate 合同与无模型 Dispatcher 切片

独立评估首次结论为 FAIL，识别四个阻塞问题：既存 Job身份未绑定 task/命令/超时/请求时间，可跨 task复用；任意命令可通过自哈希伪装为已批准策略；基础设施重试上限可由调用者覆盖；`record-gate` CLI未适配新的 Store构造合同。

修复后：

- GateJobIdentity完整绑定 issue、CandidateRevision、runner digest、命令策略、命令、超时和请求时间，恢复前逐项比较；
- GateRequestStore除固定 runner外，还要求命令集合进入 host-owned可信 allowlist并限制最大超时；策略摘要必须实际绑定同一命令集合；
- GateReceiptStore只从 host-owned state-contract读取基础设施重试预算，不再接受调用方数值；Level 0的 `record-gate`按`run_isolated_gate=false`明确 fail closed；
- 新增跨 task旧 Job、恶意自哈希命令、合同重试上限和真实 CLI回归测试。

独立复评结论为 PASS：四个原反例均被拒绝或按合同进入 blocked，未发现新的 LC-10/LC-11阻塞项。真实 Kubernetes client、Job清单、Artifact Broker及集群恢复演练仍属于 LC-12、LC-12A和 LC-13。

验证证据：

- Gate/Candidate/phase/runtime 聚焦测试：PASS；
- `python scripts/check-agentic-cicd.py`：PASS；
- `./scripts/quality-gate.sh`：PASS（tooling 146 tests及 Gradle/治理/依赖/许可证门禁）；
- `git diff --check`：PASS。

## 2026-08-15 Gate Runner、Broker 与 Kubernetes 接线切片

两轮独立复评先后暴露并关闭了可信边界与可靠性缺口：request/receipt共享写身份、可变controller镜像、候选可替换质量门禁、NetworkPolicy同步窗口、策略运行时/schema漂移、Job清理停摆、fetch内存无界、真实Gate镜像缺少可信Python包以及UDP DNS未被探测。

最终实现与证据包括：

- Supervisor和Dispatcher分别使用UID 10001/10002以及独立request/receipt Local PV，挂载方向互为RW/RO；
- controller、fetch和runner只接受完整OCI digest，导入验证tag到manifest绑定，rollout验证新Pod UID和runtime imageID；
- runner只执行镜像内`/opt/jstore-gate/run-quality-gate`，可信治理脚本和`capabilities.py`随镜像只读交付，repository manifest由不同UID的fetch init生成并只读挂载；
- network-admission init在fetch前验证API、TCP/UDP DNS和公网拒绝及Broker允许；
- Job以前台删除并确认消失，receipt卷持久化cleanup marker，使临时删除故障在request消费后仍可恢复；
- archive限制512 MiB与10,000 member，以两遍有界流式扫描先全量校验再物化，单文件内容按1 MiB分块复制。

最终独立安全和合同复评均为PASS；安全复评聚焦61项测试、合同检查、shell语法和diff检查全部通过。真实containerd导入、Pod imageID、foreground GC/配额、UDP NetworkPolicy及跨节点Broker fetch仍属于LC-K05、LC-12/12A和LC-19的集群验收证据，未提前标记完成。Level 0能力保持关闭。

## 2026-08-16 开发集群跨节点 Gate 验收

从洁净提交 `08453c89fc7e6a4abf9e52e572ddaa763a6b7d48` 构建一份 Gate Runner OCI archive，archive SHA-256为 `fb21e77b8d0b4a054db768bead10f7b255dde4c48d7956cba1a9761cc09c1cc3`，manifest digest为 `sha256:30f48b2ef512c0d7be8657637d718f663c8b8eb78843cc5603e10234ee152334`。同一 archive在 `k8s-master` 和 `k8s-worker1` 校验后导入 containerd，两节点均建立 CRI-managed `repository@digest` 别名。

控制面保持 controller digest `sha256:d6537f49397dd1ff5229b0f2feda396dde00d00442cf8df59b7600522472b697`。Symphony、Broker和 Dispatcher的当前 Pod UID分别为 `7003807f-33ff-40ea-a700-af303669dea2`、`d1901e0a-f869-4206-8957-45cab5f82f19` 和 `26918c7e-b6f3-411f-a7db-635581d020ac`，三者 runtime image ID均匹配该 digest。镜像内双 revision为 Symphony `8001b52e3062495a16e520e4ceaf8f9de868c4d0` 和 controller `7306578141202251222f790f799410a086272cda`；Deployment引用 ConfigMap `symphony-workflow-6899d6d8bk`，其 `WORKFLOW.md` SHA-256为 `a8c18b98d5fbeb32dba03f522b4fb909c42f7cc7534a8d5607bb8d90e620fa5f`。Symphony实机核对无 Kubernetes ServiceAccount token，其 ServiceAccount不能在 `agentic-cicd-gates` 创建 Job；Level 0能力与全部 GitHub远程写保持关闭。

成功路径使用 CandidateRevision `ec915c1c2ac83fe62f67b8af8a7a25c292c5064d08a595500ddefa22350c4358`。Job UID `867bfa42-8e8b-414a-95dc-e00a1fad53d7`固定调度到 worker1；network-admission、fetch和主 Gate容器实际 image ID均匹配受审 digest。两个 init分别验证网络隔离和跨节点一次性 Broker fetch，然后主 Gate在无 `.git` 的候选副本上完成全量门禁。回执为 `PASS`、exit code 0、findings空，log SHA-256为 `c349e995969960b8ac0deadd900d160e76fbebb56c5ac1b4df5c0716c6bcb059`；Dispatcher在前台确认 Job消失后才写 cleanup marker。额外的同等受限 worker1探针在 Broker `10.96.200.81:8081` 可达的前提下确认 j-store `10.107.27.233:8080`、Redis `10.101.151.46:6379` 和 PostgreSQL `10.108.123.199:5432` 全部不可达，且实际 runtime image ID仍为受审 Gate digest。

超时路径临时将可信 policy调为合同最小值 60 秒，同一 CandidateRevision的 Job UID `af8c0e13-ada4-43c9-aeda-b9b659ecd31d` 实际触发 Kubernetes `DeadlineExceeded`。Dispatcher产生 `INFRASTRUCTURE_FAILURE`、`exit_code=null`、findings空的 exact-identity receipt，完成前台清理后恢复 900 秒 policy并以新 Pod UID重启 Dispatcher。该证据连同早期的 API认证、fetch工作目录和候选写权限故障，实际覆盖创建失败、候选 FAIL、超时基础设施失败和 PASS 四种分流。

回滚目标固定为上一已验证 Gate digest `sha256:e5269ad83c671f92dd71dccd97c8e9a75b3c82d87c85ffaff5d40c9ce651487d`。以 `imagePullPolicy: Never` 创建的两个无 token、非 root、只读 rootfs Job分别在 master和worker1实际启动并返回 0，两个 runtime image ID均精确指向该 manifest。回滚动作是将 Gate Policy恢复到该 digest并替换 Dispatcher；不需要重建或按节点重打镜像。

validate恢复演练再次调度同一 CandidateRevision，并在主 Gate容器运行期间替换 Dispatcher Pod。替换前后集群始终只有一个 Job UID `b3458e86-e7dc-44b0-a025-109a633a8138` 和一个 Pod UID `97791ba7-8f11-4b93-857d-cd181123e835`；新 Dispatcher按完整 GateJobIdentity恢复而未重新创建。原 Job完成后产生 `PASS` / exit 0 / findings空的 exact-candidate receipt，log SHA-256为 `bcf098cc822e3abf11c25b30e484e4d3fe4b76adcc314ffc748a900f93971bf5`，随后以前台删除和 cleanup marker收口。该证据只关闭 validate恢复点，不替代恶意候选、implement/review恢复或真实 Reviewer验收。

恶意候选验收在一次性 Git fixture中修改候选 `gradlew`，受信 Gate入口和治理测试仍来自 runner镜像。首个 CandidateRevision `24f0ff6ffdeab82c65629c9e3370742c1fba033c92eb55e7bf261c815b352250` 实际证明 Kubernetes token不存在，API、j-store、Redis和PostgreSQL不可达；第二个新 CandidateRevision `ee392b53998edc285d5937e0b556768e17493f3404a4a564885b1d5aedacff17` 进一步证明 GitHub/Artifact凭据、Symphony state、gate exchange和candidate artifact路径全部不可见，同样无法联系四个禁止端点。两个候选均按预期退出 1，分别产生绑定各自 CandidateRevision的 `gate:validation-command-failed` 单finding FAIL receipt；第二个 Job/Pod UID为 `2c9ffb3a-10b8-4d36-a3af-4c5a3b841f68` / `d075be46-b048-4338-8027-cc2cae986c1d`，log SHA-256为 `6b671d26cfdf8c084eaf48f03c43fec0c283590bf16c862f28f2f27d7ef0a609`。Dispatcher写回执后以前台清理两个 Job，控制面三个 Pod仍 Ready且零重启。可重放 fixture、完整回执、cleanup marker和脱敏终态保存在 `evidence/2026-08-16-malicious-gate-smoke.md`。
