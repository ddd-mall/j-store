#!/usr/bin/env bash
set -euo pipefail

context=""
namespace="agentic-cicd"
image=""
timeout_seconds=900
credentialed_observer=false
symphony_source="${SYMPHONY_SOURCE:-$HOME/source/symphony}"
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
manifest_dir="$repo_root/deploy/kubernetes/agentic-cicd"
overlay="$manifest_dir/overlays/development-local-image"
lock_file="$repo_root/config/agentic-cicd/symphony.lock.json"

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
  --credentialed-observer     Reference the fixed short-lived GitHub token Secret
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
    --credentialed-observer)
      credentialed_observer=true
      shift
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

if $credentialed_observer; then
  overlay="$manifest_dir/overlays/development-credentialed-observer"
fi

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
for command in codex docker git kubectl python3 sudo; do
  command -v "$command" >/dev/null || {
    printf 'ERROR: required command is missing: %s\n' "$command" >&2
    exit 2
  }
done
codex_output=$(codex --version 2>/dev/null || true)
if [[ "$codex_output" =~ ^codex-cli\ ([0-9]+\.[0-9]+\.[0-9]+)$ ]]; then
  codex_version=${BASH_REMATCH[1]}
else
  printf 'ERROR: Codex CLI must report a stable version, got: %s\n' \
    "${codex_output:-unavailable}" >&2
  exit 2
fi
read_lock() {
  python3 - "$lock_file" "$1" <<'PY'
import json
import sys

value = json.load(open(sys.argv[1], encoding="utf-8")).get(sys.argv[2])
if not isinstance(value, str) or not value:
    raise SystemExit(f"missing Symphony lock field: {sys.argv[2]}")
print(value)
PY
}
expected_symphony_revision=$(read_lock commit)
patch_relative=$(read_lock patch)
patch_sha256=$(read_lock patch_sha256)
routing_patch_relative=$(read_lock routing_patch)
routing_patch_sha256=$(read_lock routing_patch_sha256)
dependency_lock_relative=$(read_lock dependency_lock)
dependency_lock_sha256=$(read_lock dependency_lock_sha256)
if [[ "$patch_relative" != "deploy/kubernetes/agentic-cicd/patches/symphony-phase-bridge.patch" \
  || "$routing_patch_relative" != "deploy/kubernetes/agentic-cicd/patches/symphony-phase-routing.patch" \
  || "$dependency_lock_relative" != "deploy/kubernetes/agentic-cicd/patches/symphony-mix.lock" ]]; then
  printf '%s\n' 'ERROR: Symphony lock names unexpected deployment inputs.' >&2
  exit 2
fi
patch_path="$repo_root/$patch_relative"
routing_patch_path="$repo_root/$routing_patch_relative"
dependency_lock_path="$repo_root/$dependency_lock_relative"
if $credentialed_observer; then
  "$repo_root/scripts/check-agentic-cicd-runtime.py" \
    --symphony-source "$symphony_source" \
    --source-only
fi
workflow_sha256=$(sha256sum "$manifest_dir/base/WORKFLOW.md" | awk '{print $1}')
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
actual_dependency_lock_sha256=$(sha256sum "$dependency_lock_path" | awk '{print $1}')
if [[ "$actual_dependency_lock_sha256" != "$dependency_lock_sha256" ]]; then
  printf 'ERROR: symphony-mix.lock must match %s, got %s\n' \
    "$dependency_lock_sha256" "$actual_dependency_lock_sha256" >&2
  exit 2
fi
controller_revision=$(git -C "$repo_root" rev-parse HEAD 2>/dev/null || true)
if [[ ! "$controller_revision" =~ ^[0-9a-f]{40}$ ]]; then
  printf '%s\n' 'ERROR: j-store controller source has no full Git revision.' >&2
  exit 2
fi
if [[ -n "$(git -C "$repo_root" status --porcelain --untracked-files=all)" ]]; then
  printf 'ERROR: j-store controller source must be clean: %s\n' "$repo_root" >&2
  exit 2
fi
expected_image="docker.io/library/jstore-agentic-cicd:${expected_symphony_revision:0:8}-jstore-${controller_revision:0:8}-codex-$codex_version"
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
  --build-arg "CODEX_VERSION=$codex_version" \
  --build-arg "SYMPHONY_COMMIT=$expected_symphony_revision" \
  --build-arg "JSTORE_CONTROLLER_REVISION=$controller_revision" \
  --build-arg "SYMPHONY_PATCH_SHA256=$patch_sha256" \
  --build-arg "SYMPHONY_ROUTING_PATCH_SHA256=$routing_patch_sha256" \
  --build-arg "SYMPHONY_DEPENDENCY_LOCK_SHA256=$dependency_lock_sha256" \
  --build-arg "WORKFLOW_SHA256=$workflow_sha256" \
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
image_repository=${image%:*}
image_ref="$image_repository@$image_digest"
docker run --rm --entrypoint codex "$image" --version | grep -Fx "codex-cli $codex_version"
revision=$(docker image inspect "$image" --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}')
[[ "$revision" == "$expected_symphony_revision" ]] || {
  printf 'ERROR: unexpected Symphony revision label: %s\n' "$revision" >&2
  exit 1
}
controller_label=$(docker image inspect "$image" --format '{{ index .Config.Labels "io.jstore.controller.revision" }}')
[[ "$controller_label" == "$controller_revision" ]] || {
  printf 'ERROR: unexpected j-store controller revision label: %s\n' "$controller_label" >&2
  exit 1
}
verify_image_label() {
  local label=$1
  local expected=$2
  local actual
  actual=$(docker image inspect "$image" --format "{{ index .Config.Labels \"$label\" }}")
  if [[ "$actual" != "$expected" ]]; then
    printf 'ERROR: unexpected %s label: %s\n' "$label" "$actual" >&2
    exit 1
  fi
}
verify_image_label io.jstore.symphony.patch.sha256 "$patch_sha256"
verify_image_label io.jstore.symphony.routing-patch.sha256 "$routing_patch_sha256"
verify_image_label io.jstore.symphony.dependency-lock.sha256 "$dependency_lock_sha256"
verify_image_label io.jstore.workflow.sha256 "$workflow_sha256"

docker save --output "$archive" "$image"
sudo ctr --namespace k8s.io images import "$archive"
sudo ctr --namespace k8s.io images tag "$image" "$image_ref" >/dev/null
sudo ctr --namespace k8s.io images label \
  "$image_ref" io.cri-containerd.image=managed >/dev/null
if ! sudo ctr --namespace k8s.io images list | awk \
  -v ref="$image_ref" -v digest="$image_digest" \
  '$1 == ref && $3 == digest && index($0, "io.cri-containerd.image=managed") {found=1} END {exit !found}'; then
  printf 'ERROR: controller image has no CRI-managed digest-qualified alias %s\n' \
    "$image_ref" >&2
  exit 1
fi

old_pod_uid=$(kubectl --context "$context" -n "$namespace" get pod \
  -l app.kubernetes.io/name=symphony -o jsonpath='{.items[0].metadata.uid}' 2>/dev/null || true)
kubectl --context "$context" kustomize "$overlay" \
  | sed "s#image: jstore-agentic-cicd:development-placeholder#image: $image_ref#" \
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
