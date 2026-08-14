#!/usr/bin/env bash
set -euo pipefail

context=""
expected_cluster_uid=""
environment=""
image_ref=""
namespace=""
timeout_seconds=1200
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
renderer="$repo_root/scripts/render-kubernetes-application.sh"

usage() {
  cat <<'EOF'
Usage: deploy-kubernetes-application.sh \
  --context <context> \
  --expected-cluster-uid <kube-system-namespace-uid> \
  --environment <development|integration|canary|production> \
  --namespace <jstore> \
  --image <repository@sha256:digest> \
  [--timeout-seconds <seconds>]

The CI/CD job must establish exactly one target-cluster tunnel before invoking
this command. This script does not create infrastructure, credentials, tunnels,
registries, database roles, or Secrets.
EOF
}

while (($#)); do
  case "$1" in
    --context)
      context=${2:?missing context}
      shift 2
      ;;
    --expected-cluster-uid)
      expected_cluster_uid=${2:?missing cluster UID}
      shift 2
      ;;
    --environment)
      environment=${2:?missing environment}
      shift 2
      ;;
    --namespace)
      namespace=${2:?missing namespace}
      shift 2
      ;;
    --image)
      image_ref=${2:?missing image}
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
      printf 'ERROR: unknown argument: %s\n' "$1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

for command in kubectl mktemp; do
  command -v "$command" >/dev/null || {
    printf 'ERROR: required command is missing: %s\n' "$command" >&2
    exit 2
  }
done

if [[ -z "$context" ]]; then
  printf '%s\n' 'ERROR: --context is required.' >&2
  exit 2
fi
if [[ ! "$expected_cluster_uid" =~ ^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$ ]]; then
  printf '%s\n' 'ERROR: --expected-cluster-uid must be a Kubernetes UUID.' >&2
  exit 2
fi
if [[ "$namespace" != "jstore" ]]; then
  printf '%s\n' 'ERROR: --namespace must be the pre-provisioned jstore namespace.' >&2
  exit 2
fi
if [[ ! "$timeout_seconds" =~ ^[0-9]+$ || "$timeout_seconds" -lt 60 ]]; then
  printf '%s\n' 'ERROR: --timeout-seconds must be an integer of at least 60.' >&2
  exit 2
fi

manifest=$(mktemp)
cleanup() {
  rm -f -- "$manifest"
}
trap cleanup EXIT

"$renderer" --environment "$environment" --image "$image_ref" >"$manifest"

if [[ "$(kubectl config current-context)" != "$context" ]]; then
  printf '%s\n' 'ERROR: --context must equal the current kubectl context.' >&2
  exit 2
fi

actual_cluster_uid=$(kubectl --context "$context" get namespace kube-system \
  -o jsonpath='{.metadata.uid}')
if [[ "$actual_cluster_uid" != "$expected_cluster_uid" ]]; then
  printf 'ERROR: target cluster UID mismatch: expected=%s actual=%s\n' \
    "$expected_cluster_uid" "$actual_cluster_uid" >&2
  exit 1
fi

kubectl --context "$context" -n "$namespace" get secret jstore-runtime >/dev/null

kubectl --context "$context" apply --server-side --dry-run=server \
  --field-manager=jstore-cicd -f "$manifest" >/dev/null
kubectl --context "$context" apply --server-side \
  --field-manager=jstore-cicd -f "$manifest" >/dev/null
kubectl --context "$context" -n "$namespace" rollout status deployment/j-store \
  --timeout="${timeout_seconds}s"

printf 'JSTORE_DEPLOYMENT_READY context=%s cluster_uid=%s environment=%s image=%s\n' \
  "$context" "$actual_cluster_uid" "$environment" "$image_ref"
