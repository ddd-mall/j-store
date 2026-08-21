#!/usr/bin/env bash
set -euo pipefail

mode=${1:-run}
if [[ "$mode" != "run" && "$mode" != "--preflight-only" ]]; then
  printf 'Usage: run-symphony [--preflight-only]\n' >&2
  exit 2
fi

: "${CREDENTIALS_DIRECTORY:?systemd credentials are required}"
: "${HOME:?HOME is required}"
: "${CODEX_HOME:?CODEX_HOME is required}"
: "${JSTORE_SYMPHONY_WORKSPACE_ROOT:?workspace root is required}"
: "${JSTORE_AGENTIC_CICD_STATE_ROOT:?controller state root is required}"
: "${JSTORE_CANDIDATE_ARTIFACT_ROOT:?candidate artifact root is required}"
: "${JSTORE_GATE_EXCHANGE_ROOT:?gate exchange root is required}"
: "${JSTORE_SYMPHONY_REPOSITORY:?target repository is required}"
: "${JSTORE_SYMPHONY_REPOSITORY_URL:?target repository URL is required}"

runtime_root=/opt/jstore-agentic-cicd/current
github_token_file="$CREDENTIALS_DIRECTORY/github-token"
github_expiry_file="$CREDENTIALS_DIRECTORY/github-token-expires-at"
codex_auth_file="$CREDENTIALS_DIRECTORY/codex-auth.json"
codex_config_file="$CREDENTIALS_DIRECTORY/codex-config.toml"

for credential in \
  "$github_token_file" "$github_expiry_file" \
  "$codex_auth_file" "$codex_config_file"; do
  [[ -s "$credential" ]] || {
    printf 'ERROR: required runtime credential is unavailable.\n' >&2
    exit 1
  }
done
if LC_ALL=C grep -q '[[:space:]]' "$github_token_file"; then
  printf 'ERROR: GitHub credential contains whitespace.\n' >&2
  exit 1
fi
github_expiry=$(<"$github_expiry_file")
if [[ ! "$github_expiry" =~ ^[0-9]+$ ]] \
  || ((github_expiry <= $(date +%s) + 300)); then
  printf 'ERROR: GitHub credential expires in less than five minutes.\n' >&2
  exit 1
fi

install -d -m 0700 \
  "$HOME" "$CODEX_HOME" "$JSTORE_SYMPHONY_WORKSPACE_ROOT" \
  "$JSTORE_AGENTIC_CICD_STATE_ROOT" "$JSTORE_GATE_EXCHANGE_ROOT"
ln -sfn "$codex_auth_file" "$CODEX_HOME/auth.json"
ln -sfn "$codex_config_file" "$CODEX_HOME/config.toml"

export PATH="$runtime_root/bin:/usr/bin:/bin"
export PYTHONPATH="$runtime_root/controller"
export JSTORE_SYMPHONY_GITHUB_TOKEN
JSTORE_SYMPHONY_GITHUB_TOKEN=$(<"$github_token_file")
unset GITHUB_TOKEN
unset GH_TOKEN
unset GITHUB_ENTERPRISE_TOKEN
unset GH_ENTERPRISE_TOKEN
unset CODEX_API_KEY
unset OPENAI_API_KEY

expected_codex_version=$(sed -n 's/^CODEX_VERSION=//p' "$runtime_root/runtime-revisions")
expected_otp_release=$(sed -n 's/^OTP_RELEASE=//p' "$runtime_root/runtime-revisions")
[[ -n "$expected_codex_version" ]] || {
  printf 'ERROR: runtime has no pinned Codex version.\n' >&2
  exit 1
}
codex --version | grep -Fx "codex-cli $expected_codex_version" >/dev/null
actual_otp_release=$(erl -noshell -eval 'io:format("~s", [erlang:system_info(otp_release)]), halt().')
[[ -n "$expected_otp_release" && "$actual_otp_release" == "$expected_otp_release" ]] || {
  printf 'ERROR: Erlang/OTP runtime does not match the reviewed bundle.\n' >&2
  exit 1
}
codex sandbox -- /bin/true
codex login status >/dev/null

if [[ "$mode" == "--preflight-only" ]]; then
  printf 'HOST_PREFLIGHT_READY codex=%s sandbox=bubblewrap model_calls=0\n' \
    "$expected_codex_version"
  exit 0
fi

exec symphony \
  --i-understand-that-this-will-be-running-without-the-usual-guardrails \
  --logs-root "$HOME/logs" \
  --port 4000 \
  "$runtime_root/WORKFLOW.md"
