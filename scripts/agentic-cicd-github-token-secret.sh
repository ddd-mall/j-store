#!/usr/bin/env bash
set -euo pipefail

context=""
expected_context="kubernetes-admin@kubernetes"
namespace="agentic-cicd"
token_file=""
expires_at_epoch_seconds=""
read_stdin=false
mode=""

usage() {
  cat <<'EOF'
Usage:
  agentic-cicd-github-token-secret.sh --context <context> \
    (--token-file <path> | --stdin) \
    --expires-at-epoch-seconds <epoch> (--dry-run | --apply)

Creates or validates the fixed agentic-cicd/symphony-github-token Secret.
Token values are never accepted as arguments or environment variables and are
never printed. --apply performs a cluster write and requires separate approval.

Options:
  --context <context>    Must equal the active kubectl context
  --namespace <name>     Fixed target namespace (default: agentic-cicd)
  --token-file <path>    Read a token from a regular 0400/0600 file
  --stdin                Read a token from a non-interactive pipe
  --expires-at-epoch-seconds <epoch>
                         Trusted installation token expiration (5m-2h ahead)
  --dry-run              Validate with Kubernetes server dry-run only
  --apply                Create or replace the Secret
EOF
}

file_permissions() {
  if stat -c '%a' "$1" >/dev/null 2>&1; then
    stat -c '%a' "$1"
  else
    stat -f '%Lp' "$1"
  fi
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
    --token-file)
      token_file=${2:?missing token file}
      shift 2
      ;;
    --stdin)
      read_stdin=true
      shift
      ;;
    --expires-at-epoch-seconds)
      expires_at_epoch_seconds=${2:?missing token expiration}
      shift 2
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

command -v kubectl >/dev/null || {
  printf '%s\n' 'ERROR: kubectl is required.' >&2
  exit 2
}
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
if [[ ! "$expires_at_epoch_seconds" =~ ^[0-9]{10,12}$ ]]; then
  printf '%s\n' 'ERROR: --expires-at-epoch-seconds is required.' >&2
  exit 2
fi
current_epoch_seconds=$(date +%s)
remaining_lifetime=$((expires_at_epoch_seconds - current_epoch_seconds))
if ((remaining_lifetime < 300 || remaining_lifetime > 7200)); then
  printf '%s\n' \
    'ERROR: token expiration must be between 5 minutes and 2 hours ahead.' >&2
  exit 2
fi
if [[ (-n "$token_file" && "$read_stdin" == true) \
  || (-z "$token_file" && "$read_stdin" == false) ]]; then
  printf '%s\n' 'ERROR: choose exactly one of --token-file or --stdin.' >&2
  exit 2
fi
if $read_stdin && [[ -t 0 ]]; then
  printf '%s\n' 'ERROR: --stdin requires a non-interactive pipe.' >&2
  exit 2
fi

umask 077
temporary_root=$(mktemp -d "${TMPDIR:-/tmp}/jstore-github-token.XXXXXX")
cleanup() {
  rm -rf -- "$temporary_root"
}
trap cleanup EXIT
token_path="$temporary_root/token"
manifest_path="$temporary_root/secret.yaml"

if [[ -n "$token_file" ]]; then
  if [[ ! -f "$token_file" || -L "$token_file" ]]; then
    printf '%s\n' 'ERROR: --token-file must be a regular file, not a symlink.' >&2
    exit 2
  fi
  permissions=$(file_permissions "$token_file")
  if [[ "$permissions" != "400" && "$permissions" != "600" ]]; then
    printf '%s\n' 'ERROR: --token-file permissions must be 0400 or 0600.' >&2
    exit 2
  fi
  dd if="$token_file" of="$token_path" status=none
else
  dd of="$token_path" bs=4097 count=1 status=none
fi

token_size=$(wc -c <"$token_path")
if ((token_size < 20 || token_size > 4096)); then
  printf '%s\n' 'ERROR: token must contain 20-4096 bytes.' >&2
  exit 2
fi
token_without_whitespace_size=$(LC_ALL=C tr -d '[:space:]' <"$token_path" | wc -c)
if ((token_without_whitespace_size != token_size)); then
  printf '%s\n' 'ERROR: token must not contain whitespace.' >&2
  exit 2
fi

kubectl --context "$context" get namespace "$namespace" >/dev/null
if ! kubectl --context "$context" -n "$namespace" create secret generic \
  symphony-github-token --from-file=token="$token_path" \
  --from-literal=expires-at-epoch-seconds="$expires_at_epoch_seconds" \
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
  printf 'DRY_RUN_OK context=%s namespace=%s secret=symphony-github-token\n' \
    "$context" "$namespace"
else
  if ! kubectl --context "$context" apply -f "$manifest_path" >/dev/null 2>&1; then
    printf '%s\n' 'ERROR: Kubernetes rejected the Secret manifest.' >&2
    exit 1
  fi
  printf 'APPLIED context=%s namespace=%s secret=symphony-github-token\n' \
    "$context" "$namespace"
fi
