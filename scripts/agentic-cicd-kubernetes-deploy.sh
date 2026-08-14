#!/usr/bin/env bash
set -euo pipefail

context=""
namespace="agentic-cicd"
image="jstore-agentic-cicd:8001b52e-codex-0.146.0"
timeout_seconds=900
symphony_source="${SYMPHONY_SOURCE:-$HOME/source/symphony}"
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
manifest_dir="$repo_root/deploy/kubernetes/agentic-cicd"
overlay="$manifest_dir/overlays/development-local-image"

usage() {
  cat <<'EOF'
Usage: agentic-cicd-kubernetes-deploy.sh --context <context> [options]

Builds the pinned Level 0 Symphony image, imports it into the current node's
containerd, applies only namespace agentic-cicd, and runs the smoke check.

Options:
  --namespace <name>          Fixed target namespace (default: agentic-cicd)
  --image <name:tag>          Fixed local image tag
  --timeout-seconds <value>   Rollout timeout (default: 900)
  --symphony-source <path>    Clean pinned Symphony checkout
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
    --image)
      image=${2:?missing image}
      shift 2
      ;;
    --timeout-seconds)
      timeout_seconds=${2:?missing timeout}
      shift 2
      ;;
    --symphony-source)
      symphony_source=${2:?missing Symphony source path}
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      printf 'Unknown argument: %s\n' "$1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -z "$context" || "$(kubectl config current-context)" != "$context" ]]; then
  printf '%s\n' 'ERROR: --context must equal the current kubectl context.' >&2
  exit 2
fi
if [[ "$namespace" != "agentic-cicd" ]]; then
  printf '%s\n' 'ERROR: this deployment is fixed to namespace agentic-cicd.' >&2
  exit 2
fi
if [[ "$image" != "jstore-agentic-cicd:8001b52e-codex-0.146.0" ]]; then
  printf '%s\n' 'ERROR: --image must equal the reviewed Level 0 image tag.' >&2
  exit 2
fi
if [[ ! "$timeout_seconds" =~ ^[0-9]+$ || "$timeout_seconds" -lt 60 ]]; then
  printf '%s\n' 'ERROR: --timeout-seconds must be an integer of at least 60.' >&2
  exit 2
fi
for command in docker git kubectl sudo; do
  command -v "$command" >/dev/null || {
    printf 'ERROR: required command is missing: %s\n' "$command" >&2
    exit 2
  }
done
expected_symphony_revision=8001b52e3062495a16e520e4ceaf8f9de868c4d0
actual_symphony_revision=$(git -C "$symphony_source" rev-parse HEAD 2>/dev/null || true)
if [[ "$actual_symphony_revision" != "$expected_symphony_revision" ]]; then
  printf 'ERROR: Symphony source must be pinned to %s, got %s\n' \
    "$expected_symphony_revision" "${actual_symphony_revision:-unavailable}" >&2
  exit 2
fi
if [[ -n "$(git -C "$symphony_source" status --porcelain --untracked-files=all)" ]]; then
  printf 'ERROR: Symphony source must be clean: %s\n' "$symphony_source" >&2
  exit 2
fi
sudo ctr --namespace k8s.io images list >/dev/null
sudo install -d -o 10001 -g 10001 -m 0750 /var/lib/jstore-agentic-cicd

archive=$(mktemp "${TMPDIR:-/tmp}/jstore-agentic-cicd-image.XXXXXX.tar")
rendered=$(mktemp "${TMPDIR:-/tmp}/jstore-agentic-cicd-rendered.XXXXXX.yaml")
cleanup() {
  rm -f -- "$archive" "$rendered"
}
trap cleanup EXIT

docker build \
  --build-arg HTTP_PROXY= \
  --build-arg HTTPS_PROXY= \
  --build-arg ALL_PROXY= \
  --build-arg http_proxy= \
  --build-arg https_proxy= \
  --build-arg all_proxy= \
  --build-context "symphony-source=$symphony_source" \
  --file "$manifest_dir/image/Dockerfile" \
  --tag "$image" \
  "$repo_root"
docker run --rm --entrypoint codex "$image" --version | grep -Fx 'codex-cli 0.146.0'
revision=$(docker image inspect "$image" --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}')
[[ "$revision" == "8001b52e3062495a16e520e4ceaf8f9de868c4d0" ]] || {
  printf 'ERROR: unexpected Symphony revision label: %s\n' "$revision" >&2
  exit 1
}

docker save --output "$archive" "$image"
sudo ctr --namespace k8s.io images import "$archive"

kubectl --context "$context" kustomize "$overlay" >"$rendered"
kubectl --context "$context" apply --dry-run=client -f "$rendered" >/dev/null
kubectl --context "$context" apply -f "$manifest_dir/base/namespace.yaml" >/dev/null
kubectl --context "$context" apply --dry-run=server -f "$rendered" >/dev/null
kubectl --context "$context" apply -f "$rendered" >/dev/null
kubectl --context "$context" -n "$namespace" rollout status deployment/symphony \
  --timeout="${timeout_seconds}s"

"$repo_root/scripts/agentic-cicd-kubernetes-smoke.sh" \
  --context "$context" --namespace "$namespace" --timeout-seconds "$timeout_seconds"
