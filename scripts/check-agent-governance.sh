#!/usr/bin/env bash
set -euo pipefail

repo_root="${JSTORE_REPOSITORY_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
tool_root="${JSTORE_QUALITY_TOOL_ROOT:-$repo_root/scripts}"
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
    grep -ERq --exclude-dir=.git --exclude-dir=build --exclude-dir=.gradle \
      --exclude=check-agent-governance.sh -- "$pattern" .
  fi
}

repository_files() {
  if [[ -n "${JSTORE_REPOSITORY_FILES_FILE:-}" ]]; then
    [[ "$JSTORE_REPOSITORY_FILES_FILE" = /* && -f "$JSTORE_REPOSITORY_FILES_FILE" ]] || {
      fail "repository file manifest must be an existing absolute path"
      return
    }
    sed 's#^\./##' "$JSTORE_REPOSITORY_FILES_FILE"
  else
    git ls-files
  fi
}

required_files=(
  AGENTS.md
  LICENSE
  THIRD_PARTY.md
  docs/steering/agent-governance.md
  docs/operations/branch-management.md
  docs/operations/agentic-cicd-runbook.md
  docs/operations/release-evidence.md
  .env.example
  config/licenses/file-ownership.toml
  requirements-security.txt
  .github/CODEOWNERS
  .github/workflows/quality.yml
  .github/workflows/branch-policy.yml
  .github/workflows/release-evidence.yml
  .github/workflows/security.yml
  .github/rulesets/master.json
  .github/rulesets/develop.json
  .github/rulesets/README.md
  scripts/check-branch-policy.py
  scripts/check-agentic-cicd.py
  scripts/agentic_cicd/coordinator.py
  scripts/agentic_cicd/protocol.py
  scripts/agentic_cicd/app_server.py
  scripts/agentic_cicd/runtime.py
  scripts/agentic_cicd/workspace.py
  scripts/check-agentic-cicd-runtime.py
  scripts/smoke-codex-app-server.py
  WORKFLOW.md
  config/agentic-cicd/state-contract.json
  config/agentic-cicd/symphony.lock.json
  config/agentic-cicd/codex-app-server.lock.json
  config/agentic-cicd/iteration-packet.schema.json
  config/agentic-cicd/review-decision.schema.json
  config/agentic-cicd/role-routing.json
  .github/ISSUE_TEMPLATE/agent-goal.yml
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

if command -v python3 >/dev/null 2>&1; then
  python3 "$tool_root/check-agentic-cicd.py" || \
    fail "agentic CI/CD contracts are inconsistent"
elif command -v python >/dev/null 2>&1; then
  python "$tool_root/check-agentic-cicd.py" || \
    fail "agentic CI/CD contracts are inconsistent"
else
  fail "Python 3 is required to validate agentic CI/CD contracts"
fi

if command -v rg >/dev/null 2>&1; then
  tracked_local_env="$(repository_files | rg '^\.env($|\.)' | rg -v '^\.env\.example$' || true)"
else
  tracked_local_env="$(repository_files | grep -E '^\.env($|\.)' | grep -Ev '^\.env\.example$' || true)"
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
search_quietly "openjdk-${java_version}-runtime:" j-store-boot/Dockerfile || \
  fail "j-store-boot Docker runtime does not match the Java toolchain"

if search_quietly 'actions/dependency-review-action|gitleaks/gitleaks-action|github/codeql-action' \
  .github/workflows/security.yml; then
  fail "security workflow depends on a paid or unavailable organization scanning action"
fi
search_quietly 'semgrep scan' .github/workflows/security.yml || \
  fail "security workflow is missing Semgrep static analysis"
search_quietly 'osv-scanner.*scan source' .github/workflows/security.yml || \
  fail "security workflow is missing OSV dependency vulnerability scanning"
search_quietly 'osv-scanner.*scan image' .github/workflows/security.yml || \
  fail "security workflow is missing OCI image vulnerability scanning"
search_quietly 'provenance=mode=max' .github/workflows/security.yml || \
  fail "security workflow is missing OCI provenance generation"
search_quietly 'sbom=true' .github/workflows/security.yml || \
  fail "security workflow is missing OCI SBOM generation"
search_quietly 'cyclonedxDirectBom' .github/workflows/security.yml || \
  fail "security workflow is missing a resolved production dependency SBOM"
search_quietly 'dependency-license-audit' .github/workflows/security.yml || \
  fail "security workflow is missing dependency license auditing"
search_quietly 'gradlew licensee' .github/workflows/security.yml || \
  fail "security workflow does not enforce the Licensee policy"
search_quietly 'gitleaks.*git' .github/workflows/security.yml || \
  fail "security workflow is missing Gitleaks CLI history scanning"
search_quietly 'attest-build-provenance' .github/workflows/release-evidence.yml || \
  fail "release evidence workflow is missing artifact provenance attestation"
search_quietly 'merge-base --is-ancestor' .github/workflows/release-evidence.yml || \
  fail "release evidence workflow does not require tags to belong to master history"

for workflow in .github/workflows/quality.yml .github/workflows/security.yml; do
  search_quietly 'branches: \[develop, master\]' "$workflow" || \
    fail "$workflow does not run for both protected long-lived branches"
done

if search_quietly 'feature-initial|branches: \[main, master\]' .github/workflows; then
  fail "GitHub workflows still reference a retired integration branch"
fi

for ruleset in .github/rulesets/master.json .github/rulesets/develop.json; do
  search_quietly 'required_status_checks' "$ruleset" || \
    fail "$ruleset is missing required status checks"
  for context in branch-policy quality static-analysis dependency-vulnerability-scan dependency-license-audit secret-scan; do
    search_quietly "\"context\": \"$context\"" "$ruleset" || \
      fail "$ruleset is missing required check: $context"
  done
done

if [[ -e .github/dependabot.yml ]]; then
  fail "Dependabot must remain disabled; dependency upgrades require explicit compatibility review"
fi

search_quietly '^FROM .+@sha256:[0-9a-f]{64}$' j-store-boot/Dockerfile || \
  fail "j-store-boot base image is not pinned by digest"
if search_quietly '^\s*image:\s*\S+:latest\s*$' deploy j-store-boot --glob '*.yaml'; then
  fail "Kubernetes deployment contains a latest image tag"
fi

if ((failures > 0)); then
  printf '%d governance check(s) failed.\n' "$failures" >&2
  exit 1
fi

printf 'PASS: agent governance contracts are consistent.\n'
