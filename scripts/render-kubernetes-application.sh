#!/usr/bin/env bash
set -euo pipefail

environment=""
image_ref=""
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
application_root="$repo_root/deploy/kubernetes/application"

usage() {
  cat <<'EOF'
Usage: render-kubernetes-application.sh --environment <name> --image <repository@sha256:digest>

Renders one of: development, integration, canary, production.
The image must be immutable and must not contain a tag.
EOF
}

while (($#)); do
  case "$1" in
    --environment)
      environment=${2:?missing environment}
      shift 2
      ;;
    --image)
      image_ref=${2:?missing image}
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

case "$environment" in
  development|integration|canary|production) ;;
  *)
    printf 'ERROR: unsupported environment: %s\n' "$environment" >&2
    exit 2
    ;;
esac

image_repository=${image_ref%@sha256:*}
repository_path=${image_repository##*/}
if [[ ! "$image_ref" =~ ^[a-z0-9][a-z0-9._:/-]*@sha256:[0-9a-f]{64}$ || \
  "$image_repository" == *@* || "$repository_path" == *:* ]]; then
  printf '%s\n' 'ERROR: --image must be repository@sha256:digest without a tag.' >&2
  exit 2
fi

for command in cp kubectl mktemp; do
  command -v "$command" >/dev/null || {
    printf 'ERROR: required command is missing: %s\n' "$command" >&2
    exit 2
  }
done

image_digest="sha256:${image_ref##*@sha256:}"
render_root=$(mktemp -d)
cleanup() {
  rm -rf -- "$render_root"
}
trap cleanup EXIT

cp -R "$application_root/." "$render_root/"
cat >"$render_root/kustomization.yaml" <<EOF
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources:
  - overlays/${environment}
images:
  - name: j-store/application
    newName: ${image_repository}
    digest: ${image_digest}
patches:
  - target:
      kind: ConfigMap
      name: jstore-deployment
    patch: |-
      - op: replace
        path: /data/JSTORE_SERVICE_VERSION
        value: "${image_digest}"
EOF

kubectl kustomize "$render_root"
