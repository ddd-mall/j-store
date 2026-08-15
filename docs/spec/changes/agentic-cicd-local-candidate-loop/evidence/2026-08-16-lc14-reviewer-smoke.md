# LC-14 Reviewer exact-candidate 集群验收

日期：2026-08-16  
集群：`kubernetes-admin@kubernetes` / `agentic-cicd`  
执行 Pod：`symphony-7987468f57-z98sg`

## 目标与边界

该验收只运行 host-owned Python controller，不启动 Codex/App Server，不调用模型，也不改变 Level 0 能力。首版验收被独立复评判定无效，因为它手工构造review状态且跨Issue复用receipt；修复版改由正式`GateReceiptStore`消费同Issue的持久request/receipt，从validate进入review。候选controller模块暂存到Pod的`/tmp/lc14-controller`运行，用于在构建新镜像前验证修复；因此本记录当前是候选验证，不是已部署镜像验收。

输入是既有 Gate PASS receipt `gate-gh-900001-ec915c1c2ac83fe6-5.json`：

- Git head / CandidateRevision base：`0d459263d4e95688ae8ceae9d758435f603dd57c`
- CandidateRevision：`ec915c1c2ac83fe62f67b8af8a7a25c292c5064d08a595500ddefa22350c4358`
- artifact SHA-256：`0e2578548712f2966b38f7cd7a59dea8b9cd5fb98bac6a52475ae30cb6d4edac`
- 规范化 receipt JSON SHA-256：`981a91d21e845cc6785c42fa50efd60128bbd59cc32cc2eed5d95618ac59a3da`

Git bundle只用于在 `/tmp/lc14-review-smoke-source` 恢复同一受信 head；Reviewer读取的是 CandidateRevision archive，而不是该可变 workspace。request和receipt的精确脱敏JSON也保存在`fixtures/2026-08-16-lc14-gate-{request,receipt}.json`。验收脚本为 `fixtures/2026-08-16-lc14-reviewer-smoke.py`；其SHA-256在每次最终镜像复验后重新记录。

执行入口：

```bash
kubectl --context kubernetes-admin@kubernetes \
  -n agentic-cicd exec -i deploy/symphony -- \
  env PYTHONPATH=/tmp/lc14-controller python3 - \
  < docs/spec/changes/agentic-cicd-local-candidate-loop/evidence/fixtures/2026-08-16-lc14-reviewer-smoke.py
```

## 脱敏输出

```json
{
  "artifact_sha256": "0e2578548712f2966b38f7cd7a59dea8b9cd5fb98bac6a52475ae30cb6d4edac",
  "candidate_revision": "ec915c1c2ac83fe62f67b8af8a7a25c292c5064d08a595500ddefa22350c4358",
  "completion_tamper_rejected": true,
  "final_phase": "complete",
  "gate_id": "gate-gh-900001-ec915c1c2ac83fe6-5",
  "gate_receipt_sha256": "981a91d21e845cc6785c42fa50efd60128bbd59cc32cc2eed5d95618ac59a3da",
  "implementer_session_id": "lc14-implementer-session",
  "pod": "symphony-7987468f57-z98sg",
  "read_only_entries": 2553,
  "result": "PASS",
  "review_decision_sha256": "d5486469c2603aa7f4a512465e4f2d674d9fc3fd032a2321d0e813c5ba5f9b48",
  "reviewer_session_id": "lc14-reviewer-session",
  "same_session_rejected": true,
  "source_head": "0d459263d4e95688ae8ceae9d758435f603dd57c",
  "tamper_rejected": true,
  "turn_receipt_candidate_revision": "ec915c1c2ac83fe62f67b8af8a7a25c292c5064d08a595500ddefa22350c4358"
}
```

## 结论与清理

- `GateReceiptStore`先校验同一Issue的active request、PASS receipt及完整CandidateRevision身份，并执行正式validate→review转移；`PhaseContextStore`恢复时再次重验这些证据。
- `PhaseContextStore`从artifact storage物化独立目录并逐条校验archive内容、类型和只读模式；2,553个目录/文件/链接均无写位。直接写入得到`PermissionError`；随后同UID先chmod再篡改虽成功，但`TurnStateController`在接收review turn前重新校验archive并拒绝完成。
- implementer session作为 reviewer receipt身份被 `SymphonyPhaseBridge`拒绝；独立 reviewer session成功生成 host-owned TurnReceipt和 ReviewDecision。
- proposal、receipt与 decision的 CandidateRevision完全相同，最终 phase为 `complete`。
- 验收只使用 `/tmp` state和一次性 review目录；脚本结束时恢复权限并删除 review目录、临时 state。未写入正式 Supervisor task state，未创建 Gate Job或远程 Git/GitHub状态。

该候选证据尚不关闭LC-14；必须先把包含完成前复验的新controller构建为不可变digest、部署后从镜像内`/opt/jstore-agentic-controller`重跑并通过独立复评。真实模型费用授权和端到端路径仍归LC-21，review阶段重启恢复仍归LC-22。
