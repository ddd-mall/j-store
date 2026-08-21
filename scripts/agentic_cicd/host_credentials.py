from __future__ import annotations

import json
import os
import re
import stat
import tempfile
import time
import tomllib
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urlsplit


@dataclass(frozen=True)
class PreparedHostCredentials:
    github_token: bytes
    github_token_expires_at: bytes
    codex_auth: bytes
    codex_config: bytes


def _read_restricted(path: Path, *, label: str, minimum: int, maximum: int) -> bytes:
    if path.is_symlink() or not path.is_file():
        raise ValueError(f"{label} must be a regular file, not a symlink")
    mode = stat.S_IMODE(path.stat().st_mode)
    if mode not in {0o400, 0o600}:
        raise ValueError(f"{label} permissions must be 0400 or 0600")
    value = path.read_bytes()
    if not minimum <= len(value) <= maximum:
        raise ValueError(f"{label} size is outside the accepted range")
    return value


def _reduced_codex_config(source: bytes) -> bytes:
    try:
        value = tomllib.loads(source.decode("utf-8"))
    except (UnicodeDecodeError, tomllib.TOMLDecodeError) as error:
        raise ValueError("Codex config must be valid UTF-8 TOML") from error
    model = value.get("model")
    provider_id = value.get("model_provider")
    effort = value.get("model_reasoning_effort", "medium")
    providers = value.get("model_providers")
    if not isinstance(model, str) or re.fullmatch(
        r"[A-Za-z0-9][A-Za-z0-9._:-]{0,127}", model
    ) is None:
        raise ValueError("Codex config model is invalid")
    if not isinstance(provider_id, str) or re.fullmatch(
        r"[A-Za-z][A-Za-z0-9_-]{0,63}", provider_id
    ) is None:
        raise ValueError("Codex config provider id is invalid")
    if effort not in {"none", "minimal", "low", "medium", "high", "xhigh"}:
        raise ValueError("Codex reasoning effort is invalid")
    if not isinstance(providers, dict) or not isinstance(
        providers.get(provider_id), dict
    ):
        raise ValueError("selected Codex provider is missing")
    provider = providers[provider_id]
    if set(provider) != {"name", "base_url", "wire_api", "requires_openai_auth"}:
        raise ValueError("selected Codex provider has unsupported settings")
    name = provider["name"]
    base_url = provider["base_url"]
    if not isinstance(name, str) or not name or len(name) > 128:
        raise ValueError("Codex provider name is invalid")
    if not isinstance(base_url, str) or len(base_url) > 2048:
        raise ValueError("Codex provider URL is invalid")
    parsed = urlsplit(base_url)
    if (
        parsed.scheme != "https"
        or not parsed.hostname
        or parsed.username is not None
        or parsed.password is not None
        or parsed.query
        or parsed.fragment
    ):
        raise ValueError("Codex provider URL must be credential-free HTTPS")
    if provider["wire_api"] != "responses" or provider["requires_openai_auth"] is not True:
        raise ValueError("Codex provider must use Responses API authentication")
    lines = [
        f"model = {json.dumps(model)}",
        f"model_provider = {json.dumps(provider_id)}",
        f"model_reasoning_effort = {json.dumps(effort)}",
        "",
        f"[model_providers.{provider_id}]",
        f"name = {json.dumps(name)}",
        f"base_url = {json.dumps(base_url)}",
        'wire_api = "responses"',
        "requires_openai_auth = true",
        "",
    ]
    return "\n".join(lines).encode("utf-8")


def validate_host_credentials(
    *,
    token_file: Path,
    expires_at_epoch_seconds: int,
    auth_file: Path,
    config_file: Path,
    now_epoch_seconds: int | None = None,
) -> PreparedHostCredentials:
    now = int(time.time()) if now_epoch_seconds is None else now_epoch_seconds
    remaining = expires_at_epoch_seconds - now
    if not 300 <= remaining <= 7200:
        raise ValueError("GitHub token expiration must be 5 minutes to 2 hours ahead")
    token = _read_restricted(
        token_file, label="GitHub token", minimum=20, maximum=4096
    )
    if any(byte in b" \t\r\n\v\f" for byte in token):
        raise ValueError("GitHub token must not contain whitespace")
    auth_source = _read_restricted(
        auth_file, label="Codex auth", minimum=32, maximum=16384
    )
    try:
        auth = json.loads(auth_source.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ValueError("Codex auth must be valid UTF-8 JSON") from error
    if (
        not isinstance(auth, dict)
        or set(auth) != {"OPENAI_API_KEY"}
        or not isinstance(auth["OPENAI_API_KEY"], str)
        or len(auth["OPENAI_API_KEY"]) < 20
        or re.search(r"\s", auth["OPENAI_API_KEY"])
    ):
        raise ValueError("Codex auth must contain only one nonblank OPENAI_API_KEY")
    config_source = _read_restricted(
        config_file, label="Codex config", minimum=16, maximum=65536
    )
    return PreparedHostCredentials(
        github_token=token,
        github_token_expires_at=str(expires_at_epoch_seconds).encode("ascii"),
        codex_auth=(json.dumps(auth, sort_keys=True) + "\n").encode("utf-8"),
        codex_config=_reduced_codex_config(config_source),
    )


def _atomic_write(path: Path, value: bytes) -> None:
    descriptor, temporary_name = tempfile.mkstemp(prefix=f".{path.name}.", dir=path.parent)
    temporary = Path(temporary_name)
    try:
        os.fchmod(descriptor, 0o400)
        with os.fdopen(descriptor, "wb") as target:
            target.write(value)
            target.flush()
            os.fsync(target.fileno())
        os.replace(temporary, path)
    finally:
        if temporary.exists():
            temporary.unlink()


def prepare_host_credentials(
    *,
    token_file: Path,
    expires_at_epoch_seconds: int,
    auth_file: Path,
    config_file: Path,
    destination: Path,
    now_epoch_seconds: int | None = None,
) -> None:
    prepared = validate_host_credentials(
        token_file=token_file,
        expires_at_epoch_seconds=expires_at_epoch_seconds,
        auth_file=auth_file,
        config_file=config_file,
        now_epoch_seconds=now_epoch_seconds,
    )
    if destination.is_symlink() or (destination.exists() and not destination.is_dir()):
        raise ValueError("host credential destination must be a real directory")
    destination.mkdir(mode=0o700, parents=True, exist_ok=True)
    destination.chmod(0o700)
    values = {
        "github-token": prepared.github_token,
        "github-token-expires-at": prepared.github_token_expires_at,
        "codex-auth.json": prepared.codex_auth,
        "codex-config.toml": prepared.codex_config,
    }
    for name, value in values.items():
        _atomic_write(destination / name, value)
