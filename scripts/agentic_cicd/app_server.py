from __future__ import annotations

import json
from pathlib import Path
from typing import Any, TextIO

from .protocol import IterationPacket, REVIEW_PROPOSAL_SCHEMA


IMPLEMENTER_INSTRUCTIONS = """You are the Implementer for one bounded j-store iteration.
Use the supplied IterationPacket and repository specifications as product intent. Follow TDD,
modify only the isolated workspace, run only the listed validation commands, and report evidence.
Do not push, create or modify pull requests, approve, merge, release, access production, or request
additional privileges. Your statements never replace deterministic gate results or independent review.
"""

REVIEWER_INSTRUCTIONS = """You are an Independent Reviewer for one fixed j-store candidate SHA.
Do not modify files. Check acceptance coverage, requirement drift, implementation quality, security,
and validation evidence. Return only the requested structured ReviewProposal. Do not invent session,
thread, or turn identities; the Symphony host binds those from its trusted turn receipt. Every FAIL finding must
have a stable root_cause_id and actionable verification. PASS is valid only for the supplied head SHA.
"""


class AppServerError(RuntimeError):
    """Raised for an app-server protocol or remote request error."""


class JsonLineTransport:
    """Minimal newline-delimited JSON transport used by Codex app-server stdio."""

    def __init__(self, reader: TextIO, writer: TextIO):
        self.reader = reader
        self.writer = writer

    def send(self, message: dict[str, Any]) -> None:
        self.writer.write(json.dumps(message, separators=(",", ":"), sort_keys=True) + "\n")
        self.writer.flush()

    def receive(self) -> dict[str, Any]:
        while True:
            line = self.reader.readline()
            if line == "":
                raise AppServerError("app-server transport closed before a response arrived")
            if not line.strip():
                continue
            try:
                message = json.loads(line)
            except json.JSONDecodeError as error:
                raise AppServerError("app-server emitted invalid JSON") from error
            if not isinstance(message, dict):
                raise AppServerError("app-server message must be a JSON object")
            return message


class AppServerClient:
    def __init__(self, transport: JsonLineTransport):
        self.transport = transport
        self._next_request_id = 1
        self.notifications: list[dict[str, Any]] = []
        self.initialized = False

    def _request(self, method: str, params: dict[str, Any]) -> dict[str, Any]:
        request_id = self._next_request_id
        self._next_request_id += 1
        self.transport.send({"method": method, "id": request_id, "params": params})

        while True:
            message = self.transport.receive()
            if message.get("id") == request_id:
                if "error" in message:
                    error = message["error"]
                    raise AppServerError(f"{method} failed: {error}")
                result = message.get("result")
                if not isinstance(result, dict):
                    raise AppServerError(f"{method} returned a non-object result")
                return result
            if "method" in message and "id" in message:
                self.transport.send(
                    {
                        "id": message["id"],
                        "error": {
                            "code": -32601,
                            "message": "server-initiated requests are denied by this client",
                        },
                    }
                )
            else:
                self.notifications.append(message)

    def initialize(self) -> dict[str, Any]:
        if self.initialized:
            raise AppServerError("client is already initialized")
        result = self._request(
            "initialize",
            {
                "clientInfo": {
                    "name": "j_store_agentic_cicd",
                    "title": "j-store Agentic CI/CD",
                    "version": "0.1.0",
                }
            },
        )
        self.transport.send({"method": "initialized", "params": {}})
        self.initialized = True
        return result

    def start_thread(self, params: dict[str, Any]) -> dict[str, Any]:
        self._require_initialized()
        return self._request("thread/start", params)

    def start_implementer_turn(
        self, thread_id: str, packet: IterationPacket
    ) -> dict[str, Any]:
        return self._start_turn(thread_id, packet, read_only=False, output_schema=None)

    def start_review_turn(
        self, thread_id: str, packet: IterationPacket
    ) -> dict[str, Any]:
        if packet.implementer_session_id is None:
            raise ValueError("review requires a trusted implementer session id")
        return self._start_turn(
            thread_id,
            packet,
            read_only=True,
            output_schema=REVIEW_PROPOSAL_SCHEMA,
        )

    def _start_turn(
        self,
        thread_id: str,
        packet: IterationPacket,
        *,
        read_only: bool,
        output_schema: dict[str, Any] | None,
    ) -> dict[str, Any]:
        self._require_initialized()
        params = build_turn_params(
            thread_id,
            packet,
            read_only=read_only,
            output_schema=output_schema,
        )
        return self._request("turn/start", params)

    def _require_initialized(self) -> None:
        if not self.initialized:
            raise AppServerError("initialize handshake is required")


def build_turn_params(
    thread_id: str,
    packet: IterationPacket,
    *,
    read_only: bool,
    output_schema: dict[str, Any] | None,
) -> dict[str, Any]:
    normalized_thread_id = thread_id.strip()
    if not normalized_thread_id:
        raise ValueError("thread_id must not be blank")
    params: dict[str, Any] = {
        "threadId": normalized_thread_id,
        "input": [
            {
                "type": "text",
                "text": json.dumps(packet.to_json(), sort_keys=True),
            }
        ],
        "approvalPolicy": "never",
        "sandboxPolicy": (
            {"type": "readOnly", "networkAccess": False}
            if read_only
            else {
                "type": "workspaceWrite",
                "writableRoots": [],
                "networkAccess": False,
            }
        ),
    }
    if output_schema is not None:
        params["outputSchema"] = output_schema
    return params


def build_review_turn_params(
    thread_id: str, packet: IterationPacket
) -> dict[str, Any]:
    if packet.implementer_session_id is None:
        raise ValueError("review requires a trusted implementer session id")
    return build_turn_params(
        thread_id,
        packet,
        read_only=True,
        output_schema=REVIEW_PROPOSAL_SCHEMA,
    )


def _thread_params(workspace: Path, *, reviewer: bool) -> dict[str, Any]:
    resolved = workspace.resolve()
    if not resolved.is_absolute():
        raise ValueError("workspace must resolve to an absolute path")
    return {
        "cwd": str(resolved),
        "ephemeral": True,
        "approvalPolicy": "never",
        "sandbox": "read-only" if reviewer else "workspace-write",
        "developerInstructions": (
            REVIEWER_INSTRUCTIONS if reviewer else IMPLEMENTER_INSTRUCTIONS
        ),
        "runtimeWorkspaceRoots": [str(resolved)],
    }


def build_implementer_thread_params(workspace: Path) -> dict[str, Any]:
    return _thread_params(workspace, reviewer=False)


def build_review_thread_params(workspace: Path) -> dict[str, Any]:
    return _thread_params(workspace, reviewer=True)
