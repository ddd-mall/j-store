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
