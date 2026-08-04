#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

printf '%s\n' '[1/3] Repository governance'
./scripts/check-agent-governance.sh

printf '%s\n' '[2/3] Spec-dev contract tests'
if command -v uv >/dev/null 2>&1; then
  JSTORE_UV_CACHE_DIR="${JSTORE_UV_CACHE_DIR:-${TMPDIR:-/tmp}/j-store-uv-cache}"
  UV_CACHE_DIR="$JSTORE_UV_CACHE_DIR" uv run --with-requirements requirements-quality.txt \
    python -m unittest discover -s tests/skills/spec-dev -p 'test_*.py'
  UV_CACHE_DIR="$JSTORE_UV_CACHE_DIR" uv run --with-requirements requirements-quality.txt \
    python -m unittest discover -s tests/governance -p 'test_*.py'
else
  python3 -c 'import jsonschema' 2>/dev/null || {
    printf '%s\n' 'FAIL: install requirements-quality.txt or install uv to run specification tests.' >&2
    exit 1
  }
  python3 -m unittest discover -s tests/skills/spec-dev -p 'test_*.py'
  python3 -m unittest discover -s tests/governance -p 'test_*.py'
fi

printf '%s\n' '[3/3] Gradle regression tests'
./gradlew test

printf '%s\n' 'PASS: all local quality gates completed.'
