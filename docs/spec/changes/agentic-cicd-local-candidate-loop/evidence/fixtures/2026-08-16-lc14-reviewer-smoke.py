from __future__ import annotations

import hashlib
import json
import os
import shutil
import stat
import tempfile
from pathlib import Path

from agentic_cicd.candidate import CandidateRevision, CandidateSnapshotter
from agentic_cicd.coordinator import SnapshotStore, TaskSnapshot
from agentic_cicd.protocol import GateReceipt
from agentic_cicd.runtime_controller import (
    GateReceiptStore,
    PhaseContextStore,
    ReviewProposalStore,
    TurnStateController,
)


ISSUE = "GH-900001"
SOURCE = Path("/tmp/lc14-review-smoke-source")
ARTIFACT_ROOT = Path("/var/lib/candidate-artifacts")
RECEIPT_PATH = Path(
    "/var/lib/gate-exchange/receipts/gate-gh-900001-ec915c1c2ac83fe6-5.json"
)
REQUEST_PATH = Path(
    "/var/lib/gate-exchange/requests/gate-gh-900001-ec915c1c2ac83fe6-5.json"
)
CONTRACT_PATH = Path("/opt/config/agentic-cicd/state-contract.json")
IMPLEMENTER_SESSION = "lc14-implementer-session"
REVIEWER_SESSION = "lc14-reviewer-session"


def digest_json(payload: dict) -> str:
    encoded = json.dumps(payload, sort_keys=True, separators=(",", ":")).encode()
    return hashlib.sha256(encoded).hexdigest()


def make_writable(root: Path) -> None:
    for path in [root, *root.rglob("*")]:
        if not path.is_symlink():
            os.chmod(path, 0o755 if path.is_dir() else 0o644)


receipt_payload = json.loads(RECEIPT_PATH.read_text(encoding="utf-8"))
request_payload = json.loads(REQUEST_PATH.read_text(encoding="utf-8"))
receipt = GateReceipt.from_json(receipt_payload)
assert receipt.verdict == "PASS"
revision = CandidateRevision.from_json(receipt_payload["candidate_revision"])
head = os.popen(f"git -C {SOURCE} rev-parse HEAD").read().strip()
assert head == revision.base_sha

state_root = Path(tempfile.mkdtemp(prefix="lc14-review-state-"))
review_workspace: Path | None = None
try:
    snapshot_path = state_root / "tasks" / f"{ISSUE}.json"
    snapshot = TaskSnapshot(
        issue_identifier=ISSUE,
        state="queued",
        base_sha=head,
        head_sha=head,
        branch="codex/gh-900001-review-smoke",
        workspace=str(SOURCE.resolve()),
        iteration_phase="validate",
        implementer_session_id=IMPLEMENTER_SESSION,
        candidate_revision=revision.to_json(),
        gate_request=request_payload,
    )
    SnapshotStore(snapshot_path).save(snapshot)
    GateReceiptStore(
        state_root,
        contract_path=CONTRACT_PATH,
        gate_enabled=True,
    ).record(ISSUE, receipt_payload)

    context = PhaseContextStore(
        state_root,
        workspace_write_enabled=True,
        artifact_root=ARTIFACT_ROOT,
    ).load(ISSUE, SOURCE)
    review_workspace = Path(context.model_workspace)
    assert context.role == "reviewer"
    assert context.run_model and context.complete_turn
    assert context.thread_sandbox == "read-only"
    assert context.turn_sandbox_policy == {"type": "readOnly", "networkAccess": False}
    assert context.candidate_revision == revision.candidate_revision
    assert review_workspace != SOURCE.resolve()
    CandidateSnapshotter(SOURCE, ARTIFACT_ROOT).verify_materialized(
        revision, review_workspace
    )

    paths = [review_workspace, *review_workspace.rglob("*")]
    assert paths
    assert all(
        path.is_symlink()
        or not (stat.S_IMODE(path.stat().st_mode) & 0o222)
        for path in paths
    )
    regular_file = next(path for path in paths if path.is_file() and not path.is_symlink())
    try:
        regular_file.write_bytes(regular_file.read_bytes() + b"tamper")
    except PermissionError:
        tamper_rejected = True
    else:
        tamper_rejected = False
    assert tamper_rejected

    proposal = {
        "verdict": "PASS",
        "head_sha": head,
        "candidate_revision": revision.candidate_revision,
        "reviewer_role": "spec-evaluator",
        "findings": [],
    }
    ReviewProposalStore(state_root).submit(ISSUE, proposal)

    os.chmod(regular_file, 0o644)
    regular_file.write_bytes(regular_file.read_bytes() + b"tamper")
    try:
        TurnStateController(
            state_root,
            workspace_write_enabled=True,
            artifact_root=ARTIFACT_ROOT,
        ).complete_turn(
            ISSUE,
            SOURCE,
            session_id=REVIEWER_SESSION,
            thread_id="lc14-tamper-thread",
            turn_id="lc14-tamper-turn",
        )
    except Exception as error:
        completion_tamper_rejected = "materialized candidate" in str(error)
    else:
        completion_tamper_rejected = False
    assert completion_tamper_rejected

    make_writable(review_workspace)
    shutil.rmtree(review_workspace)
    restored = SnapshotStore(snapshot_path).load()
    restored.review_workspace = None
    SnapshotStore(snapshot_path).save(restored)
    context = PhaseContextStore(
        state_root,
        workspace_write_enabled=True,
        artifact_root=ARTIFACT_ROOT,
    ).load(ISSUE, SOURCE)
    review_workspace = Path(context.model_workspace)

    try:
        TurnStateController(
            state_root,
            workspace_write_enabled=True,
            artifact_root=ARTIFACT_ROOT,
        ).complete_turn(
            ISSUE,
            SOURCE,
            session_id=IMPLEMENTER_SESSION,
            thread_id="lc14-negative-thread",
            turn_id="lc14-negative-turn",
        )
    except RuntimeError as error:
        same_session_rejected = "must differ" in str(error)
    else:
        same_session_rejected = False
    assert same_session_rejected

    TurnStateController(
        state_root,
        workspace_write_enabled=True,
        artifact_root=ARTIFACT_ROOT,
    ).complete_turn(
        ISSUE,
        SOURCE,
        session_id=REVIEWER_SESSION,
        thread_id="lc14-review-thread",
        turn_id="lc14-review-turn",
    )
    completed = SnapshotStore(snapshot_path).load()
    decision = completed.review_decision_for(revision.candidate_revision)
    assert decision is not None
    assert completed.iteration_phase == "complete"
    assert completed.last_turn_receipt is not None
    assert completed.last_turn_receipt["role"] == "reviewer"
    assert completed.last_turn_receipt["candidate_revision"] == revision.candidate_revision
    assert decision.verdict == "PASS"
    assert decision.candidate_revision == revision.candidate_revision
    assert decision.implementer_session_id == IMPLEMENTER_SESSION
    assert decision.reviewer_session_id == REVIEWER_SESSION
    assert decision.reviewer_session_id != decision.implementer_session_id

    print(
        json.dumps(
            {
                "result": "PASS",
                "pod": os.environ.get("HOSTNAME", "unknown"),
                "source_head": head,
                "candidate_revision": revision.candidate_revision,
                "artifact_sha256": revision.artifact_sha256,
                "gate_id": receipt.gate_id,
                "gate_receipt_sha256": digest_json(receipt_payload),
                "review_workspace": str(review_workspace),
                "read_only_entries": len(paths),
                "tamper_rejected": tamper_rejected,
                "completion_tamper_rejected": completion_tamper_rejected,
                "same_session_rejected": same_session_rejected,
                "implementer_session_id": IMPLEMENTER_SESSION,
                "reviewer_session_id": REVIEWER_SESSION,
                "turn_receipt_candidate_revision": completed.last_turn_receipt[
                    "candidate_revision"
                ],
                "review_decision_sha256": digest_json(decision.to_json()),
                "final_phase": completed.iteration_phase,
            },
            indent=2,
            sort_keys=True,
        )
    )
finally:
    if review_workspace is not None and review_workspace.exists():
        make_writable(review_workspace)
        shutil.rmtree(review_workspace)
    shutil.rmtree(state_root, ignore_errors=True)
