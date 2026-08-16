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

该候选证据当时尚不关闭LC-14；必须先构建并部署包含完成前复验的新controller，再从镜像内`/opt/jstore-agentic-controller`重跑并通过独立复评。真实模型费用授权和端到端路径仍归LC-21，review阶段重启恢复仍归LC-22。

## 最终镜像复验（2026-08-16）

包含修复（提交 `430187405481281c5dcd832dbc60e457b3f081ef`）的 controller 已构建为不可变镜像并部署：runtime manifest `sha256:305a2b8af0cdc38510b663436e17d6f47eba4b02a9c015010e64a3aa0084d1a9`，OCI 镜像 labels 与镜像内 `runtime-revisions` 清单绑定 controller revision `3a537df4dac461e40b12fcda46597b959ef24f52`、Symphony revision `8001b52e3062495a16e520e4ceaf8f9de868c4d0` 和 WORKFLOW SHA-256 `a8c18b98d5fbeb32dba03f522b4fb909c42f7cc7534a8d5607bb8d90e620fa5f`。复验在 Pod `symphony-5586ff8477-gnst9`（UID `4a8cdf05-2ba0-4715-ab63-bf399d0a126f`）内以镜像内代码执行，入口改为：

```bash
kubectl --context kubernetes-admin@kubernetes \
  -n agentic-cicd exec -i deploy/symphony -- \
  env PYTHONPATH=/opt/jstore-agentic-controller python3 - \
  < docs/spec/changes/agentic-cicd-local-candidate-loop/evidence/fixtures/2026-08-16-lc14-reviewer-smoke.py
```

镜像内 `runtime_controller.py`、`phase_bridge.py` 和 `controller.py` 的 SHA-256（`f116cba6deff2498...`、`a7d10fae1b412b70...`、`335c411f18a884b9...`）与 `origin/develop`（`88dc2460`）同路径文件逐字节一致。本验收脚本（develop 版本）SHA-256 为 `7fd4846728a39c57db54f0f2e118b93346bd4f6324a6e9b59f03cd61101fed1f`。受信 head `0d459263d4e95688ae8ceae9d758435f603dd57c` 经 Git bundle（SHA-256 `e43ed5d45a4e65c51ff1ea5388df5624e1ee50ff2bdf6e16195a80badc1895d1`）恢复到 `/tmp/lc14-review-smoke-source`。

脱敏输出：

```json
{
  "artifact_sha256": "0e2578548712f2966b38f7cd7a59dea8b9cd5fb98bac6a52475ae30cb6d4edac",
  "candidate_revision": "ec915c1c2ac83fe62f67b8af8a7a25c292c5064d08a595500ddefa22350c4358",
  "completion_tamper_rejected": true,
  "final_phase": "complete",
  "gate_id": "gate-gh-900001-ec915c1c2ac83fe6-5",
  "gate_receipt_sha256": "981a91d21e845cc6785c42fa50efd60128bbd59cc32cc2eed5d95618ac59a3da",
  "implementer_session_id": "lc14-implementer-session",
  "pod": "symphony-5586ff8477-gnst9",
  "read_only_entries": 2553,
  "result": "PASS",
  "review_decision_sha256": "d5486469c2603aa7f4a512465e4f2d674d9fc3fd032a2321d0e813c5ba5f9b48",
  "review_workspace": "/var/lib/candidate-artifacts/reviews/ec915c1c2ac83fe62f67b8af8a7a25c292c5064d08a595500ddefa22350c4358-0431a90c74b1ee7e",
  "reviewer_session_id": "lc14-reviewer-session",
  "same_session_rejected": true,
  "source_head": "0d459263d4e95688ae8ceae9d758435f603dd57c",
  "tamper_rejected": true,
  "turn_receipt_candidate_revision": "ec915c1c2ac83fe62f67b8af8a7a25c292c5064d08a595500ddefa22350c4358"
}
```

结果与候选验证逐项一致：receipt/decision SHA-256、CandidateRevision、2,553 个只读条目、直接写拒绝、完成前篡改拒绝和同 session 拒绝全部复现。复验只使用 `/tmp` state 与一次性 review 目录，脚本结束后删除；bundle 与恢复目录已从 Pod 清理。未启动 Codex/App Server 或模型，未写正式 Supervisor task state，未创建 Gate Job 或远程 Git/GitHub 状态，Level 0 能力未变化。

最终镜像复验已 PASS。独立合同/安全复评以独立命令逐项复现上述声明（镜像 digest/Pod UID、镜像内代码与 develop 逐字节一致、脚本 SHA-256、receipt/archive 身份、临时材料清理、能力合同与无 API token、decision SHA-256 重算一致），并排除 PYTHONPATH 混淆、跨 Issue receipt 复用和脚本捷径，结论为 PASS；非阻塞观察：证据措辞已改为「OCI 镜像 labels 与镜像内 runtime-revisions 清单」，脱敏输出现包含 `review_workspace` 字段。LC-14 由此关闭；真实模型 Reviewer 与 review 阶段重启恢复仍分别归 LC-21 和 LC-22。
