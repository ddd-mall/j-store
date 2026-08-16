# LC-14 Reviewer exact-candidate 集群验收

日期：2026-08-16  
集群：`kubernetes-admin@kubernetes` / `agentic-cicd`  
候选验收 Pod：`symphony-7987468f57-z98sg`
最终镜像复验 Pod：`symphony-5586ff8477-gnst9` / UID `4a8cdf05-2ba0-4715-ab63-bf399d0a126f`

## 目标与边界

该验收只运行 host-owned Python controller，不启动 Codex/App Server，不调用模型，也不改变 Level 0 能力。首版验收被独立复评判定无效，因为它手工构造review状态且跨Issue复用receipt；修复版改由正式`GateReceiptStore`消费同Issue的持久request/receipt，从validate进入review。候选复验先使用Pod临时模块验证修复；最终复验直接使用已部署不可变镜像内的`/opt/jstore-agentic-controller`。

最终运行身份：

- controller image ID：`docker.io/library/jstore-agentic-cicd@sha256:305a2b8af0cdc38510b663436e17d6f47eba4b02a9c015010e64a3aa0084d1a9`
- Symphony revision：`8001b52e3062495a16e520e4ceaf8f9de868c4d0`
- j-store controller revision：`3a537df4dac461e40b12fcda46597b959ef24f52`
- WORKFLOW SHA-256：`a8c18b98d5fbeb32dba03f522b4fb909c42f7cc7534a8d5607bb8d90e620fa5f`
- fixture SHA-256：`7fd4846728a39c57db54f0f2e118b93346bd4f6324a6e9b59f03cd61101fed1f`

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

最终镜像复验先将同一fixture复制到Pod临时目录并核对上述摘要，然后执行：

```bash
kubectl --context kubernetes-admin@kubernetes \
  -n agentic-cicd exec deploy/symphony -- \
  env PYTHONPATH=/opt/jstore-agentic-controller \
  python3 /tmp/lc14-reviewer-smoke.py
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
  "pod": "symphony-5586ff8477-gnst9",
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

不可变镜像内复验与独立代码/证据复核通过，LC-14关闭。55项聚焦测试、治理检查与完整`./scripts/quality-gate.sh`六阶段均PASS。验收后review目录、临时state、Git bundle和Pod临时源目录已删除；正式Supervisor task state和Gate namespace未变更。真实模型费用授权和端到端路径仍归LC-21，review阶段重启恢复仍归LC-22。

后续加固镜像`sha256:7edcb88b...66bf6`部署后，旧fixture因`complete-turn`新增可信invocation绑定参数而先失败；该失败不能作为篡改拒绝证据。fixture补齐phase、role、head和CandidateRevision绑定后，SHA-256更新为`6c4615894cf91b1b7be24f863a04b497ee9a1e136d321f848a5cb078ec5a3567`，并从新镜像内重新得到相同CandidateRevision、artifact、receipt与ReviewDecision摘要，2,553条只读检查及全部正反例PASS。新部署和LC-22恢复证据见`2026-08-16-lc18-lc22-hardened-runtime.md`。
