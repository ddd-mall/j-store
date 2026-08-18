# LC-18 加固镜像部署与 LC-22 恢复演练

日期：2026-08-16  
集群：`kubernetes-admin@kubernetes`  
namespace：`agentic-cicd` / `agentic-cicd-gates`

## 范围与边界

本次经人工授权将已通过 LC-17 独立安全资格审查的 controller 镜像部署到开发集群，并用可信无模型 fixture补齐implement完成后、等待Gate、Gate PASS后和等待review四个恢复点。未注入 GitHub 或模型凭据，未启动 Codex/App Server，未修改正式 Supervisor task state，未创建远程 branch、commit或 PR。机器合同继续为 Level 0，`local_workspace_write`、`freeze_local_candidate`、`run_isolated_gate`和全部远程写仍为 false。

## 不可变运行时身份

- controller image：`docker.io/library/jstore-agentic-cicd@sha256:7edcb88bd99edd88bf07659147beade6119a73f758807a6cd47bc99661566bf6`
- controller revision：`86480b1f3819312b3cb4ee978a094f95d81dc2c4`
- Symphony revision：`8001b52e3062495a16e520e4ceaf8f9de868c4d0`
- routing patch SHA-256：`00af6b18e85565de63b9535281ae2bf4c9f8f44744be27bf9db73ba15f69fbe2`
- WORKFLOW ConfigMap：`symphony-workflow-km2247kkb5`
- WORKFLOW SHA-256：`ca821efe0c5ed3c495f227ef68b9d4b6cebf785e16b22a34f29710420c8e344d`
- Gate Runner：`docker.io/library/jstore-agentic-gate@sha256:30f48b2ef512c0d7be8657637d718f663c8b8eb78843cc5603e10234ee152334`

部署后首次 Pod UID：

```text
symphony         acd54098-ac5d-4b6d-b223-4503a89ac0f3
artifact-broker  cdda5a0a-057e-49f0-bdfa-54faa00bf0c5
gate-dispatcher  55788b8f-ecae-486c-823c-6ab30801adb9
```

三者 spec image与runtime image ID均匹配上述controller digest，均 Ready且零重启。三个Pod都设置`automountServiceAccountToken: false`且没有Secret引用；Symphony和Artifact Broker没有挂载Kubernetes token。Gate Dispatcher没有自动挂载token，但显式挂载仅面向`https://kubernetes.default.svc.cluster.local`、有效期3600秒的projected ServiceAccount token，以执行受限Job控制面操作。Symphony SA的`create jobs`和Dispatcher SA的`get secrets`均返回`no`，Dispatcher的最小`create jobs`权限返回`yes`。`kube-router-firewall`保持固定digest并为`2/2` Ready。

## Reviewer exact-candidate 复验

旧LC-14 fixture首次在新镜像上因缺少新的invocation-binding参数失败；这次失败不能作为篡改拒绝证据。fixture随后显式绑定`expected_phase`、`expected_role`、`expected_head_sha`和`expected_candidate_revision`，当前SHA-256为：

```text
6c4615894cf91b1b7be24f863a04b497ee9a1e136d321f848a5cb078ec5a3567
```

修正后从镜像内`/opt/jstore-agentic-controller`重跑通过：CandidateRevision `ec915c1c...0c4358`、artifact `0e257854...6d4edac`和Gate receipt摘要保持不变；2,553个条目只读，直接写入和chmod后完成前篡改均被拒绝，同session被拒绝，独立reviewer session生成同一CandidateRevision的PASS decision。

## Implement 恢复点

可信fixture `fixtures/2026-08-16-lc22-recovery-smoke.py` SHA-256为：

```text
bbf4aadf840dfad86731c07c4e149c918562a668fd3e689e91d3e42f11aa4865
```

fixture把测试task state限定在专用路径`/var/lib/symphony/controller-fixtures/lc22-recovery`，使用隔离的fixture source workspace `/var/lib/symphony/workspaces/lc22-recovery-source`，并在正式共享artifact根的`/var/lib/candidate-artifacts/reviews`下物化和复验只读Reviewer workspace。它先在Pod `symphony-6bc6676d6-ks29l`完成Implementer receipt，将phase持久化为`validate`并得到snapshot SHA-256：

```text
00257fd94afafde1a495a8eecfb55ff7aa452e03b636a6bcd5c2c9aae05d9076
```

Deployment重启后，新Pod `symphony-67b9574b-7ppnc` / UID `4f1175f8-f4fa-4dda-9a10-4a4410a59d52`恢复同一snapshot摘要。`PhaseContextStore`返回`phase=validate`、`run_model=false`、`complete_turn=false`；旧Implementer callback因可信invocation phase不再匹配而被拒绝，snapshot字节未变化。

## Validate 等待 Gate 恢复点

独立评审指出既有validate证据只在等待期间重启Dispatcher，不能满足AC-LC-08要求的Supervisor重启。补证使用同一fixture为Issue `GH-900023`发布Gate request；重启前snapshot SHA-256为`aad19a8768f58575f834c615643c9ce039042457d78e2e838ff30947f8ed277a`，CandidateRevision为`ec915c1c2ac83fe62f67b8af8a7a25c292c5064d08a595500ddefa22350c4358`。Gate主容器在worker1运行期间，集群身份固定为：

```text
gate ID   gate-gh-900023-ec915c1c2ac83fe6-0
Job UID   20502fee-b09e-47da-85bf-942003af7c26
Pod UID   a3fb0a62-8d21-4070-9de5-6e0d1a2b409b
```

等待期间将Symphony从Pod UID `e8890770-1214-4241-a279-1da9d543310a`替换为`symphony-544c4f44cb-5tbw5` / UID `02264d9e-c587-418d-9cf8-37e379a4035c`。重启后fixture确认snapshot字节、CandidateRevision、Gate ID、Implementer turn receipt和全零预算均不变，`phase=validate`、`run_model=false`，request仍待处理；集群仍只有上述同一Job UID和Pod UID，没有第二份Gate。

原Gate随后完成完整六阶段`./scripts/quality-gate.sh`：212个Gradle任务、58个发布JAR许可证验证以及全部本地门禁PASS。`complete-validate`消费原exact-identity receipt并得到`verdict=PASS`，保持CandidateRevision、turn receipt和全零预算不变，phase进入`review`，request被消费；完成态snapshot SHA-256为`4323cb63ac29e13ede341a9db4c269ab2945774d196775aa5f348c396a11eb33`。receipt SHA-256为`6bb256da20f5b7185dfdd37d9f8ecbbc01e31cbafeca42e5d0439e0c43a69c66`。Dispatcher以前台删除原Job/Pod后写入cleanup marker，摘要为`0f675a94c3e6503127ffd277b5d418c850c69c3ab4d136e0ba1b19d13c6c63f3`；Gate namespace最终无残留资源。

## Gate PASS 后恢复点

首次补证仍在receipt到达后直接消费，独立复评据AC-LC-08判定FAIL：等待Gate和Gate PASS后是两个独立恢复点，不能用后续等待review演练替代。第二次补证使用Issue `GH-900024`和Gate ID `gate-gh-900024-ec915c1c2ac83fe6-0`。启动snapshot SHA-256为`13a93e634fc8148e3087c63aeadc680006106464a6154696f3b4449332e06b7b`，预算全零且`run_model=false`；独立Gate身份为：

```text
Job UID   ee8f220b-bf90-4c01-bb5e-0a6d25717151
Pod UID   c0be4e7d-168c-461b-8175-056bca3b9846
```

该Gate完成六阶段质量门禁并以退出码0结束。`capture-post-pass`在不调用`ValidatePhaseDriver`的情况下确认PASS receipt已持久化、TaskSnapshot的`gate_receipt`仍为空、request仍存在、phase仍为`validate`且不运行模型；receipt SHA-256为`7af5905c999cc339bce39d0bbd065bb10280a1add936e0ba11acf3c84e5cbed5`，snapshot字节仍为`13a93e...e06b7b`。

随后将Symphony从`symphony-544c4f44cb-5tbw5` / UID `02264d9e-c587-418d-9cf8-37e379a4035c`替换为`symphony-65bff76d94-2cwjm` / UID `d1438178-c386-4109-a322-54569f8f809e`。新Pod先验证receipt、snapshot、CandidateRevision、Gate ID、Implementer turn receipt、request和全零预算逐字节/逐字段不变，且`run_model=false`；随后才由正式`ValidatePhaseDriver.advance`消费原receipt并进入`review`。消费后snapshot SHA-256为`43ecd9a0ec4d5d9066cb7645ea37bee748eee95127e6210d0b258b5ce35d984e`，request已移除，receipt中的Job/Pod UID保持上述身份。Dispatcher cleanup marker SHA-256为`96ff4caee5e0150e1321f436112558a35e3afb9940c0ae88c18f452deba470d2`，Gate namespace无残留Job/Pod。

## Review 恢复点

同一fixture使用已保留的同Issue PASS Gate request/receipt，经正式`GateReceiptStore`进入review并物化唯一只读workspace：

```text
/var/lib/candidate-artifacts/reviews/ec915c1c2ac83fe62f67b8af8a7a25c292c5064d08a595500ddefa22350c4358-c836bbffa500f7dc
```

重启前snapshot SHA-256为`d57e57e1db367720bf0ff2f261dc1ac83db21864ebfa66f49371872a30097c35`。Deployment再次重启后，新Pod `symphony-9b79577f8-6w696` / UID `e8890770-1214-4241-a279-1da9d543310a`恢复并复用完全相同的workspace，review目录集合未增加；CandidateSnapshotter再次逐条验证exact archive。独立reviewer完成唯一PASS decision，重复callback被拒绝且完成态snapshot保持：

```text
5728b277e590cfd9a3de08a32145b4a3d9ab3f25f73604b24dd7f95aa8e8d1f5
```

## 回归与结论

- `python3 -m unittest tests.tooling.test_agentic_cicd_kubernetes`：21项PASS；
- phase/gate/runtime/candidate聚焦组合：53项PASS；
- 最终`agentic-cicd-kubernetes-smoke.sh`：PASS；
- `agentic-cicd-gates`无残留Job/Pod；j-store、Redis、PostgreSQL均Ready且零重启；
- 最终Pod的`/proc/*/cmdline`只有Symphony/Erlang及本次检查进程，无Codex/App Server残留。

真实Gate证据覆盖等待Gate时重启Symphony并复用同一Job/Pod，以及PASS receipt消费前重启后保持exact identity；本地正式合同测试与既有集群证据覆盖Gate FAIL、new revision、Review FAIL、旧callback重放、历史decision保留和同根因第三次熔断。implement完成后、等待Gate、Gate PASS后和等待review四个实机恢复点均已执行。独立最终复评逐项复算receipt、pre/post snapshot、Job/Pod UID、cleanup marker和终态后判定PASS、无阻塞finding，LC-22关闭。

本次无模型演练没有启动真实turn，不能关闭LC-16的真实单turn/无第二App Server验收，也不满足LC-20/LC-21的GitHub token、disposable Issue和模型费用人工门。
