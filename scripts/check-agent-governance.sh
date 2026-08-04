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

search_quietly() {
  local pattern="$1"
  shift

  if command -v rg >/dev/null 2>&1; then
    rg -q "$pattern" "$@"
  else
    grep -ERq -- "$pattern" "$@"
  fi
}

working_tree_contains_secret() {
  local pattern="$1"

  if command -v rg >/dev/null 2>&1; then
    rg --hidden --glob '!**/.git/**' --glob '!**/build/**' --glob '!**/.gradle/**' \
      --glob '!scripts/check-agent-governance.sh' -q "$pattern" .
  else
    git grep -Eq "$pattern" -- . ':(exclude)scripts/check-agent-governance.sh'
  fi
}

required_files=(
  AGENTS.md
  docs/steering/agent-governance.md
  .env.example
  requirements-security.txt
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

if command -v rg >/dev/null 2>&1; then
  tracked_local_env="$(git ls-files | rg '^\.env($|\.)' | rg -v '^\.env\.example$' || true)"
else
  tracked_local_env="$(git ls-files | grep -E '^\.env($|\.)' | grep -Ev '^\.env\.example$' || true)"
fi
if [[ -n "$tracked_local_env" ]]; then
  fail "a local environment file is tracked"
fi

if working_tree_contains_secret \
  'Jupeter104741|jstore-dev-secret-key-must-be-at-least-32-bytes-long|192\.168\.31\.213|-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----'; then
  fail "known credential or private-key material is present in the working tree"
fi

if search_quietly '`skills/spec-dev/references/' agents .agents; then
  fail "legacy agent adapter uses a broken skills/spec-dev reference"
fi

kotlin_version="$(sed -n 's/^kotlin = "\([^"]*\)"/\1/p' gradle/libs.versions.toml)"
spring_version="$(sed -n 's/^spring-boot = "\([^"]*\)"/\1/p' gradle/libs.versions.toml)"
java_version="$(sed -n 's/.*JavaLanguageVersion\.of(\([0-9][0-9]*\)).*/\1/p' build.gradle.kts)"

search_quietly "Kotlin ${kotlin_version}，Java ${java_version}" docs/project-overview.md || \
  fail "docs/project-overview.md does not match Kotlin/Java build versions"
search_quietly "Spring Boot ${spring_version}" docs/project-overview.md || \
  fail "docs/project-overview.md does not match the Spring Boot catalog version"
search_quietly "amazoncorretto:${java_version}-" j-store-boot/Dockerfile || \
  fail "j-store-boot Docker runtime does not match the Java toolchain"

if search_quietly 'actions/dependency-review-action|gitleaks/gitleaks-action|github/codeql-action' \
  .github/workflows/security.yml; then
  fail "security workflow depends on a paid or unavailable organization scanning action"
fi
search_quietly 'semgrep scan' .github/workflows/security.yml || \
  fail "security workflow is missing Semgrep static analysis"
search_quietly 'osv-scanner.*scan source' .github/workflows/security.yml || \
  fail "security workflow is missing OSV dependency vulnerability scanning"
search_quietly 'cyclonedxDirectBom' .github/workflows/security.yml || \
  fail "security workflow is missing a resolved production dependency SBOM"
search_quietly 'gitleaks.*git' .github/workflows/security.yml || \
  fail "security workflow is missing Gitleaks CLI history scanning"

if ((failures > 0)); then
  printf '%d governance check(s) failed.\n' "$failures" >&2
  exit 1
fi

printf 'PASS: agent governance contracts are consistent.\n'
