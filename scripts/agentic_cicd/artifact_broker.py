from __future__ import annotations

import hashlib
import json
import os
import secrets
import time
from dataclasses import dataclass
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import BinaryIO


SHA256_LENGTH = 64


def _is_sha256(value: str) -> bool:
    return len(value) == SHA256_LENGTH and all(
        character in "0123456789abcdef" for character in value
    )


@dataclass(frozen=True)
class ArtifactLease:
    token: str
    artifact_sha256: str
    expires_at: int


class ArtifactLeaseStore:
    """Issues and atomically consumes short-lived, single-use artifact leases."""

    def __init__(self, lease_root: Path, *, maximum_ttl_seconds: int = 300):
        self.lease_root = lease_root.resolve()
        self.maximum_ttl_seconds = maximum_ttl_seconds
        self.lease_root.mkdir(parents=True, exist_ok=True, mode=0o700)

    def issue(
        self,
        artifact_sha256: str,
        *,
        ttl_seconds: int,
        now: int | None = None,
        token: str | None = None,
    ) -> ArtifactLease:
        if not _is_sha256(artifact_sha256):
            raise ValueError("artifact_sha256 must be a lowercase SHA-256")
        if (
            isinstance(ttl_seconds, bool)
            or not isinstance(ttl_seconds, int)
            or ttl_seconds <= 0
            or ttl_seconds > self.maximum_ttl_seconds
        ):
            raise ValueError("lease TTL is outside the trusted limit")
        issued_token = token or secrets.token_urlsafe(32)
        if len(issued_token) < 32 or any(character.isspace() for character in issued_token):
            raise ValueError("lease token is not sufficiently strong")
        timestamp = int(time.time()) if now is None else now
        lease = ArtifactLease(issued_token, artifact_sha256, timestamp + ttl_seconds)
        path = self._path_for(issued_token)
        descriptor = os.open(
            path,
            os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0),
            0o600,
        )
        with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as stream:
            os.fchmod(stream.fileno(), 0o640)
            json.dump(
                {
                    "artifact_sha256": lease.artifact_sha256,
                    "expires_at": lease.expires_at,
                },
                stream,
                separators=(",", ":"),
                sort_keys=True,
            )
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        return lease

    def consume(
        self, token: str, artifact_sha256: str, *, now: int | None = None
    ) -> ArtifactLease | None:
        if not _is_sha256(artifact_sha256):
            return None
        source = self._path_for(token)
        consumed = self.lease_root / f".consumed-{os.getpid()}-{secrets.token_hex(8)}"
        try:
            os.replace(source, consumed)
        except FileNotFoundError:
            return None
        try:
            if consumed.is_symlink() or not consumed.is_file():
                return None
            payload = json.loads(consumed.read_text(encoding="utf-8"))
            if set(payload) != {"artifact_sha256", "expires_at"}:
                return None
            if payload["artifact_sha256"] != artifact_sha256:
                return None
            expires_at = payload["expires_at"]
            if isinstance(expires_at, bool) or not isinstance(expires_at, int):
                return None
            timestamp = int(time.time()) if now is None else now
            if expires_at < timestamp:
                return None
            return ArtifactLease(token, artifact_sha256, expires_at)
        finally:
            consumed.unlink(missing_ok=True)

    def _path_for(self, token: str) -> Path:
        if not isinstance(token, str) or not token:
            raise ValueError("lease token must not be blank")
        digest = hashlib.sha256(token.encode()).hexdigest()
        return self.lease_root / f"{digest}.json"


class ArtifactStore:
    """Opens immutable candidate archives by digest without following links."""

    def __init__(self, artifact_root: Path):
        self.archive_root = artifact_root.resolve() / "archives"

    def open_verified(self, artifact_sha256: str) -> tuple[BinaryIO, int] | None:
        if not _is_sha256(artifact_sha256):
            return None
        path = self.archive_root / f"{artifact_sha256}.tar"
        try:
            descriptor = os.open(
                path,
                os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0),
            )
        except (FileNotFoundError, OSError):
            return None
        stream = os.fdopen(descriptor, "rb")
        digest = hashlib.sha256()
        size = 0
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
            size += len(chunk)
        if digest.hexdigest() != artifact_sha256:
            stream.close()
            return None
        stream.seek(0)
        return stream, size


class ArtifactBrokerHandler(BaseHTTPRequestHandler):
    server_version = "jstore-artifact-broker/1"

    def do_GET(self) -> None:  # noqa: N802
        prefix = "/artifacts/"
        if not self.path.startswith(prefix) or "?" in self.path:
            self.send_error(404)
            return
        artifact_sha256 = self.path[len(prefix) :]
        authorization = self.headers.get("Authorization", "")
        if not authorization.startswith("Bearer "):
            self.send_error(401)
            return
        token = authorization.removeprefix("Bearer ")
        lease_store: ArtifactLeaseStore = self.server.lease_store  # type: ignore[attr-defined]
        if lease_store.consume(token, artifact_sha256) is None:
            self.send_error(403)
            return
        artifact_store: ArtifactStore = self.server.artifact_store  # type: ignore[attr-defined]
        opened = artifact_store.open_verified(artifact_sha256)
        if opened is None:
            self.send_error(404)
            return
        stream, size = opened
        try:
            self.send_response(200)
            self.send_header("Content-Type", "application/x-tar")
            self.send_header("Content-Length", str(size))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                self.wfile.write(chunk)
        finally:
            stream.close()

    def log_message(self, format: str, *args: object) -> None:
        # Never log Authorization headers or capability tokens.
        return


def serve(artifact_root: Path, lease_root: Path, host: str, port: int) -> None:
    server = ThreadingHTTPServer((host, port), ArtifactBrokerHandler)
    server.artifact_store = ArtifactStore(artifact_root)  # type: ignore[attr-defined]
    server.lease_store = ArtifactLeaseStore(lease_root)  # type: ignore[attr-defined]
    server.serve_forever()
