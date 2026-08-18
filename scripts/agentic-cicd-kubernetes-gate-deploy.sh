#!/usr/bin/env bash
set -euo pipefail

context=""
controller_image=""
gate_image=""
timeout_seconds=900
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
manifest_dir="$repo_root/deploy/kubernetes/agentic-cicd"

usage() {
  cat <<'EOF'
Usage: agentic-cicd-kubernetes-gate-deploy.sh --context <context> \
  --controller-image <name:revision@sha256:digest> \
  --gate-image <name:revision@sha256:digest>

Deploys the credential-free Artifact Broker and Gate Dispatcher, with the
Gate Runner fixed to one imported digest. This does not enable the Level 1
capability flags or any GitHub write capability.
EOF
}

while (($#)); do
  case "$1" in
    --context)
      context=${2:?missing context}
      shift 2
      ;;
    --controller-image)
      controller_image=${2:?missing controller image}
      shift 2
      ;;
    --gate-image)
      gate_image=${2:?missing gate image}
      shift 2
      ;;
    --timeout-seconds)
      timeout_seconds=${2:?missing timeout}
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
if [[ ! "$controller_image" =~ ^[a-z0-9._/-]+(:[a-zA-Z0-9._-]+)?@sha256:[0-9a-f]{64}$ ]]; then
  printf '%s\n' 'ERROR: --controller-image must use an immutable SHA-256 digest.' >&2
  exit 2
fi
if [[ ! "$gate_image" =~ ^[a-z0-9._/-]+(:[a-zA-Z0-9._-]+)?@sha256:[0-9a-f]{64}$ ]]; then
  printf '%s\n' 'ERROR: --gate-image must use an immutable SHA-256 digest.' >&2
  exit 2
fi
if [[ ! "$timeout_seconds" =~ ^[0-9]+$ || "$timeout_seconds" -lt 60 || "$timeout_seconds" -gt 3600 ]]; then
  printf '%s\n' 'ERROR: timeout must be between 60 and 3600 seconds.' >&2
  exit 2
fi
for command in kubectl python3; do
  command -v "$command" >/dev/null || {
    printf 'ERROR: required command is missing: %s\n' "$command" >&2
    exit 2
  }
done

declare -A expected_owners=(
  [/var/lib/jstore-agentic-candidates]=10001:11001
  [/var/lib/jstore-agentic-gate-requests]=10001:11001
  [/var/lib/jstore-agentic-gate-receipts]=10002:11001
  [/var/lib/jstore-agentic-artifact-leases]=10002:11001
)
for path in "${!expected_owners[@]}"; do
  if [[ ! -d "$path" ]]; then
    printf 'ERROR: required local PV directory is missing: %s\n' "$path" >&2
    exit 2
  fi
  actual_owner=$(stat -c '%u:%g' "$path")
  actual_mode=$(stat -c '%a' "$path")
  if [[ "$actual_owner" != "${expected_owners[$path]}" || "$actual_mode" != "2770" ]]; then
    printf 'ERROR: unsafe PV directory ownership/mode for %s: %s %s\n' \
      "$path" "$actual_owner" "$actual_mode" >&2
    exit 2
  fi
done

temporary_dir=$(mktemp -d "${TMPDIR:-/tmp}/jstore-gate-deploy.XXXXXX")
cleanup() {
  rm -rf -- "$temporary_dir"
}
trap cleanup EXIT
policy="$temporary_dir/gate-policy.json"
rendered="$temporary_dir/gates.yaml"
python3 - "$gate_image" "$timeout_seconds" > "$policy" <<'PY'
import json
import sys

image, timeout = sys.argv[1], int(sys.argv[2])
json.dump(
    {
        "runner_image": image,
        "fetch_image": image,
        "validation_commands": ["/opt/jstore-gate/run-quality-gate"],
        "timeout_seconds": timeout,
    },
    sys.stdout,
    separators=(",", ":"),
    sort_keys=True,
)
sys.stdout.write("\n")
PY

kubectl --context "$context" apply -f "$manifest_dir/base/namespace.yaml" >/dev/null
kubectl --context "$context" apply -f "$manifest_dir/gates/namespace.yaml" >/dev/null
kubectl --context "$context" -n agentic-cicd create configmap gate-policy \
  --from-file=gate-policy.json="$policy" --dry-run=client -o yaml \
  | kubectl --context "$context" apply -f - >/dev/null
kubectl --context "$context" kustomize "$manifest_dir/gates" \
  | sed "s#image: jstore-agentic-cicd:development-placeholder#image: $controller_image#g" \
  > "$rendered"
grep -F "image: $controller_image" "$rendered" >/dev/null
kubectl --context "$context" apply --dry-run=client -f "$manifest_dir/base/gate-storage.yaml" >/dev/null
kubectl --context "$context" apply --dry-run=client -f "$rendered" >/dev/null
kubectl --context "$context" apply --dry-run=server -f "$manifest_dir/base/gate-storage.yaml" >/dev/null
kubectl --context "$context" apply -f "$manifest_dir/base/gate-storage.yaml" >/dev/null
old_broker_uid=$(kubectl --context "$context" -n agentic-cicd get pod \
  -l app.kubernetes.io/name=artifact-broker -o jsonpath='{.items[0].metadata.uid}' \
  2>/dev/null || true)
old_dispatcher_uid=$(kubectl --context "$context" -n agentic-cicd get pod \
  -l app.kubernetes.io/name=gate-dispatcher -o jsonpath='{.items[0].metadata.uid}' \
  2>/dev/null || true)
kubectl --context "$context" apply --dry-run=server -f "$rendered" >/dev/null
kubectl --context "$context" apply -f "$rendered" >/dev/null
kubectl --context "$context" -n agentic-cicd scale \
  deployment/artifact-broker deployment/gate-dispatcher --replicas=1 >/dev/null
kubectl --context "$context" -n agentic-cicd rollout status \
  deployment/artifact-broker --timeout="${timeout_seconds}s"
kubectl --context "$context" -n agentic-cicd rollout status \
  deployment/gate-dispatcher --timeout="${timeout_seconds}s"

verify_runtime_image() {
  local application=$1
  local previous_uid=$2
  local pod_uid image_id expected_digest
  pod_uid=$(kubectl --context "$context" -n agentic-cicd get pod \
    -l "app.kubernetes.io/name=$application" -o jsonpath='{.items[0].metadata.uid}')
  image_id=$(kubectl --context "$context" -n agentic-cicd get pod \
    -l "app.kubernetes.io/name=$application" \
    -o jsonpath='{.items[0].status.containerStatuses[0].imageID}')
  expected_digest=${controller_image##*@}
  if [[ -n "$previous_uid" && "$pod_uid" == "$previous_uid" ]]; then
    printf 'ERROR: %s rollout retained the previous Pod UID: %s\n' \
      "$application" "$pod_uid" >&2
    exit 1
  fi
  if [[ "$image_id" != *"$expected_digest"* ]]; then
    printf 'ERROR: %s runtime image %s does not match %s\n' \
      "$application" "$image_id" "$expected_digest" >&2
    exit 1
  fi
  printf 'PASS: %s Pod %s runs %s\n' "$application" "$pod_uid" "$image_id"
}
verify_runtime_image artifact-broker "$old_broker_uid"
verify_runtime_image gate-dispatcher "$old_dispatcher_uid"

if supervisor_token=$(kubectl --context "$context" auth can-i create jobs \
  --as=system:serviceaccount:agentic-cicd:symphony -n agentic-cicd-gates); then
  supervisor_status=0
else
  supervisor_status=$?
fi
if dispatcher_secret=$(kubectl --context "$context" auth can-i get secrets \
  --as=system:serviceaccount:agentic-cicd:gate-dispatcher -n agentic-cicd-gates); then
  dispatcher_status=0
else
  dispatcher_status=$?
fi
if [[ "$supervisor_token" != "no" || "$supervisor_status" -ne 1 \
  || "$dispatcher_secret" != "no" || "$dispatcher_status" -ne 1 ]]; then
  printf '%s\n' 'ERROR: deployed RBAC exceeds the Level 1 boundary.' >&2
  exit 1
fi
printf 'PASS: Gate control plane deployed with runner %s\n' "$gate_image"
printf '%s\n' 'PASS: Level 1 capability and all GitHub writes remain disabled.'
