# Level 1 本地候选闭环评审记录

本文件是仍在推进的 Level 1 变更的原始评审证据，不维护当前能力或跨阶段任务状态。当前状态只在 `../../agentic-cicd/tasks.md` 和本变更的 `tasks.md` 中维护；变更完成后，本记录随证据一起并入总归档。

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

## 2026-08-16 Reviewer exact-candidate 无模型验收

首版验收手工构造review snapshot并跨Issue复用Gate receipt，且只证明直接写入被权限位拒绝；独立合同和安全复评均判定FAIL。修复后，`PhaseContextStore`在启动Reviewer前重新解析并校验PASS request/receipt的Issue、gate ID、runner、命令策略和完整CandidateRevision；`TurnStateController`在接受review turn前再次逐文件验证物化archive，因而即使同UID先chmod再篡改也不能生成PASS decision。

候选controller代码已在Symphony Pod的临时路径中完成无模型复验：正式`GateReceiptStore`以同Issue输入执行validate→review；直接写和完成前篡改分别被权限位与exact-archive复验拒绝；同session仍被拒绝，独立session最终绑定同一revision。该验收未启动Codex/App Server或模型，未写正式Supervisor task state，未创建Gate Job或远程Git/GitHub状态。可重放脚本、精确request/receipt和脱敏输出保存在`evidence/2026-08-16-lc14-reviewer-smoke.md`。由于新代码尚未进入部署镜像，LC-14保持未完成；真实模型Reviewer与review阶段重启仍分别保留给LC-21和LC-22。

随后不可变controller `sha256:305a2b8af0cdc38510b663436e17d6f47eba4b02a9c015010e64a3aa0084d1a9`部署到开发集群，镜像内controller revision为`3a537df4dac461e40b12fcda46597b959ef24f52`，已包含上述修复。当前Pod `symphony-5586ff8477-gnst9` / UID `4a8cdf05-2ba0-4715-ab63-bf399d0a126f`从`/opt/jstore-agentic-controller`重跑固定fixture SHA-256 `7fd4846728a39c57db54f0f2e118b93346bd4f6324a6e9b59f03cd61101fed1f`；CandidateRevision、artifact、GateReceipt与decision摘要与候选验收一致。2,553个条目的只读检查、直接篡改拒绝、chmod后完成前复验拒绝、同session拒绝和独立session PASS均成立。本次经独立代码/证据复核、55项聚焦测试、治理检查与完整`./scripts/quality-gate.sh`后关闭LC-14；真实模型Reviewer和review阶段重启仍分别归LC-21和LC-22。

## 2026-08-16 Symphony 供应链资格

上游锁定commit的原始依赖审计得到27个Hex公告，包含Bandit、Mint、Phoenix、Plug、Req和HPAX的HIGH级网络/资源耗尽风险。缓解候选以单独只读`mix.lock`升级受影响包及必要传递约束，保留`yaml_elixir 2.12.0`，并把Ecto 3.14下的空`codex.command`拒绝改为显式合同。39项依赖许可证只包含MIT、Apache-2.0和BSD-2-Clause。

指定Linux主机以固定Elixir构建器从洁净上游Git archive按顺序应用两段补丁。最终结果为296项Mix测试零失败、Hex公告为零、escript构建PASS和`codex-cli 0.146.0`精确smoke PASS；仓库完整`./scripts/quality-gate.sh`亦PASS。独立规格复评确认LC-04/LC-05证据充分，并指出首版LC-06构建入口未把实际patch/lock内容重新散列后再写入labels；该缺口已改为构建前fail-closed复验。完整公告、升级、许可证、兼容性和回滚记录见`evidence/2026-08-16-symphony-supply-chain-audit.md`。

LC-06仍保持未完成，直到洁净提交产生实际runtime manifest digest、完整OCI labels、SPDX SBOM和SLSA provenance，并验证两份attestation的subject绑定同一runtime digest。Level 0能力与全部远端写继续关闭。

随后提交`89c7b462...be401`在指定Linux主机完成最新两阶段隔离审计：第一阶段在执行被审代码前生成零公告与许可证证据，第二阶段不挂载证据目录并完成296项测试、escript和Codex精确版本smoke；各阶段前后均复验依赖锁。审计JSON绑定routing patch `b60be305...7535`且自身SHA-256为`736c8a35...8954`，因此LC-05证据门关闭。

最新洁净controller `3a537df4...24f52`随后从固定Git archive构建。可加载runtime与attested OCI输出得到相同manifest `sha256:305a2b8a...4d1a9`；镜像labels完整绑定Symphony/j-store revision、两段patch、dependency lock、Codex、两个基础镜像和WORKFLOW。构建器从OCI中各提取唯一SPDX与SLSA statement，并确认两者subject绑定该runtime digest；制品摘要与回滚边界保存在`evidence/2026-08-16-controller-image-build.md`，LC-06证据门关闭。

## 2026-08-16 Complete hook invocation 身份与恢复幂等

代码复查发现`complete-turn`按回调到达时的当前phase推断receipt role。Review FAIL先把phase回退到implement，因此同一个Reviewer after hook重试会被误判为新Implementer并推进validate。另一个规格漂移是新实现或新head会整体清空ReviewDecision，与“旧证据保留审计但不能批准新候选”冲突。

修复先以回归测试复现，确认旧实现因不接受`expected_phase`而失败。随后complete hook改为必须回传Symphony启动turn前取得的host-owned phase、role、head SHA和可选CandidateRevision；controller在任何持久化前逐项校验，并以session/thread/turn规范元组消费幂等键。测试同时证明Review FAIL后的立即重放因phase不匹配被拒绝，未来再次进入review时旧回调仍因已消费而拒绝，snapshot、预算、finding和decision均不受第二次回调影响。历史ReviewDecision继续按CandidateRevision保留，当前授权仍要求exact-candidate匹配。

99项本地无模型组合测试、治理检查和两段patch apply均PASS。独立规格评审复验调用身份、重放、历史账本、patch顺序和摘要后结论为PASS，无阻塞finding。该证据只证明仓库合同；本机没有Elixir`mix`，新的routing patch尚未完成Symphony compile/test、镜像重建或集群review恢复演练，因此LC-16/LC-22保持未关闭，Level 0能力与全部远程写保持关闭。可重放命令和边界记录见`evidence/2026-08-16-lc16-invocation-binding.md`。

随后容器化原生审计从固定Elixir基础digest一次构建audit-toolchain，并让依赖资格与compile/test两个隔离容器共同按捕获的image ID运行，避免重复在线安装。工具链Dockerfile SHA-256为`a324ae9a...e0918`，临时image ID为`sha256:adaa67f5...6136c0`且退出后已精确清理。两段patch顺序apply、零Hex公告、39项许可证、warnings-as-errors编译、296项测试、escript和Codex精确版本均PASS；报告SHA-256为`87db57f4...a053`。这关闭了新routing patch的Elixir compile/test缺口，但未替代不可变镜像、真实单turn、无第二App Server和集群review恢复证据，因此LC-16/LC-17/LC-22仍保持未关闭。

独立只读规格/安全复评再次判定PASS、无阻塞finding，并独立重跑21项Kubernetes/脚本合同测试。残余风险为audit清理失败和并发运行尚无故障注入，且代理分支主要依赖静态合同与本次成功运行；这些风险不改变当前fail-closed审计结果，但保留给后续工具测试加固。LC-16/LC-17/LC-22状态不变。

## 2026-08-16 LC-17 controller镜像安全门禁

controller `175da3b1...b964`从洁净提交构建为runtime manifest `sha256:5bbe1352...058e`。本机RepoDigest、OCI labels和非root用户核对通过；Docker archive、SPDX、SLSA provenance、source record与OSV JSON均有独立SHA-256，且两份attestation的唯一subject绑定同一runtime digest。第一次成功镜像有229组finding/16 critical；runtime执行当前Bookworm安全更新后降为199组/14 critical，但仍有13组critical未被Debian标为`unimportant`，对应curl、glibc、openssh、perl、sqlite3和zlib的Bookworm源包均无fixed version。

AC-LC-09不允许实现者因开发网络隔离或暂无修复而自行接受该风险。当前裁决为`BLOCKED_PENDING_HUMAN_SECURITY_DISPOSITION`：镜像可以继续只读复核，但不得部署；LC-17、LC-16和LC-22继续保持未关闭，Level 0和全部远程写不变。完整身份、扫描统计和13项finding见`evidence/2026-08-16-lc17-controller-image-security.md`。

独立只读规格/安全评审核对最终OSV报告、AC-LC-09和制品subject后确认身份链、SBOM及provenance PASS，但对LC-17和digest `sha256:5bbe1352...058e`部署均给出BLOCK；自动化或评估者无权接受该高风险。本轮21项Kubernetes/构建合同测试、99项无模型组合测试、治理检查和完整六阶段质量门禁均PASS，不改变安全阻塞裁决。

旧候选BLOCK后继续做最小运行时和Git网络触发面缓解。提交`a5e03c6d...d937`删除runtime中的`curl`和OpenSSH客户端，生产bootstrap固定到j-store公开HTTPS origin，并清除ambient proxy、SSH/askpass、credential helper及system/global Git配置；提交`e03849d2...04cb`进一步固定HTTP/1.1、30秒低速熔断和120秒Git子进程总时限。真实GitHub clone只证明TLS/HTTPS请求抵达GitHub，当前环境等待`info/refs`时超时，未被记录为成功bootstrap。

最终不可变候选runtime manifest为`sha256:e3a3e25a...f753b6`，RepoDigest、labels、非root身份、Docker archive、SPDX、SLSA provenance和source record均已核对，两份attestation唯一subject与runtime digest一致。镜像内`curl`、`ssh`、`scp`、`sftp`均不存在，Git/Python/Codex版本符合锁定合同。最终OSV报告SHA-256为`4ba29c11...bde5`，结果为178组/33个受影响package：13 critical、53 high、92 medium、20 low或未评分，其中56组为Debian `unimportant`，12组critical不是`unimportant`；OpenSSH finding已消失。

最终revision上的21项Kubernetes合同测试、102项无模型组合测试、治理检查、`git diff --check`和完整六阶段质量门禁均PASS。剩余12项缓解尚待独立安全复评，当前仍为`BLOCKED_PENDING_HUMAN_SECURITY_DISPOSITION`；该镜像未部署，LC-16/LC-17/LC-22继续保持未关闭，Level 0和全部远程写不变。

独立只读安全复评随后重新核对全部制品摘要、唯一attestation subject、OSV统计、最终镜像和受审代码路径。评估者确认curl四项所需的HTTP/2 stream dependency、跨origin Digest、跨域cookie和多proxy认证复用均被固定HTTPS origin与Git传输策略排除；glibc、Perl、SQLite的危险API在受审路径不可达，32位Perl finding不适用于`ivsize=8`，MiniZip受影响组件不存在。因此身份链和AC-LC-09安全资格PASS，LC-17关闭。实际部署仍为`BLOCKED_BY_AUTHORITY`，且GitHub clone尚未越过`info/refs`超时，不能提前关闭LC-16/LC-22或宣称bootstrap成功。

评审另建议后续清除`GIT_CONFIG_PARAMETERS`、`GIT_SSL_NO_VERIFY`和Git HTTP low-speed环境变量。该项为非阻塞加固，不改变本次已审核digest；实施时必须产生新revision并重新完成镜像、扫描和独立评审。

加固回归首先证明bootstrap修复仍不完整：CandidateSnapshotter的`check-ignore`、临时index/tree、通用Git和`hash-object`会继承完整controller环境。独立评审据此BLOCK中间digest `sha256:cc26e425...3fe54`；该候选被标记为`SUPERSEDED`且从未部署。提交`86480b1f...c2c4`抽取统一最小子进程环境，仅继承`PATH`、locale和时区，候选Git只追加受控配置与临时`GIT_INDEX_FILE`。真实Git wrapper测试仅记录变量名称，证明GitHub/model凭据、askpass、proxy、TLS/config和low-speed ambient变量均未传播。

新洁净revision构建为runtime manifest `sha256:7edcb88b...66bf6`。Docker archive、SPDX、SLSA provenance、source record和OSV JSON摘要分别为`6b423474...df1`、`59ba9d03...fd5c`、`fdec9eff...30f6`、`dd43c74d...eae5`和`841652b8...cb61`；两份attestation各自唯一subject均绑定新digest，镜像内关键代码与提交逐字节一致。OSV保持178组/33个受影响package、13 critical和12组非`unimportant` critical，无新增finding；镜像内真实CandidateSnapshotter freeze wrapper probe和运行时工具面核对PASS。

最终独立只读安全复评合并重跑39项candidate/runtime测试并复算全部身份和扫描统计，结论为无阻塞finding：AC-LC-09、LC-17及新digest部署安全资格均PASS。实际部署仍为`BLOCKED_BY_AUTHORITY`；LC-16/LC-22和凭据、disposable Issue、模型费用等人工门不受本裁决影响，Level 0与全部远程写保持关闭。

## 2026-08-16 加固镜像部署与恢复复验

经精确授权，controller `86480b1f...c2c4` / digest `sha256:7edcb88b...66bf6`已导入master containerd并部署到Symphony、Artifact Broker和Gate Dispatcher。三者均产生新Pod UID，runtime image ID、Symphony/controller revision、routing patch和WORKFLOW摘要逐项匹配；Level 0、全部远程写和无Secret边界保持不变。Symphony/Broker不挂载Kubernetes token；Dispatcher关闭自动挂载但显式使用3600秒、固定API audience的projected ServiceAccount token，`create jobs=yes`、`get secrets=no`。21项Kubernetes合同测试与最终Level 0 smoke均PASS。

LC-14 Reviewer fixture在新接口上先因缺少可信invocation绑定参数失败，因此未把TypeError误记为安全拒绝；补齐phase、role、head和CandidateRevision后，从新镜像内重跑2,553条只读exact-candidate正反例全部PASS。专用fixture随后在Implementer receipt落盘后和review workspace物化后各替换一次Symphony Pod，分别证明validate/no-model恢复与唯一只读Reviewer workspace复用。独立评审首次裁决为FAIL：既有validate演练只重启Dispatcher，不满足等待Gate时重启Supervisor的AC-LC-08；同时指出Dispatcher token和fixture写入边界表述不准确。

补证在同一Gate Job主容器运行期间替换Symphony Pod。重启前后始终只有Job UID `20502fee-b09e-47da-85bf-942003af7c26`和Pod UID `a3fb0a62-8d21-4070-9de5-6e0d1a2b409b`；snapshot、CandidateRevision、turn receipt、全零预算与request身份不变，`run_model=false`。原Gate完成六阶段质量门禁后产生exact-identity PASS receipt，恢复后的controller进入review并消费request，Dispatcher随后清理原Job/Pod并写cleanup marker。文档同步修正Dispatcher projected token及fixture专用state、source workspace与共享review artifact边界；完整UID、摘要和命令见`evidence/2026-08-16-lc18-lc22-hardened-runtime.md`。

独立补证复评仍判定FAIL：AC-LC-08要求四个重启点，上述演练在等待Gate时重启后直接消费PASS receipt，后续review恢复不能替代“Gate PASS后、receipt消费前”的独立重启。第二次补证据此保持LC-22未关闭，并新增`prepare/capture/complete-post-pass`阶段。Gate `gate-gh-900024-ec915c1c2ac83fe6-0`以Job UID `ee8f220b...717151` / Pod UID `c0be4e7d...b9846`完整PASS；fixture先证明receipt durable但TaskSnapshot尚未消费且request保留，再重启Symphony。新Pod UID `d1438178...f809e`复验snapshot、receipt、CandidateRevision、turn receipt、Gate ID和全零预算不变、`run_model=false`，随后才由正式ValidatePhaseDriver消费原receipt并进入review。原Job/Pod已清理且cleanup marker落盘；四个AC-LC-08恢复点现均有实机证据，等待最终独立复评。

最终独立只读复评逐字节/逐字段复算pre/post snapshot、receipt、Job/Pod UID、request消费、cleanup marker和Gate namespace终态，并核对requirement、design、tasks、fixture与证据一致，结论为PASS、无阻塞finding。LC-22关闭；LC-16、LC-20和LC-21仍保持未关闭，Level 0与全部远程写能力不变。

本次未启动模型或App Server；最终`/proc`无Codex/App Server残留只证明无模型fixture边界，不能关闭LC-16。LC-02/LC-20/LC-21的凭据轮换、disposable Issue和模型费用人工门仍未满足，机器合同继续保持Level 0。

## 2026-08-17 LC-02短期凭据注入准备

只读集群审计确认`agentic-cicd` namespace没有 Secret，当前 Symphony仍使用非秘密哨兵`level0-no-github-access`且未挂ServiceAccount token；Pod内没有GitHub通用别名或model provider key。主机交互环境存在个人/provider凭据变量名，但本次没有读取值、复用或注入这些凭据。

仓库新增credentialed-observer overlay和固定Secret注入工具。base继续保留哨兵，overlay不生成Secret且只引用`symphony-github-token/token`；工具固定`kubernetes-admin@kubernetes`和namespace，token来源只能是`0400`/`0600`文件或非交互stdin管道，并把server dry-run与真实apply分成显式互斥模式。它拒绝TTY输入和冲突模式，未知参数与kubectl错误也不会回显潜在秘密。锁定Symphony`8001b52e...4d0`源码审计确认App Server同时通过Port环境清除和shell `unset`清除`GITHUB_TOKEN`、`GH_TOKEN`、两个Enterprise别名以及WORKFLOW配置引用的`JSTORE_SYMPHONY_GITHUB_TOKEN`。旧部署入口的routing patch摘要漂移一并收敛为从`symphony.lock.json`读取。

本次没有写入Secret、rollout Deployment、创建Issue或启动模型。凭据所有者仍未确认此前暴露凭据已经撤销/轮换，因此LC-02保持未完成，LC-20/LC-21继续阻塞。

独立安全复评随后发现两个Level 0阻塞：锁定Symphony的通用`github_api`仍向模型提供写方法，且credentialed部署没有强制执行token清除source preflight。routing patch现将工具面限制为GET，并对POST、PATCH、PUT、DELETE逐项证明在调用client前拒绝；最终patch SHA-256为`31be82e1...43622`，从固定archive按bridge、routing顺序执行check/apply均PASS。部署入口新增`--source-only`强制门，位置早于任何`sudo`、构建或集群写入，镜像revision校验也改为使用lock值。

真实锁定Symphony checkout的source-only preflight PASS；Elixir 1.19.5/OTP 28临时容器中的formatter和GitHub adapter聚焦测试为8项全PASS。仓库完整六阶段quality gate PASS，其中spec-dev 28项、governance 44项、tooling 198项，55个runtime classpath、55个Licensee模块、Gradle regression和58个发布制品许可证检查均通过。独立复评最终结论为PASS、无阻塞finding；LC-02仍因凭据所有者撤销/轮换确认和精确Secret/rollout授权缺失而保持未完成。本次未访问秘密值，未写Secret、rollout、Issue、GitHub状态或启动模型。

## 2026-08-17 取消Codex仓库级版本绑定

按人工要求，Codex App Server合同删除`0.146.0`精确版本字段，改为接受严格匹配`codex-cli X.Y.Z`的当前稳定版，并继续以v2 schema生成和初始化握手验证运行兼容性。Supervisor部署、独立镜像构建和Symphony审计入口均从宿主机`codex --version`取得实际稳定版，再把该精确版本显式传入Dockerfile；Dockerfile无默认版本且拒绝空值，因此不会隐式安装浮动latest。实际版本继续写入镜像tag、label、runtime revisions和source record，单个制品由完整镜像digest唯一绑定。

历史证据中的`codex-cli 0.146.0`描述已构建制品的真实身份，未被改写。该策略变更不扩大capability、GitHub权限或部署授权；LC-02状态不变，未构建最终运行镜像，也未执行Secret写入、rollout、Issue或模型turn。

验证中，本机`codex-cli 0.147.0`通过runtime稳定版检查，并完成无模型App Server v2 schema生成和Linux初始化握手；预发布版本负测保持fail-closed。Dockerfile的Codex stage以显式`CODEX_VERSION=0.147.0`执行cache-only构建，npm安装后精确输出`codex-cli 0.147.0`，未生成最终运行镜像。56项聚焦测试、Agentic CI/CD与治理合同、shell/Python语法、`git diff --check`和完整六阶段quality gate均PASS；宿主机完整runtime preflight仅因缺少Elixir/mise继续失败，不再因Codex版本失败。

## 2026-08-18 LC-20模型认证与费用门准备

官方Codex认证文档确认API Key适用于受信自动化，`codex login --with-api-key`产生的登录缓存可供CLI复用；`CODEX_API_KEY`只支持`codex exec`，不能据此宣称Symphony的`codex app-server`已认证。仓库因此新增固定`symphony-codex-auth`工具，只接受`0400`/`0600`且仅含一个非空`OPENAI_API_KEY`的JSON，以及经白名单验证、只保留选中HTTPS Responses provider的Codex配置，不打印凭据或provider URL；credentialed overlay通过非root init container准备Pod专用`.codex`目录，只读挂载`auth.json`和裁剪后的`config.toml`，不挂宿主机登录目录。部署完成后只运行不产生模型调用的`codex login status`就绪检查。

聚焦32项凭据/Kubernetes合同测试、真实Codex Secret server-side dry-run、credentialed overlay server-side dry-run和`git diff --check`均PASS。只读GitHub核验确认App仍限定到`ddd-mall/j-store`，但installation token权限仍为`issues: read`，无法创建disposable Issue；现有Issue中也没有重复的Agentic observer演练对象。另发现`max_cost_microusd=5000000`只存在于声明式合同，控制器没有可验证的实时费用熔断；OpenAI官方文档同时明确project spend alert只通知、不停止API请求，因此合同或alert都不能宣称真实5美元硬上限。LC-20保持未完成；本次未写Codex Secret、未rollout、未创建或标记Issue、未启动模型。

GitHub App所有者随后将Issues权限改为write，`ddd-mall`组织接受installation权限更新；新签发token实际返回`issues: write`且仍只覆盖`ddd-mall/j-store`。在无重复候选后创建disposable Issue `#50`，标题为`[Agent Goal]: 只读核对本地开发文档入口`，初始唯一标签`agent:candidate`，任务正文明确禁止文件、GitHub、部署和生产写入。创建完成后立即撤销该写token，再按installation token API显式请求Actions、Checks、Contents、Issues和Pull requests只读权限；新token只见目标仓库，返回`issues: read`，对Issue `#50`的真实PATCH负测被GitHub拒绝且标题未改变。LC-20仍未关闭：未写Kubernetes Secret、未rollout、未添加`agent:queued`且未启动模型。

宿主现有Codex认证使用API-Key登录缓存和自定义HTTPS Responses provider；Secret工具现将原始配置缩减为当前模型、推理强度及选中provider的名称和URL，拒绝内嵌凭据、非HTTPS、非Responses配置，并丢弃其他provider、MCP和策略设置。真实`auth.json`与`config.toml`通过目标集群server-side dry-run，未打印Key或provider URL，也未写Secret。聚焦凭据/Kubernetes合同34项、全部tooling 205项、Agentic CI/CD与治理合同、`git diff --check`及完整六阶段quality gate均PASS；后者包含55个runtime classpath、55个Licensee模块、Gradle回归和58个发布制品许可证检查。Symphony仍为0副本，Issue `#50`仍未调度，未发生模型调用。
