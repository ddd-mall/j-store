#!/usr/bin/env bash
set -euo pipefail

repository=""
platform="linux/amd64"
output_dir=""
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

usage() {
  cat <<'EOF'
Usage: build-oci-candidate.sh \
  --repository <registry/path> \
  --output-dir <directory> \
  [--platform <platforms>]

Builds and pushes one OCI candidate with BuildKit SBOM and provenance
attestations. Registry authentication must already be configured by CI/CD.
The script writes candidate.env and build-metadata.json to the output directory.
EOF
}

while (($#)); do
  case "$1" in
    --repository)
      repository=${2:?missing repository}
      shift 2
      ;;
    --output-dir)
      output_dir=${2:?missing output directory}
      shift 2
      ;;
    --platform)
      platform=${2:?missing platform}
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

repository_path=${repository##*/}
if [[ ! "$repository" =~ ^[a-z0-9][a-z0-9._:/-]*$ || \
  "$repository" == *@* || "$repository_path" == *:* ]]; then
  printf '%s\n' 'ERROR: --repository must be an OCI repository without tag or digest.' >&2
  exit 2
fi
if [[ -z "$output_dir" ]]; then
  printf '%s\n' 'ERROR: --output-dir is required.' >&2
  exit 2
fi
if [[ ! "$platform" =~ ^linux/(amd64|arm64)(,linux/(amd64|arm64))*$ ]]; then
  printf '%s\n' 'ERROR: --platform must contain supported Linux OCI platforms.' >&2
  exit 2
fi

for command in docker git python3; do
  command -v "$command" >/dev/null || {
    printf 'ERROR: required command is missing: %s\n' "$command" >&2
    exit 2
  }
done

cd "$repo_root"
if [[ -n "$(git status --porcelain)" ]]; then
  printf '%s\n' 'ERROR: OCI candidates must be built from a clean Git worktree.' >&2
  exit 1
fi

commit=$(git rev-parse HEAD)
candidate_tag="${repository}:git-${commit}"
mkdir -p "$output_dir"
metadata_file="$output_dir/build-metadata.json"

./gradlew :j-store-boot:bootJar --no-daemon --console=plain
docker buildx build \
  --platform "$platform" \
  --push \
  --provenance=mode=max \
  --sbom=true \
  --metadata-file "$metadata_file" \
  --tag "$candidate_tag" \
  j-store-boot

digest=$(python3 - "$metadata_file" <<'PY'
import json
import re
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    metadata = json.load(handle)
digest = metadata.get("containerimage.digest", "")
if not re.fullmatch(r"sha256:[0-9a-f]{64}", digest):
    raise SystemExit("BuildKit metadata did not contain a sha256 image digest")
print(digest)
PY
)

{
  printf 'JSTORE_GIT_COMMIT=%s\n' "$commit"
  printf 'JSTORE_IMAGE_REPOSITORY=%s\n' "$repository"
  printf 'JSTORE_IMAGE_DIGEST=%s\n' "$digest"
  printf 'JSTORE_IMAGE_REF=%s@%s\n' "$repository" "$digest"
} >"$output_dir/candidate.env"

printf 'JSTORE_OCI_CANDIDATE_READY image=%s@%s commit=%s\n' \
  "$repository" "$digest" "$commit"
