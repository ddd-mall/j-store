#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

if command -v python3 >/dev/null 2>&1; then
  python_bin="python3"
elif command -v python >/dev/null 2>&1; then
  python_bin="python"
else
  printf '%s\n' 'FAIL: Python 3 is required to run the quality gate.' >&2
  exit 1
fi

printf '%s\n' '[1/6] Repository governance'
./scripts/check-agent-governance.sh

printf '%s\n' '[2/6] Spec-dev and governance contract tests'
if command -v uv >/dev/null 2>&1; then
  JSTORE_UV_CACHE_DIR="${JSTORE_UV_CACHE_DIR:-${TMPDIR:-/tmp}/j-store-uv-cache}"
  UV_CACHE_DIR="$JSTORE_UV_CACHE_DIR" uv run --with-requirements requirements-quality.txt \
    python -m unittest discover -s tests/skills/spec-dev -p 'test_*.py'
  UV_CACHE_DIR="$JSTORE_UV_CACHE_DIR" uv run --with-requirements requirements-quality.txt \
    python -m unittest discover -s tests/governance -p 'test_*.py'
else
  "$python_bin" -c 'import jsonschema' 2>/dev/null || {
    printf '%s\n' 'FAIL: install requirements-quality.txt or install uv to run specification tests.' >&2
    exit 1
  }
  "$python_bin" -m unittest discover -s tests/skills/spec-dev -p 'test_*.py'
  "$python_bin" -m unittest discover -s tests/governance -p 'test_*.py'
fi

printf '%s\n' '[3/6] Source ownership and formatting'
"$python_bin" scripts/check-file-ownership.py
./gradlew spotlessCheck --no-daemon --console=plain

printf '%s\n' '[4/6] Dependency license audit'
./gradlew licensee --no-daemon --console=plain

printf '%s\n' '[5/6] Gradle regression tests'
./gradlew test --no-daemon --console=plain

printf '%s\n' '[6/6] Release artifact license verification'
./gradlew verifyLicenseArtifacts --no-daemon --console=plain

printf '%s\n' 'PASS: all local quality gates completed.'
