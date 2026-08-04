#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

failures=0

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  failures=$((failures + 1))
}

require_file() {
  [[ -f "$1" ]] || fail "missing required file: $1"
}

required_files=(
  AGENTS.md
  docs/steering/agent-governance.md
  .env.example
  .github/CODEOWNERS
  .github/workflows/quality.yml
  .github/workflows/security.yml
  .github/dependabot.yml
  .codex/agents/maintenance-orchestrator.toml
  .codex/agents/product-steward.toml
  .codex/agents/quality-gate.toml
  .codex/agents/security-supply-chain.toml
  .codex/agents/sre-incident.toml
  .codex/agents/release-migration.toml
)

for path in "${required_files[@]}"; do
  require_file "$path"
done

tracked_local_env="$(git ls-files | rg '^\.env($|\.)' | rg -v '^\.env\.example$' || true)"
if [[ -n "$tracked_local_env" ]]; then
  fail "a local environment file is tracked"
fi

if rg --hidden --glob '!**/.git/**' --glob '!**/build/**' --glob '!**/.gradle/**' \
  --glob '!scripts/check-agent-governance.sh' -q \
  'Jupeter104741|jstore-dev-secret-key-must-be-at-least-32-bytes-long|192\.168\.31\.213|-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----' .; then
  fail "known credential or private-key material is present in the working tree"
fi

if rg -q '`skills/spec-dev/references/' agents .agents; then
  fail "legacy agent adapter uses a broken skills/spec-dev reference"
fi

kotlin_version="$(sed -n 's/^kotlin = "\([^"]*\)"/\1/p' gradle/libs.versions.toml)"
spring_version="$(sed -n 's/^spring-boot = "\([^"]*\)"/\1/p' gradle/libs.versions.toml)"
java_version="$(sed -n 's/.*JavaLanguageVersion\.of(\([0-9][0-9]*\)).*/\1/p' build.gradle.kts)"

rg -q "Kotlin ${kotlin_version}，Java ${java_version}" docs/project-overview.md || \
  fail "docs/project-overview.md does not match Kotlin/Java build versions"
rg -q "Spring Boot ${spring_version}" docs/project-overview.md || \
  fail "docs/project-overview.md does not match the Spring Boot catalog version"
rg -q "amazoncorretto:${java_version}-" j-store-boot/Dockerfile || \
  fail "j-store-boot Docker runtime does not match the Java toolchain"

if ((failures > 0)); then
  printf '%d governance check(s) failed.\n' "$failures" >&2
  exit 1
fi

printf 'PASS: agent governance contracts are consistent.\n'
