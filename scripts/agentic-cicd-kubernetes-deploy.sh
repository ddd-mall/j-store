#!/usr/bin/env bash
set -euo pipefail

context=""
namespace="agentic-cicd"
image=""
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
  --image <name:tag>          Immutable reviewed image tag (normally derived)
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
patch_path="$manifest_dir/patches/symphony-phase-bridge.patch"
patch_sha256=bbaad0e4ad04377b5b64238f7fabbfd383915cf60692f321493dd5f3372bcb8a
routing_patch_path="$manifest_dir/patches/symphony-phase-routing.patch"
routing_patch_sha256=4914e4a5e20008c8c6b87ce835892499477cfb82fa5b944cd2efce00f024eb18
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
actual_patch_sha256=$(sha256sum "$patch_path" | awk '{print $1}')
if [[ "$actual_patch_sha256" != "$patch_sha256" ]]; then
  printf 'ERROR: symphony-phase-bridge.patch must match %s, got %s\n' \
    "$patch_sha256" "$actual_patch_sha256" >&2
  exit 2
fi
git -C "$symphony_source" apply --recount --check "$patch_path"
actual_routing_patch_sha256=$(sha256sum "$routing_patch_path" | awk '{print $1}')
if [[ "$actual_routing_patch_sha256" != "$routing_patch_sha256" ]]; then
  printf 'ERROR: symphony-phase-routing.patch must match %s, got %s\n' \
    "$routing_patch_sha256" "$actual_routing_patch_sha256" >&2
  exit 2
fi
temporary_patch_index=$(mktemp "${TMPDIR:-/tmp}/jstore-symphony-patch-index.XXXXXX")
rm -f -- "$temporary_patch_index"
GIT_INDEX_FILE="$temporary_patch_index" git -C "$symphony_source" read-tree HEAD
GIT_INDEX_FILE="$temporary_patch_index" git -C "$symphony_source" apply --cached --recount "$patch_path"
GIT_INDEX_FILE="$temporary_patch_index" git -C "$symphony_source" apply --cached --recount --check "$routing_patch_path"
rm -f -- "$temporary_patch_index"
controller_revision=$(git -C "$repo_root" rev-parse HEAD 2>/dev/null || true)
if [[ ! "$controller_revision" =~ ^[0-9a-f]{40}$ ]]; then
  printf '%s\n' 'ERROR: j-store controller source has no full Git revision.' >&2
  exit 2
fi
if [[ -n "$(git -C "$repo_root" status --porcelain --untracked-files=all)" ]]; then
  printf 'ERROR: j-store controller source must be clean: %s\n' "$repo_root" >&2
  exit 2
fi
expected_image="jstore-agentic-cicd:${expected_symphony_revision:0:8}-jstore-${controller_revision:0:8}-codex-0.146.0"
image=${image:-$expected_image}
if [[ "$image" != "$expected_image" ]]; then
  printf 'ERROR: --image must equal the derived immutable tag: %s\n' "$expected_image" >&2
  exit 2
fi
sudo ctr --namespace k8s.io images list >/dev/null
sudo install -d -o 10001 -g 10001 -m 0750 /var/lib/jstore-agentic-cicd

archive=$(mktemp "${TMPDIR:-/tmp}/jstore-agentic-cicd-image.XXXXXX.tar")
rendered=$(mktemp "${TMPDIR:-/tmp}/jstore-agentic-cicd-rendered.XXXXXX.yaml")
metadata=$(mktemp "${TMPDIR:-/tmp}/jstore-agentic-cicd-metadata.XXXXXX.json")
cleanup() {
  rm -f -- "$archive" "$rendered" "$metadata"
}
trap cleanup EXIT

docker build \
  --provenance=false \
  --metadata-file "$metadata" \
  --build-arg HTTP_PROXY= \
  --build-arg HTTPS_PROXY= \
  --build-arg ALL_PROXY= \
  --build-arg http_proxy= \
  --build-arg https_proxy= \
  --build-arg all_proxy= \
  --build-context "symphony-source=$symphony_source" \
  --build-arg "SYMPHONY_COMMIT=$expected_symphony_revision" \
  --build-arg "JSTORE_CONTROLLER_REVISION=$controller_revision" \
  --file "$manifest_dir/image/Dockerfile" \
  --tag "$image" \
  "$repo_root"
image_digest=$(python3 - "$metadata" <<'PY'
import json
import re
import sys

digest = json.load(open(sys.argv[1], encoding="utf-8")).get("containerimage.digest", "")
if not re.fullmatch(r"sha256:[0-9a-f]{64}", digest):
    raise SystemExit("controller build metadata has no immutable image digest")
print(digest)
PY
)
image_ref="$image@$image_digest"
docker run --rm --entrypoint codex "$image" --version | grep -Fx 'codex-cli 0.146.0'
revision=$(docker image inspect "$image" --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}')
[[ "$revision" == "8001b52e3062495a16e520e4ceaf8f9de868c4d0" ]] || {
  printf 'ERROR: unexpected Symphony revision label: %s\n' "$revision" >&2
  exit 1
}
controller_label=$(docker image inspect "$image" --format '{{ index .Config.Labels "io.jstore.controller.revision" }}')
[[ "$controller_label" == "$controller_revision" ]] || {
  printf 'ERROR: unexpected j-store controller revision label: %s\n' "$controller_label" >&2
  exit 1
}

docker save --output "$archive" "$image"
sudo ctr --namespace k8s.io images import "$archive"

old_pod_uid=$(kubectl --context "$context" -n "$namespace" get pod \
  -l app.kubernetes.io/name=symphony -o jsonpath='{.items[0].metadata.uid}' 2>/dev/null || true)
kubectl --context "$context" kustomize "$overlay" \
  | sed "s#image: jstore-agentic-cicd:8001b52e-codex-0.146.0#image: $image_ref#" \
  >"$rendered"
grep -F "image: $image_ref" "$rendered" >/dev/null
kubectl --context "$context" apply --dry-run=client -f "$rendered" >/dev/null
kubectl --context "$context" apply -f "$manifest_dir/base/namespace.yaml" >/dev/null
kubectl --context "$context" apply --dry-run=server -f "$rendered" >/dev/null
kubectl --context "$context" apply -f "$rendered" >/dev/null
kubectl --context "$context" -n "$namespace" rollout status deployment/symphony \
  --timeout="${timeout_seconds}s"
new_pod_uid=$(kubectl --context "$context" -n "$namespace" get pod \
  -l app.kubernetes.io/name=symphony -o jsonpath='{.items[0].metadata.uid}')
if [[ -n "$old_pod_uid" && "$new_pod_uid" == "$old_pod_uid" ]]; then
  printf 'ERROR: immutable runtime update did not create a new Pod: %s\n' "$new_pod_uid" >&2
  exit 1
fi

"$repo_root/scripts/agentic-cicd-kubernetes-smoke.sh" \
  --context "$context" --namespace "$namespace" --timeout-seconds "$timeout_seconds" \
  --image "$image_ref" --symphony-revision "$expected_symphony_revision" \
  --controller-revision "$controller_revision"
