from __future__ import annotations

import re
from dataclasses import asdict, dataclass
from typing import Any


REPOSITORY = re.compile(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
FULL_SHA = re.compile(r"[0-9a-f]{40}")
OPERATIONS = {
    "draft-pr",
    "handoff",
    "label",
    "push",
    "ready",
    "review-request",
    "workpad",
}
SOURCES = {"mutation", "observation"}


@dataclass(frozen=True)
class GitHubEventReceipt:
    operation: str
    repository: str
    resource_kind: str
    resource_id: str
    state: str
    source: str
    head_sha: str | None = None
    issue_number: int | None = None
    pull_request_number: int | None = None
    detail: str | None = None

    def __post_init__(self) -> None:
        for field_name in (
            "operation",
            "repository",
            "resource_kind",
            "resource_id",
            "state",
            "source",
        ):
            value = getattr(self, field_name)
            if not isinstance(value, str) or not value.strip() or value != value.strip():
                raise ValueError(f"GitHub receipt {field_name} must be nonblank and normalized")
        if self.operation not in OPERATIONS:
            raise ValueError("GitHub receipt operation is unsupported")
        if REPOSITORY.fullmatch(self.repository) is None:
            raise ValueError("GitHub receipt repository is invalid")
        if self.source not in SOURCES:
            raise ValueError("GitHub receipt source is unsupported")
        if self.head_sha is not None and FULL_SHA.fullmatch(self.head_sha) is None:
            raise ValueError("GitHub receipt head SHA is invalid")
        for field_name in ("issue_number", "pull_request_number"):
            value = getattr(self, field_name)
            if value is not None and (not isinstance(value, int) or isinstance(value, bool) or value <= 0):
                raise ValueError(f"GitHub receipt {field_name} must be positive")
        if self.detail is not None and (
            not isinstance(self.detail, str)
            or not self.detail.strip()
            or self.detail != self.detail.strip()
        ):
            raise ValueError("GitHub receipt detail must be nonblank and normalized")

    def to_json(self) -> dict[str, Any]:
        return {
            key: value
            for key, value in asdict(self).items()
            if value is not None
        }

    @classmethod
    def from_json(cls, payload: dict[str, Any]) -> "GitHubEventReceipt":
        if not isinstance(payload, dict):
            raise ValueError("GitHub receipt must be a JSON object")
        try:
            return cls(**payload)
        except TypeError as error:
            raise ValueError("GitHub receipt has invalid fields") from error
