#!/usr/bin/env bash
set -euo pipefail

output_dir=""
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
dockerfile="$repo_root/deploy/kubernetes/agentic-cicd/image/GateRunner.Dockerfile"

usage() {
  cat <<'EOF'
Usage: agentic-cicd-gate-image-build.sh --output-dir <directory>

Builds the reviewed Gate Runner as a single-platform OCI archive. The source
repository must be clean so the archive identity is bound to a Git revision.
EOF
}

while (($#)); do
  case "$1" in
    --output-dir)
      output_dir=${2:?missing output directory}
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

if [[ -z "$output_dir" ]]; then
  usage >&2
  exit 2
fi
for command in docker git python3 sha256sum tar; do
  command -v "$command" >/dev/null || {
    printf 'ERROR: required command is missing: %s\n' "$command" >&2
    exit 2
  }
done
revision=$(git -C "$repo_root" rev-parse HEAD)
if [[ ! "$revision" =~ ^[0-9a-f]{40}$ ]]; then
  printf '%s\n' 'ERROR: repository HEAD is not a full Git revision.' >&2
  exit 2
fi
if [[ -n "$(git -C "$repo_root" status --porcelain --untracked-files=all)" ]]; then
  printf '%s\n' 'ERROR: Gate Runner source repository must be clean.' >&2
  exit 2
fi

output_dir=$(mkdir -p "$output_dir" && cd "$output_dir" && pwd)
tag="docker.io/library/jstore-agentic-gate:jstore-${revision:0:12}"
archive="$output_dir/jstore-agentic-gate-${revision:0:12}.oci.tar"
metadata="$output_dir/jstore-agentic-gate-${revision:0:12}.metadata.json"
environment="$output_dir/jstore-agentic-gate-${revision:0:12}.env"
for path in "$archive" "$metadata" "$environment"; do
  if [[ -e "$path" ]]; then
    printf 'ERROR: output already exists: %s\n' "$path" >&2
    exit 2
  fi
done
temporary_archive=$(mktemp "$output_dir/.gate-image.XXXXXX.oci.tar")
temporary_metadata=$(mktemp "$output_dir/.gate-image.XXXXXX.metadata.json")
cleanup() {
  rm -f -- "$temporary_archive" "$temporary_metadata"
}
trap cleanup EXIT

docker buildx build \
  --platform linux/amd64 \
  --provenance=false \
  --build-arg "JSTORE_CONTROLLER_REVISION=$revision" \
  --file "$dockerfile" \
  --tag "$tag" \
  --metadata-file "$temporary_metadata" \
  --output "type=oci,dest=$temporary_archive" \
  "$repo_root"

digest=$(python3 - "$temporary_metadata" <<'PY'
import json
import re
import sys

payload = json.load(open(sys.argv[1], encoding="utf-8"))
digest = payload.get("containerimage.digest", "")
if not re.fullmatch(r"sha256:[0-9a-f]{64}", digest):
    raise SystemExit("build metadata has no immutable image digest")
print(digest)
PY
)
repository=${tag%:*}
tar -tf "$temporary_archive" | grep -Fx 'oci-layout' >/dev/null
tar -tf "$temporary_archive" | grep -Fx 'index.json' >/dev/null
archive_sha256=$(sha256sum "$temporary_archive" | awk '{print $1}')
mv "$temporary_archive" "$archive"
mv "$temporary_metadata" "$metadata"
printf '%s\n' \
  "GATE_IMAGE_TAG=$tag" \
  "GATE_IMAGE_DIGEST=$digest" \
  "GATE_IMAGE_REF=$repository@$digest" \
  "GATE_IMAGE_ARCHIVE=$archive" \
  "GATE_IMAGE_ARCHIVE_SHA256=$archive_sha256" \
  "JSTORE_CONTROLLER_REVISION=$revision" \
  > "$environment"

printf 'PASS: Gate Runner OCI archive: %s\n' "$archive"
printf 'PASS: Gate Runner image: %s@%s\n' "$tag" "$digest"
printf 'PASS: OCI archive SHA-256: %s\n' "$archive_sha256"
