#!/usr/bin/env bash
set -euo pipefail

context=""
namespace="agentic-cicd"
timeout_seconds=300
image=""
symphony_revision=""
controller_revision=""

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
    --timeout-seconds)
      timeout_seconds=${2:?missing timeout}
      shift 2
      ;;
    --image)
      image=${2:?missing image}
      shift 2
      ;;
    --symphony-revision)
      symphony_revision=${2:?missing Symphony revision}
      shift 2
      ;;
    --controller-revision)
      controller_revision=${2:?missing controller revision}
      shift 2
      ;;
    *)
      printf 'Unknown argument: %s\n' "$1" >&2
      exit 2
      ;;
  esac
done

if [[ -z "$context" || "$(kubectl config current-context)" != "$context" ]]; then
  printf '%s\n' 'ERROR: --context must equal the current kubectl context.' >&2
  exit 2
fi
if [[ "$namespace" != "agentic-cicd" ]]; then
  printf '%s\n' 'ERROR: smoke is fixed to namespace agentic-cicd.' >&2
  exit 2
fi
if [[ ! "$timeout_seconds" =~ ^[0-9]+$ || "$timeout_seconds" -lt 30 ]]; then
  printf '%s\n' 'ERROR: --timeout-seconds must be an integer of at least 30.' >&2
  exit 2
fi
if [[ ! "$image" =~ @sha256:[0-9a-f]{64}$ || ! "$symphony_revision" =~ ^[0-9a-f]{40}$ || ! "$controller_revision" =~ ^[0-9a-f]{40}$ ]]; then
  printf '%s\n' 'ERROR: immutable image and full runtime revisions are required.' >&2
  exit 2
fi

kubectl --context "$context" -n "$namespace" rollout status deployment/symphony \
  --timeout="${timeout_seconds}s"
pod=$(kubectl --context "$context" -n "$namespace" get pod \
  -l app.kubernetes.io/name=symphony -o jsonpath='{.items[0].metadata.name}')
phase=$(kubectl --context "$context" -n "$namespace" get pod "$pod" -o jsonpath='{.status.phase}')
[[ "$phase" == "Running" ]] || {
  printf 'ERROR: Symphony pod phase is %s.\n' "$phase" >&2
  exit 1
}
claim_phase=$(kubectl --context "$context" -n "$namespace" get pvc symphony-state \
  -o jsonpath='{.status.phase}')
[[ "$claim_phase" == "Bound" ]] || {
  printf 'ERROR: Symphony PVC phase is %s.\n' "$claim_phase" >&2
  exit 1
}
codex_version=$(kubectl --context "$context" -n "$namespace" exec "$pod" -- codex --version)
[[ "$codex_version" =~ ^codex-cli\ [0-9]+\.[0-9]+\.[0-9]+$ ]] || {
  printf 'ERROR: unexpected Codex version: %s\n' "$codex_version" >&2
  exit 1
}
deployed_image=$(kubectl --context "$context" -n "$namespace" get pod "$pod" \
  -o jsonpath='{.spec.containers[0].image}')
[[ "$deployed_image" == "$image" ]] || {
  printf 'ERROR: Pod image is %s, expected %s.\n' "$deployed_image" "$image" >&2
  exit 1
}
runtime_image=$(kubectl --context "$context" -n "$namespace" get pod "$pod" \
  -o jsonpath='{.status.containerStatuses[0].imageID}')
expected_digest=${image##*@}
[[ "$runtime_image" == *"$expected_digest"* ]] || {
  printf 'ERROR: runtime image is %s, expected digest %s.\n' \
    "$runtime_image" "$expected_digest" >&2
  exit 1
}
actual_symphony_revision=$(kubectl --context "$context" -n "$namespace" exec "$pod" -- \
  sh -c 'sed -n "s/^SYMPHONY_REVISION=//p" /opt/jstore-agentic-controller/runtime-revisions')
actual_controller_revision=$(kubectl --context "$context" -n "$namespace" exec "$pod" -- \
  sh -c 'sed -n "s/^JSTORE_CONTROLLER_REVISION=//p" /opt/jstore-agentic-controller/runtime-revisions')
[[ "$actual_symphony_revision" == "$symphony_revision" ]] || {
  printf 'ERROR: Symphony revision is %s, expected %s.\n' "$actual_symphony_revision" "$symphony_revision" >&2
  exit 1
}
[[ "$actual_controller_revision" == "$controller_revision" ]] || {
  printf 'ERROR: controller revision is %s, expected %s.\n' "$actual_controller_revision" "$controller_revision" >&2
  exit 1
}
state=$(kubectl --context "$context" get --raw \
  "/api/v1/namespaces/${namespace}/services/http:symphony:4000/proxy/api/v1/state")
[[ "$state" == *'"running"'* && "$state" == *'"counts"'* && "$state" == *'"codex_totals"'* ]] || {
  printf '%s\n' 'ERROR: Symphony state API did not return the expected runtime fields.' >&2
  exit 1
}

printf 'AGENTIC_CICD_LEVEL0_READY namespace=%s pod=%s codex=%s pvc=%s\n' \
  "$namespace" "$pod" "$codex_version" "$claim_phase"
