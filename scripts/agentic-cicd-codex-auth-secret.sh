#!/usr/bin/env bash
set -euo pipefail

context=""
expected_context="kubernetes-admin@kubernetes"
namespace="agentic-cicd"
auth_file=""
config_file=""
read_stdin=false
mode=""

usage() {
  cat <<'EOF'
Usage:
  agentic-cicd-codex-auth-secret.sh --context <context> \
    (--auth-file <path> | --stdin) --config-file <path> \
    (--dry-run | --apply)

Creates or validates the fixed agentic-cicd/symphony-codex-auth Secret.
The auth input must contain only OPENAI_API_KEY. The config input is reduced to
one HTTPS Responses API provider plus its model and reasoning effort. Credential
values and provider URLs are never accepted as arguments or printed. --apply
performs a cluster credential write and requires separate approval.

Options:
  --context <context>    Must equal the active kubectl context
  --namespace <name>     Fixed target namespace (default: agentic-cicd)
  --auth-file <path>     Read auth.json from a regular 0400/0600 file
  --stdin                Read auth.json from a non-interactive pipe
  --config-file <path>   Read and reduce a regular 0400/0600 Codex config.toml
  --dry-run              Validate with Kubernetes server dry-run only
  --apply                Create or replace the Secret
EOF
}

while (($#)); do
  case "$1" in
    --context)
      context=${2:?missing context}
      shift 2
      ;;
    --namespace)
      namespace=${2:?missing namespace}
      shift 2
      ;;
    --auth-file)
      auth_file=${2:?missing auth file}
      shift 2
      ;;
    --config-file)
      config_file=${2:?missing config file}
      shift 2
      ;;
    --stdin)
      read_stdin=true
      shift
      ;;
    --dry-run)
      if [[ -n "$mode" ]]; then
        printf '%s\n' 'ERROR: choose exactly one of --dry-run or --apply.' >&2
        exit 2
      fi
      mode=dry-run
      shift
      ;;
    --apply)
      if [[ -n "$mode" ]]; then
        printf '%s\n' 'ERROR: choose exactly one of --dry-run or --apply.' >&2
        exit 2
      fi
      mode=apply
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      printf '%s\n' 'ERROR: unsupported argument.' >&2
      usage >&2
      exit 2
      ;;
  esac
done

for command in jq kubectl python3; do
  command -v "$command" >/dev/null || {
    printf 'ERROR: required command is missing: %s\n' "$command" >&2
    exit 2
  }
done
if [[ "$context" != "$expected_context" \
  || "$(kubectl config current-context)" != "$expected_context" ]]; then
  printf 'ERROR: target and active context must both equal %s.\n' \
    "$expected_context" >&2
  exit 2
fi
if [[ "$namespace" != "agentic-cicd" ]]; then
  printf '%s\n' 'ERROR: this tool is fixed to namespace agentic-cicd.' >&2
  exit 2
fi
if [[ "$mode" != "dry-run" && "$mode" != "apply" ]]; then
  printf '%s\n' 'ERROR: choose exactly one of --dry-run or --apply.' >&2
  exit 2
fi
if [[ (-n "$auth_file" && "$read_stdin" == true) \
  || (-z "$auth_file" && "$read_stdin" == false) ]]; then
  printf '%s\n' 'ERROR: choose exactly one of --auth-file or --stdin.' >&2
  exit 2
fi
if [[ -z "$config_file" ]]; then
  printf '%s\n' 'ERROR: --config-file is required.' >&2
  exit 2
fi
if $read_stdin && [[ -t 0 ]]; then
  printf '%s\n' 'ERROR: --stdin requires a non-interactive pipe.' >&2
  exit 2
fi

umask 077
temporary_root=$(mktemp -d "${TMPDIR:-/tmp}/jstore-codex-auth.XXXXXX")
cleanup() {
  rm -rf -- "$temporary_root"
}
trap cleanup EXIT
auth_path="$temporary_root/auth.json"
config_path="$temporary_root/config.toml"
manifest_path="$temporary_root/secret.yaml"

if [[ -n "$auth_file" ]]; then
  if [[ ! -f "$auth_file" || -L "$auth_file" ]]; then
    printf '%s\n' 'ERROR: --auth-file must be a regular file, not a symlink.' >&2
    exit 2
  fi
  permissions=$(stat -c '%a' -- "$auth_file")
  if [[ "$permissions" != "400" && "$permissions" != "600" ]]; then
    printf '%s\n' 'ERROR: --auth-file permissions must be 0400 or 0600.' >&2
    exit 2
  fi
  dd if="$auth_file" of="$auth_path" status=none
else
  dd of="$auth_path" bs=16385 count=1 status=none
fi

if [[ ! -f "$config_file" || -L "$config_file" ]]; then
  printf '%s\n' 'ERROR: --config-file must be a regular file, not a symlink.' >&2
  exit 2
fi
config_permissions=$(stat -c '%a' -- "$config_file")
if [[ "$config_permissions" != "400" && "$config_permissions" != "600" ]]; then
  printf '%s\n' 'ERROR: --config-file permissions must be 0400 or 0600.' >&2
  exit 2
fi

auth_size=$(wc -c <"$auth_path")
if ((auth_size < 32 || auth_size > 16384)); then
  printf '%s\n' 'ERROR: auth.json must contain 32-16384 bytes.' >&2
  exit 2
fi
if ! jq -e '
  type == "object"
  and keys == ["OPENAI_API_KEY"]
  and (.OPENAI_API_KEY | type == "string")
  and (.OPENAI_API_KEY | length >= 20)
  and (.OPENAI_API_KEY | test("[[:space:]]") | not)
' "$auth_path" >/dev/null 2>&1; then
  printf '%s\n' \
    'ERROR: auth.json must contain only one nonblank OPENAI_API_KEY string.' >&2
  exit 2
fi

if ! python3 - "$config_file" "$config_path" 2>/dev/null <<'PY'
import json
import re
import sys
import tomllib
from pathlib import Path
from urllib.parse import urlsplit

source = tomllib.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
model = source.get("model")
provider_id = source.get("model_provider")
effort = source.get("model_reasoning_effort", "medium")
providers = source.get("model_providers")
if not isinstance(model, str) or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._:-]{0,127}", model):
    raise SystemExit("invalid model")
if not isinstance(provider_id, str) or not re.fullmatch(r"[A-Za-z][A-Za-z0-9_-]{0,63}", provider_id):
    raise SystemExit("invalid provider id")
if effort not in {"none", "minimal", "low", "medium", "high", "xhigh"}:
    raise SystemExit("invalid reasoning effort")
if not isinstance(providers, dict) or not isinstance(providers.get(provider_id), dict):
    raise SystemExit("selected provider is missing")
provider = providers[provider_id]
if set(provider) != {"name", "base_url", "wire_api", "requires_openai_auth"}:
    raise SystemExit("selected provider has unsupported settings")
name = provider["name"]
base_url = provider["base_url"]
if not isinstance(name, str) or not name or len(name) > 128:
    raise SystemExit("invalid provider name")
if not isinstance(base_url, str) or len(base_url) > 2048:
    raise SystemExit("invalid provider URL")
parsed = urlsplit(base_url)
if (
    parsed.scheme != "https"
    or not parsed.hostname
    or parsed.username is not None
    or parsed.password is not None
    or parsed.query
    or parsed.fragment
):
    raise SystemExit("provider URL must be credential-free HTTPS")
if provider["wire_api"] != "responses" or provider["requires_openai_auth"] is not True:
    raise SystemExit("provider must use Responses API with OpenAI authentication")

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
Path(sys.argv[2]).write_text("\n".join(lines), encoding="utf-8")
PY
then
  printf '%s\n' \
    'ERROR: config.toml must select one credential-free HTTPS Responses provider.' >&2
  exit 2
fi

kubectl --context "$context" get namespace "$namespace" >/dev/null
if ! kubectl --context "$context" -n "$namespace" create secret generic \
  symphony-codex-auth \
  --from-file=auth.json="$auth_path" \
  --from-file=config.toml="$config_path" \
  --dry-run=client -o yaml \
  >"$manifest_path" 2>/dev/null; then
  printf '%s\n' 'ERROR: kubectl could not generate the Secret manifest.' >&2
  exit 1
fi

if [[ "$mode" == "dry-run" ]]; then
  if ! kubectl --context "$context" apply --dry-run=server -f "$manifest_path" \
    >/dev/null 2>&1; then
    printf '%s\n' 'ERROR: Kubernetes rejected the Secret manifest.' >&2
    exit 1
  fi
  printf 'DRY_RUN_OK context=%s namespace=%s secret=symphony-codex-auth\n' \
    "$context" "$namespace"
else
  if ! kubectl --context "$context" apply -f "$manifest_path" >/dev/null 2>&1; then
    printf '%s\n' 'ERROR: Kubernetes rejected the Secret manifest.' >&2
    exit 1
  fi
  printf 'APPLIED context=%s namespace=%s secret=symphony-codex-auth\n' \
    "$context" "$namespace"
fi
