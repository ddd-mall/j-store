#!/usr/bin/env bash
set -euo pipefail

archive=""
expected_sha256=""
image_digest=""
image_ref=""

usage() {
  cat <<'EOF'
Usage: agentic-cicd-gate-image-import.sh --archive <path> --sha256 <digest> \
  --image-ref <name:tag@sha256:digest>

Verifies one reviewed OCI archive and imports it into the local Kubernetes
containerd namespace. Run the same archive on every eligible Gate node.
EOF
}

while (($#)); do
  case "$1" in
    --archive)
      archive=${2:?missing archive path}
      shift 2
      ;;
    --sha256)
      expected_sha256=${2:?missing SHA-256}
      shift 2
      ;;
    --image-ref)
      image_ref=${2:?missing image reference}
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

if [[ ! -f "$archive" || ! "$expected_sha256" =~ ^[0-9a-f]{64}$ \
  || ! "$image_ref" =~ ^[a-z0-9._/-]+(:[a-zA-Z0-9._-]+)?@sha256:[0-9a-f]{64}$ ]]; then
  usage >&2
  exit 2
fi
image_tag=${image_ref%@*}
image_digest=${image_ref##*@}
containerd_image_tag=$image_tag
if [[ "$image_tag" != */* ]]; then
  containerd_image_tag="docker.io/library/$image_tag"
fi
for command in ctr sha256sum sudo tar; do
  command -v "$command" >/dev/null || {
    printf 'ERROR: required command is missing: %s\n' "$command" >&2
    exit 2
  }
done
actual_sha256=$(sha256sum "$archive" | awk '{print $1}')
if [[ "$actual_sha256" != "$expected_sha256" ]]; then
  printf 'ERROR: OCI archive digest mismatch: expected %s, got %s\n' \
    "$expected_sha256" "$actual_sha256" >&2
  exit 1
fi
tar -tf "$archive" | grep -Fx 'oci-layout' >/dev/null
tar -tf "$archive" | grep -Fx 'index.json' >/dev/null
sudo ctr --namespace k8s.io images import "$archive"
if ! sudo ctr --namespace k8s.io content list | awk '{print $1}' | grep -Fx "$image_digest" >/dev/null; then
  printf 'ERROR: imported containerd content is missing %s\n' "$image_digest" >&2
  exit 1
fi
if ! sudo ctr --namespace k8s.io images list | awk \
  -v tag="$containerd_image_tag" -v digest="$image_digest" \
  '$1 == tag && $3 == digest {found=1} END {exit !found}'; then
  printf 'ERROR: imported image %s is not bound to %s\n' \
    "$containerd_image_tag" "$image_digest" >&2
  exit 1
fi
containerd_image_ref="$containerd_image_tag@$image_digest"
sudo ctr --namespace k8s.io images tag \
  "$containerd_image_tag" "$containerd_image_ref" >/dev/null
if ! sudo ctr --namespace k8s.io images list | awk \
  -v ref="$containerd_image_ref" -v digest="$image_digest" \
  '$1 == ref && $3 == digest {found=1} END {exit !found}'; then
  printf 'ERROR: imported image has no digest-qualified alias %s\n' \
    "$containerd_image_ref" >&2
  exit 1
fi
printf 'PASS: imported verified OCI archive %s\n' "$actual_sha256"
printf 'PASS: node %s maps %s to manifest %s\n' \
  "$(hostname)" "$containerd_image_ref" "$image_digest"
