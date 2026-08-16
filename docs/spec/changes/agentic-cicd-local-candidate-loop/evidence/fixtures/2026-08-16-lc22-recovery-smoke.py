from __future__ import annotations

import argparse
import hashlib
import json
import os
import subprocess
from pathlib import Path

from agentic_cicd.candidate import CandidateRevision, CandidateSnapshotter
from agentic_cicd.coordinator import SnapshotStore, TaskSnapshot
from agentic_cicd.gate_runtime import GateMailbox, GatePolicy, ValidatePhaseDriver
from agentic_cicd.protocol import GateReceipt, GateRequest
from agentic_cicd.runtime_controller import (
    GateReceiptStore,
    PhaseContextStore,
    ReviewProposalStore,
    TurnStateController,
)


BASE_SHA = "0d459263d4e95688ae8ceae9d758435f603dd57c"
IMPLEMENT_ISSUE = "GH-900022"
VALIDATE_ISSUE = "GH-900023"
POST_PASS_ISSUE = "GH-900024"
REVIEW_ISSUE = "GH-900001"
SOURCE = Path("/var/lib/symphony/workspaces/lc22-recovery-source")
STATE_ROOT = Path("/var/lib/symphony/controller-fixtures/lc22-recovery")
ARTIFACT_ROOT = Path("/var/lib/candidate-artifacts")
CONTRACT_PATH = Path("/opt/config/agentic-cicd/state-contract.json")
REQUEST_PATH = Path(
    "/var/lib/gate-exchange/requests/gate-gh-900001-ec915c1c2ac83fe6-5.json"
)
RECEIPT_PATH = Path(
    "/var/lib/gate-exchange/receipts/gate-gh-900001-ec915c1c2ac83fe6-5.json"
)
EXCHANGE_ROOT = Path("/var/lib/gate-exchange")
VALIDATE_GATE_ID = "gate-gh-900023-ec915c1c2ac83fe6-0"
POST_PASS_GATE_ID = "gate-gh-900024-ec915c1c2ac83fe6-0"


def snapshot_path(issue: str) -> Path:
    return STATE_ROOT / "tasks" / f"{issue}.json"


def file_sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def source_head() -> str:
    result = subprocess.run(
        ["git", "rev-parse", "HEAD"],
        cwd=SOURCE,
        check=True,
        capture_output=True,
        text=True,
    )
    head = result.stdout.strip()
    if head != BASE_SHA:
        raise RuntimeError(f"fixture source HEAD is {head}, expected {BASE_SHA}")
    return head


def controller() -> TurnStateController:
    return TurnStateController(
        STATE_ROOT,
        workspace_write_enabled=True,
        artifact_root=ARTIFACT_ROOT,
    )


def context_store() -> PhaseContextStore:
    return PhaseContextStore(
        STATE_ROOT,
        workspace_write_enabled=True,
        artifact_root=ARTIFACT_ROOT,
    )


def print_result(stage: str, **evidence: object) -> None:
    print(
        json.dumps(
            {
                "result": "PASS",
                "stage": stage,
                "pod": os.environ.get("HOSTNAME", "unknown"),
                **evidence,
            },
            indent=2,
            sort_keys=True,
        )
    )


def gate_policy() -> GatePolicy:
    existing = GateRequest.from_json(
        json.loads(REQUEST_PATH.read_text(encoding="utf-8"))
    )
    return GatePolicy.from_json(
        {
            "runner_image": existing.runner_image,
            "fetch_image": existing.runner_image,
            "validation_commands": list(existing.validation_commands),
            "timeout_seconds": existing.timeout_seconds,
        }
    )


def prepare_implement() -> None:
    head = source_head()
    path = snapshot_path(IMPLEMENT_ISSUE)
    if path.exists():
        raise RuntimeError("implement recovery fixture state already exists")
    snapshot = TaskSnapshot(
        issue_identifier=IMPLEMENT_ISSUE,
        state="queued",
        base_sha=head,
        head_sha=head,
        branch="codex/gh-900022-lc22-recovery",
        workspace=str(SOURCE.resolve()),
        iteration_phase="implement",
    )
    SnapshotStore(path).save(snapshot)
    context = context_store().load(IMPLEMENT_ISSUE, SOURCE)
    if context.role != "implementer" or not context.run_model:
        raise RuntimeError("fixture did not enter the implementer model phase")
    controller().complete_turn(
        IMPLEMENT_ISSUE,
        SOURCE,
        session_id="lc22-implementer-session",
        thread_id="lc22-implementer-thread",
        turn_id="lc22-implementer-turn",
        expected_phase="implement",
        expected_role="implementer",
        expected_head_sha=head,
        expected_candidate_revision=None,
    )
    completed = SnapshotStore(path).load()
    recovered_context = context_store().load(IMPLEMENT_ISSUE, SOURCE)
    if (
        completed.iteration_phase != "validate"
        or completed.implementer_session_id != "lc22-implementer-session"
        or recovered_context.run_model
        or recovered_context.complete_turn
    ):
        raise RuntimeError("implement completion did not enter host-only validate")
    print_result(
        "prepare-implement",
        issue=IMPLEMENT_ISSUE,
        source_head=head,
        snapshot_sha256=file_sha256(path),
        next_phase=completed.iteration_phase,
        run_model_after_completion=recovered_context.run_model,
        implementer_session_id=completed.implementer_session_id,
    )


def verify_implement() -> None:
    head = source_head()
    path = snapshot_path(IMPLEMENT_ISSUE)
    before = file_sha256(path)
    context = context_store().load(IMPLEMENT_ISSUE, SOURCE)
    if context.phase != "validate" or context.run_model or context.complete_turn:
        raise RuntimeError("restart did not recover host-only validate")
    try:
        controller().complete_turn(
            IMPLEMENT_ISSUE,
            SOURCE,
            session_id="lc22-implementer-session",
            thread_id="lc22-implementer-thread",
            turn_id="lc22-implementer-turn",
            expected_phase="implement",
            expected_role="implementer",
            expected_head_sha=head,
            expected_candidate_revision=None,
        )
    except RuntimeError as error:
        duplicate_rejected = "phase no longer matches" in str(error)
    else:
        duplicate_rejected = False
    if not duplicate_rejected or file_sha256(path) != before:
        raise RuntimeError("implement callback replay changed recovered state")
    print_result(
        "verify-implement",
        issue=IMPLEMENT_ISSUE,
        source_head=head,
        snapshot_sha256=before,
        recovered_phase=context.phase,
        run_model=context.run_model,
        duplicate_callback_rejected=duplicate_rejected,
    )


def prepare_validate() -> None:
    head = source_head()
    path = snapshot_path(VALIDATE_ISSUE)
    if path.exists():
        raise RuntimeError("validate recovery fixture state already exists")
    existing_receipt = GateReceipt.from_json(
        json.loads(RECEIPT_PATH.read_text(encoding="utf-8"))
    )
    revision = existing_receipt.candidate_revision
    policy = gate_policy()
    request = GateRequest(
        gate_id=VALIDATE_GATE_ID,
        issue_identifier=VALIDATE_ISSUE,
        candidate_revision=revision,
        runner_image=policy.runner_image,
        command_policy_sha256=policy.command_policy_sha256,
        validation_commands=policy.validation_commands,
        timeout_seconds=policy.timeout_seconds,
        requested_at="2026-08-16T15:20:00Z",
    )
    last_turn_receipt = {
        "session_id": "lc22-validate-implementer-session",
        "thread_id": "lc22-validate-implementer-thread",
        "turn_id": "lc22-validate-implementer-turn",
        "role": "implementer",
        "head_sha": head,
    }
    snapshot = TaskSnapshot(
        issue_identifier=VALIDATE_ISSUE,
        state="queued",
        base_sha=head,
        head_sha=head,
        branch="codex/gh-900023-lc22-recovery",
        workspace=str(SOURCE.resolve()),
        iteration_phase="validate",
        implementer_session_id=last_turn_receipt["session_id"],
        last_turn_receipt=last_turn_receipt,
        candidate_revision=revision.to_json(),
        gate_request=request.to_json(),
        consumed_idempotency_keys={
            'turn:["lc22-validate-implementer-session",'
            '"lc22-validate-implementer-thread","lc22-validate-implementer-turn"]'
        },
    )
    SnapshotStore(path).save(snapshot)
    context = context_store().load(VALIDATE_ISSUE, SOURCE)
    if context.phase != "validate" or context.run_model or context.complete_turn:
        raise RuntimeError("pending gate did not retain host-only validate")
    GateMailbox(EXCHANGE_ROOT).publish_request(request)
    snapshot_digest = file_sha256(path)
    marker = {
        "snapshot_sha256": snapshot_digest,
        "candidate_revision": revision.candidate_revision,
        "gate_id": request.gate_id,
        "last_turn_receipt": last_turn_receipt,
        "budget": snapshot.budget.__dict__,
    }
    marker_path = STATE_ROOT / "evidence" / "validate-before-restart.json"
    marker_path.parent.mkdir(parents=True, exist_ok=True)
    marker_path.write_text(
        json.dumps(marker, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    print_result(
        "prepare-validate",
        issue=VALIDATE_ISSUE,
        source_head=head,
        candidate_revision=revision.candidate_revision,
        gate_id=request.gate_id,
        snapshot_sha256=snapshot_digest,
        last_turn_receipt=last_turn_receipt,
        budget=snapshot.budget.__dict__,
        run_model=context.run_model,
    )


def verify_validate() -> None:
    source_head()
    path = snapshot_path(VALIDATE_ISSUE)
    snapshot = SnapshotStore(path).load()
    marker = json.loads(
        (STATE_ROOT / "evidence" / "validate-before-restart.json").read_text(
            encoding="utf-8"
        )
    )
    context = context_store().load(VALIDATE_ISSUE, SOURCE)
    request = GateRequest.from_json(snapshot.gate_request or {})
    pending = [
        path.stem for path in GateMailbox(EXCHANGE_ROOT).pending_requests()
    ]
    if (
        file_sha256(path) != marker["snapshot_sha256"]
        or snapshot.candidate_revision is None
        or snapshot.candidate_revision["candidate_revision"]
        != marker["candidate_revision"]
        or request.gate_id != marker["gate_id"]
        or snapshot.last_turn_receipt != marker["last_turn_receipt"]
        or snapshot.budget.__dict__ != marker["budget"]
        or request.gate_id not in pending
        or context.phase != "validate"
        or context.run_model
        or context.complete_turn
    ):
        raise RuntimeError("Symphony restart changed pending gate identity")
    print_result(
        "verify-validate",
        issue=VALIDATE_ISSUE,
        candidate_revision=marker["candidate_revision"],
        gate_id=request.gate_id,
        snapshot_sha256=marker["snapshot_sha256"],
        last_turn_receipt=snapshot.last_turn_receipt,
        budget=snapshot.budget.__dict__,
        run_model=context.run_model,
        request_retained=True,
    )


def complete_validate() -> None:
    source_head()
    path = snapshot_path(VALIDATE_ISSUE)
    before = SnapshotStore(path).load()
    if before.gate_request is None or before.candidate_revision is None:
        raise RuntimeError("validate recovery fixture lost exact gate state")
    request = GateRequest.from_json(before.gate_request)
    mailbox = GateMailbox(EXCHANGE_ROOT)
    receipt = mailbox.read_receipt(request.gate_id)
    if receipt is None:
        raise RuntimeError("validate recovery receipt is not available")
    ValidatePhaseDriver(
        state_root=STATE_ROOT,
        artifact_root=ARTIFACT_ROOT,
        mailbox=mailbox,
        policy=gate_policy(),
        contract_path=CONTRACT_PATH,
        enabled=True,
    ).advance(VALIDATE_ISSUE, SOURCE)
    completed = SnapshotStore(path).load()
    request_path = EXCHANGE_ROOT / "requests" / f"{request.gate_id}.json"
    if (
        completed.iteration_phase != "review"
        or completed.candidate_revision != before.candidate_revision
        or completed.last_turn_receipt != before.last_turn_receipt
        or completed.budget != before.budget
        or completed.gate_receipt != receipt.to_json()
        or request_path.exists()
    ):
        raise RuntimeError("exact gate receipt did not preserve recovered identity")
    print_result(
        "complete-validate",
        issue=VALIDATE_ISSUE,
        candidate_revision=receipt.candidate_revision.candidate_revision,
        gate_id=receipt.gate_id,
        gate_verdict=receipt.verdict,
        job_uid=receipt.job_uid,
        gate_pod_uid=receipt.pod_uid,
        last_turn_receipt=completed.last_turn_receipt,
        budget=completed.budget.__dict__,
        final_phase=completed.iteration_phase,
        snapshot_sha256=file_sha256(path),
        request_consumed=True,
    )


def prepare_post_pass() -> None:
    head = source_head()
    path = snapshot_path(POST_PASS_ISSUE)
    if path.exists():
        raise RuntimeError("post-PASS recovery fixture state already exists")
    existing_receipt = GateReceipt.from_json(
        json.loads(RECEIPT_PATH.read_text(encoding="utf-8"))
    )
    revision = existing_receipt.candidate_revision
    policy = gate_policy()
    request = GateRequest(
        gate_id=POST_PASS_GATE_ID,
        issue_identifier=POST_PASS_ISSUE,
        candidate_revision=revision,
        runner_image=policy.runner_image,
        command_policy_sha256=policy.command_policy_sha256,
        validation_commands=policy.validation_commands,
        timeout_seconds=policy.timeout_seconds,
        requested_at="2026-08-16T15:40:00Z",
    )
    last_turn_receipt = {
        "session_id": "lc22-post-pass-implementer-session",
        "thread_id": "lc22-post-pass-implementer-thread",
        "turn_id": "lc22-post-pass-implementer-turn",
        "role": "implementer",
        "head_sha": head,
    }
    snapshot = TaskSnapshot(
        issue_identifier=POST_PASS_ISSUE,
        state="queued",
        base_sha=head,
        head_sha=head,
        branch="codex/gh-900024-lc22-recovery",
        workspace=str(SOURCE.resolve()),
        iteration_phase="validate",
        implementer_session_id=last_turn_receipt["session_id"],
        last_turn_receipt=last_turn_receipt,
        candidate_revision=revision.to_json(),
        gate_request=request.to_json(),
        consumed_idempotency_keys={
            'turn:["lc22-post-pass-implementer-session",'
            '"lc22-post-pass-implementer-thread",'
            '"lc22-post-pass-implementer-turn"]'
        },
    )
    SnapshotStore(path).save(snapshot)
    context = context_store().load(POST_PASS_ISSUE, SOURCE)
    if context.phase != "validate" or context.run_model or context.complete_turn:
        raise RuntimeError("post-PASS fixture did not enter host-only validate")
    GateMailbox(EXCHANGE_ROOT).publish_request(request)
    print_result(
        "prepare-post-pass",
        issue=POST_PASS_ISSUE,
        candidate_revision=revision.candidate_revision,
        gate_id=request.gate_id,
        snapshot_sha256=file_sha256(path),
        last_turn_receipt=last_turn_receipt,
        budget=snapshot.budget.__dict__,
        run_model=context.run_model,
    )


def capture_post_pass() -> None:
    source_head()
    path = snapshot_path(POST_PASS_ISSUE)
    snapshot = SnapshotStore(path).load()
    if snapshot.gate_request is None or snapshot.candidate_revision is None:
        raise RuntimeError("post-PASS fixture lost pending gate identity")
    request = GateRequest.from_json(snapshot.gate_request)
    receipt_path = EXCHANGE_ROOT / "receipts" / f"{request.gate_id}.json"
    receipt = GateMailbox(EXCHANGE_ROOT).read_receipt(request.gate_id)
    context = context_store().load(POST_PASS_ISSUE, SOURCE)
    request_path = EXCHANGE_ROOT / "requests" / f"{request.gate_id}.json"
    if (
        receipt is None
        or receipt.verdict != "PASS"
        or receipt.candidate_revision.to_json() != snapshot.candidate_revision
        or snapshot.gate_receipt is not None
        or not request_path.exists()
        or context.phase != "validate"
        or context.run_model
        or context.complete_turn
    ):
        raise RuntimeError("PASS receipt was consumed before restart boundary")
    marker = {
        "snapshot_sha256": file_sha256(path),
        "candidate_revision": receipt.candidate_revision.candidate_revision,
        "gate_id": receipt.gate_id,
        "last_turn_receipt": snapshot.last_turn_receipt,
        "budget": snapshot.budget.__dict__,
        "receipt_sha256": file_sha256(receipt_path),
        "receipt": receipt.to_json(),
    }
    marker_path = STATE_ROOT / "evidence" / "post-pass-before-restart.json"
    marker_path.write_text(
        json.dumps(marker, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    print_result(
        "capture-post-pass",
        issue=POST_PASS_ISSUE,
        candidate_revision=marker["candidate_revision"],
        gate_id=receipt.gate_id,
        gate_verdict=receipt.verdict,
        job_uid=receipt.job_uid,
        gate_pod_uid=receipt.pod_uid,
        snapshot_sha256=marker["snapshot_sha256"],
        receipt_sha256=marker["receipt_sha256"],
        request_retained=True,
        receipt_not_consumed=True,
        run_model=context.run_model,
    )


def complete_post_pass() -> None:
    source_head()
    path = snapshot_path(POST_PASS_ISSUE)
    before = SnapshotStore(path).load()
    marker = json.loads(
        (STATE_ROOT / "evidence" / "post-pass-before-restart.json").read_text(
            encoding="utf-8"
        )
    )
    if before.gate_request is None or before.candidate_revision is None:
        raise RuntimeError("post-PASS restart lost pending gate identity")
    request = GateRequest.from_json(before.gate_request)
    mailbox = GateMailbox(EXCHANGE_ROOT)
    receipt = mailbox.read_receipt(request.gate_id)
    receipt_path = EXCHANGE_ROOT / "receipts" / f"{request.gate_id}.json"
    request_path = EXCHANGE_ROOT / "requests" / f"{request.gate_id}.json"
    context = context_store().load(POST_PASS_ISSUE, SOURCE)
    if (
        receipt is None
        or receipt.to_json() != marker["receipt"]
        or file_sha256(receipt_path) != marker["receipt_sha256"]
        or file_sha256(path) != marker["snapshot_sha256"]
        or before.candidate_revision["candidate_revision"]
        != marker["candidate_revision"]
        or before.last_turn_receipt != marker["last_turn_receipt"]
        or before.budget.__dict__ != marker["budget"]
        or before.gate_receipt is not None
        or not request_path.exists()
        or context.phase != "validate"
        or context.run_model
        or context.complete_turn
    ):
        raise RuntimeError("restart changed durable unconsumed PASS receipt state")
    ValidatePhaseDriver(
        state_root=STATE_ROOT,
        artifact_root=ARTIFACT_ROOT,
        mailbox=mailbox,
        policy=gate_policy(),
        contract_path=CONTRACT_PATH,
        enabled=True,
    ).advance(POST_PASS_ISSUE, SOURCE)
    completed = SnapshotStore(path).load()
    if (
        completed.iteration_phase != "review"
        or completed.candidate_revision != before.candidate_revision
        or completed.last_turn_receipt != before.last_turn_receipt
        or completed.budget != before.budget
        or completed.gate_receipt != receipt.to_json()
        or request_path.exists()
    ):
        raise RuntimeError("post-restart receipt consumption changed exact identity")
    print_result(
        "complete-post-pass",
        issue=POST_PASS_ISSUE,
        candidate_revision=receipt.candidate_revision.candidate_revision,
        gate_id=receipt.gate_id,
        gate_verdict=receipt.verdict,
        job_uid=receipt.job_uid,
        gate_pod_uid=receipt.pod_uid,
        receipt_sha256=marker["receipt_sha256"],
        snapshot_before_restart_sha256=marker["snapshot_sha256"],
        snapshot_after_consumption_sha256=file_sha256(path),
        last_turn_receipt=completed.last_turn_receipt,
        budget=completed.budget.__dict__,
        final_phase=completed.iteration_phase,
        receipt_survived_restart=True,
        request_consumed=True,
    )


def prepare_review() -> None:
    head = source_head()
    path = snapshot_path(REVIEW_ISSUE)
    if path.exists():
        raise RuntimeError("review recovery fixture state already exists")
    request_payload = json.loads(REQUEST_PATH.read_text(encoding="utf-8"))
    receipt_payload = json.loads(RECEIPT_PATH.read_text(encoding="utf-8"))
    receipt = GateReceipt.from_json(receipt_payload)
    revision = CandidateRevision.from_json(receipt_payload["candidate_revision"])
    if revision.base_sha != head or receipt.verdict != "PASS":
        raise RuntimeError("review fixture evidence is not the expected PASS candidate")
    snapshot = TaskSnapshot(
        issue_identifier=REVIEW_ISSUE,
        state="queued",
        base_sha=head,
        head_sha=head,
        branch="codex/gh-900001-lc22-recovery",
        workspace=str(SOURCE.resolve()),
        iteration_phase="validate",
        implementer_session_id="lc22-review-implementer-session",
        candidate_revision=revision.to_json(),
        gate_request=request_payload,
    )
    SnapshotStore(path).save(snapshot)
    GateReceiptStore(
        STATE_ROOT,
        contract_path=CONTRACT_PATH,
        gate_enabled=True,
    ).record(REVIEW_ISSUE, receipt_payload)
    context = context_store().load(REVIEW_ISSUE, SOURCE)
    review_workspace = Path(context.model_workspace)
    CandidateSnapshotter(SOURCE, ARTIFACT_ROOT).verify_materialized(
        revision, review_workspace
    )
    if context.role != "reviewer" or not context.run_model:
        raise RuntimeError("PASS gate did not enter the reviewer phase")
    review_directories = sorted(
        path.name for path in (ARTIFACT_ROOT / "reviews").iterdir()
    )
    print_result(
        "prepare-review",
        issue=REVIEW_ISSUE,
        source_head=head,
        candidate_revision=revision.candidate_revision,
        gate_id=receipt.gate_id,
        snapshot_sha256=file_sha256(path),
        review_workspace=str(review_workspace),
        review_directories=review_directories,
    )


def verify_review() -> None:
    head = source_head()
    path = snapshot_path(REVIEW_ISSUE)
    snapshot = SnapshotStore(path).load()
    if snapshot.candidate_revision is None or snapshot.review_workspace is None:
        raise RuntimeError("review recovery fixture has no candidate workspace")
    revision = CandidateRevision.from_json(snapshot.candidate_revision)
    before_workspace = snapshot.review_workspace
    before_directories = sorted(
        path.name for path in (ARTIFACT_ROOT / "reviews").iterdir()
    )
    context = context_store().load(REVIEW_ISSUE, SOURCE)
    after_directories = sorted(
        path.name for path in (ARTIFACT_ROOT / "reviews").iterdir()
    )
    if (
        context.model_workspace != before_workspace
        or before_directories != after_directories
        or context.role != "reviewer"
        or not context.run_model
    ):
        raise RuntimeError("review restart created a duplicate model workspace")
    CandidateSnapshotter(SOURCE, ARTIFACT_ROOT).verify_materialized(
        revision, Path(context.model_workspace)
    )
    ReviewProposalStore(STATE_ROOT).submit(
        REVIEW_ISSUE,
        {
            "verdict": "PASS",
            "head_sha": head,
            "candidate_revision": revision.candidate_revision,
            "reviewer_role": "spec-evaluator",
            "findings": [],
        },
    )
    controller().complete_turn(
        REVIEW_ISSUE,
        SOURCE,
        session_id="lc22-reviewer-session",
        thread_id="lc22-reviewer-thread",
        turn_id="lc22-reviewer-turn",
        expected_phase="review",
        expected_role="reviewer",
        expected_head_sha=head,
        expected_candidate_revision=revision.candidate_revision,
    )
    completed = SnapshotStore(path).load()
    decision = completed.review_decision_for(revision.candidate_revision)
    if completed.iteration_phase != "complete" or decision is None:
        raise RuntimeError("recovered reviewer did not produce one decision")
    completed_sha256 = file_sha256(path)
    try:
        controller().complete_turn(
            REVIEW_ISSUE,
            SOURCE,
            session_id="lc22-reviewer-session",
            thread_id="lc22-reviewer-thread",
            turn_id="lc22-reviewer-turn",
            expected_phase="review",
            expected_role="reviewer",
            expected_head_sha=head,
            expected_candidate_revision=revision.candidate_revision,
        )
    except RuntimeError as error:
        duplicate_rejected = "phase no longer matches" in str(error)
    else:
        duplicate_rejected = False
    if not duplicate_rejected or file_sha256(path) != completed_sha256:
        raise RuntimeError("review callback replay changed completed state")
    print_result(
        "verify-review",
        issue=REVIEW_ISSUE,
        source_head=head,
        candidate_revision=revision.candidate_revision,
        recovered_workspace=before_workspace,
        review_workspace_reused=True,
        duplicate_callback_rejected=duplicate_rejected,
        reviewer_session_id=decision.reviewer_session_id,
        implementer_session_id=decision.implementer_session_id,
        final_phase=completed.iteration_phase,
        snapshot_sha256=completed_sha256,
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "stage",
        choices=(
            "prepare-implement",
            "verify-implement",
            "prepare-validate",
            "verify-validate",
            "complete-validate",
            "prepare-post-pass",
            "capture-post-pass",
            "complete-post-pass",
            "prepare-review",
            "verify-review",
        ),
    )
    arguments = parser.parse_args()
    {
        "prepare-implement": prepare_implement,
        "verify-implement": verify_implement,
        "prepare-validate": prepare_validate,
        "verify-validate": verify_validate,
        "complete-validate": complete_validate,
        "prepare-post-pass": prepare_post_pass,
        "capture-post-pass": capture_post_pass,
        "complete-post-pass": complete_post_pass,
        "prepare-review": prepare_review,
        "verify-review": verify_review,
    }[arguments.stage]()


if __name__ == "__main__":
    main()
