from __future__ import annotations

from collections.abc import Mapping
import os


TRUSTED_PROCESS_ENVIRONMENT_VARIABLES = ("PATH", "LANG", "LC_ALL", "TZ")


def trusted_process_environment(
    overrides: Mapping[str, str] | None = None,
) -> dict[str, str]:
    environment = {
        name: value
        for name in TRUSTED_PROCESS_ENVIRONMENT_VARIABLES
        if (value := os.environ.get(name))
    }
    if overrides is not None:
        environment.update(overrides)
    return environment
