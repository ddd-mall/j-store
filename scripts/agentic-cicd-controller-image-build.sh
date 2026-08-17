#!/usr/bin/env bash
set -euo pipefail

output_dir=""
symphony_source="${SYMPHONY_SOURCE:-$HOME/source/symphony}"
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
manifest_dir="$repo_root/deploy/kubernetes/agentic-cicd"
lock_file="$repo_root/config/agentic-cicd/symphony.lock.json"
elixir_image="hexpm/elixir:1.19.5-erlang-28.3-debian-bookworm-20260202-slim@sha256:09279250196a9ad971ebe4673ec2df47bc760c0409a055df8ea283954ac6a099"
node_image="node:22-bookworm-slim@sha256:d649c27dae7ba0137b3cef5dd75baa422c08dc3d9e3fc0c23dfb172dc3cc6436"

usage() {
  cat <<'EOF'
Usage: agentic-cicd-controller-image-build.sh --output-dir <path> [options]

Builds the immutable Supervisor image and emits a Docker import archive, SPDX
SBOM statement, SLSA provenance statement, source record, and digest metadata.
The repository and the pinned Symphony checkout must both be clean.

Options:
  --output-dir <path>       Destination for immutable build artifacts
  --symphony-source <path>  Clean pinned Symphony checkout
EOF
}

while (($#)); do
  case "$1" in
    --output-dir)
      output_dir=${2:?missing output directory}
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

if [[ -z "$output_dir" ]]; then
  printf '%s\n' 'ERROR: --output-dir is required.' >&2
  exit 2
fi
for command in codex docker git python3 sha256sum tar; do
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
docker buildx version >/dev/null

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

symphony_revision=$(read_lock commit)
patch_relative=$(read_lock patch)
patch_sha256=$(read_lock patch_sha256)
routing_patch_relative=$(read_lock routing_patch)
routing_patch_sha256=$(read_lock routing_patch_sha256)
dependency_lock_relative=$(read_lock dependency_lock)
dependency_lock_sha256=$(read_lock dependency_lock_sha256)
workflow_sha256=$(sha256sum "$manifest_dir/base/WORKFLOW.md" | awk '{print $1}')
verify_sha256() {
  local path=$1
  local expected=$2
  local actual
  actual=$(sha256sum "$path" | awk '{print $1}')
  if [[ "$actual" != "$expected" ]]; then
    printf 'ERROR: %s must match %s, got %s\n' "$path" "$expected" "$actual" >&2
    exit 2
  fi
}
verify_sha256 "$repo_root/$patch_relative" "$patch_sha256"
verify_sha256 "$repo_root/$routing_patch_relative" "$routing_patch_sha256"
verify_sha256 "$repo_root/$dependency_lock_relative" "$dependency_lock_sha256"
controller_revision=$(git -C "$repo_root" rev-parse HEAD 2>/dev/null || true)
if [[ ! "$controller_revision" =~ ^[0-9a-f]{40}$ \
  || -n "$(git -C "$repo_root" status --porcelain --untracked-files=all)" ]]; then
  printf '%s\n' 'ERROR: j-store controller source must be a clean full revision.' >&2
  exit 2
fi
if [[ "$(git -C "$symphony_source" rev-parse HEAD 2>/dev/null || true)" != "$symphony_revision" \
  || -n "$(git -C "$symphony_source" status --porcelain --untracked-files=all)" ]]; then
  printf 'ERROR: Symphony source must be clean at %s.\n' "$symphony_revision" >&2
  exit 2
fi

image="docker.io/library/jstore-agentic-cicd:${symphony_revision:0:8}-jstore-${controller_revision:0:8}-codex-$codex_version"
artifact_prefix="jstore-agentic-controller-${controller_revision:0:12}"
mkdir -p "$output_dir"
temporary_root=$(mktemp -d "${TMPDIR:-/tmp}/jstore-controller-build.XXXXXX")
cleanup() {
  rm -rf -- "$temporary_root"
}
trap cleanup EXIT
runtime_metadata="$temporary_root/runtime-metadata.json"
attested_metadata="$temporary_root/attested-metadata.json"
attested_archive="$temporary_root/attested.oci.tar"
symphony_context="$temporary_root/symphony"
mkdir -p "$symphony_context"
git -C "$symphony_source" archive "$symphony_revision" \
  | tar -x -C "$symphony_context"

build_arguments=()
proxy_values="${HTTP_PROXY:-}${HTTPS_PROXY:-}${http_proxy:-}${https_proxy:-}"
if [[ -n "$proxy_values" ]]; then
  build_arguments+=(
    --build-arg HTTP_PROXY
    --build-arg HTTPS_PROXY
    --build-arg NO_PROXY
    --build-arg http_proxy
    --build-arg https_proxy
    --build-arg no_proxy
  )
  if [[ "$proxy_values" == *"127.0.0.1"* \
    || "$proxy_values" == *"localhost"* ]]; then
    build_arguments+=(--network host)
  fi
fi
build_arguments+=(
  --build-context "symphony-source=$symphony_context"
  --build-arg "ELIXIR_IMAGE=$elixir_image"
  --build-arg "NODE_IMAGE=$node_image"
  --build-arg "CODEX_VERSION=$codex_version"
  --build-arg "SYMPHONY_COMMIT=$symphony_revision"
  --build-arg "JSTORE_CONTROLLER_REVISION=$controller_revision"
  --build-arg "SYMPHONY_PATCH_SHA256=$patch_sha256"
  --build-arg "SYMPHONY_ROUTING_PATCH_SHA256=$routing_patch_sha256"
  --build-arg "SYMPHONY_DEPENDENCY_LOCK_SHA256=$dependency_lock_sha256"
  --build-arg "WORKFLOW_SHA256=$workflow_sha256"
  --file "$manifest_dir/image/Dockerfile"
  --tag "$image"
)

docker buildx build \
  --platform linux/amd64 \
  --load \
  --provenance=false \
  --metadata-file "$runtime_metadata" \
  "${build_arguments[@]}" \
  "$repo_root"

runtime_digest=$(python3 - "$runtime_metadata" <<'PY'
import json
import re
import sys

digest = json.load(open(sys.argv[1], encoding="utf-8")).get("containerimage.digest", "")
if not re.fullmatch(r"sha256:[0-9a-f]{64}", digest):
    raise SystemExit("runtime build metadata has no immutable manifest digest")
print(digest)
PY
)

docker buildx build \
  --platform linux/amd64 \
  --output "type=oci,dest=$attested_archive" \
  --sbom=true \
  --provenance=mode=max \
  --metadata-file "$attested_metadata" \
  "${build_arguments[@]}" \
  "$repo_root"

docker run --rm --entrypoint codex "$image" --version \
  | grep -Fx "codex-cli $codex_version"
labels_json=$(docker image inspect "$image" --format '{{json .Config.Labels}}')
python3 - "$labels_json" <<PY
import json
import sys

labels = json.loads(sys.argv[1])
expected = {
    "org.opencontainers.image.revision": "$symphony_revision",
    "io.jstore.controller.revision": "$controller_revision",
    "io.jstore.codex.version": "$codex_version",
    "io.jstore.symphony.patch.sha256": "$patch_sha256",
    "io.jstore.symphony.routing-patch.sha256": "$routing_patch_sha256",
    "io.jstore.symphony.dependency-lock.sha256": "$dependency_lock_sha256",
    "io.jstore.workflow.sha256": "$workflow_sha256",
    "io.jstore.base.elixir": "$elixir_image",
    "io.jstore.base.node": "$node_image",
}
if labels != {**labels, **expected}:
    missing = {key: value for key, value in expected.items() if labels.get(key) != value}
    raise SystemExit(f"controller image labels are incomplete: {missing}")
PY

sbom_path="$output_dir/$artifact_prefix.spdx.json"
provenance_path="$output_dir/$artifact_prefix.provenance.json"
python3 - "$attested_archive" "$runtime_digest" "$sbom_path" "$provenance_path" <<'PY'
import json
import pathlib
import sys
import tarfile

archive, runtime_digest, sbom_path, provenance_path = sys.argv[1:]
statements = []
with tarfile.open(archive, "r") as bundle:
    for member in bundle.getmembers():
        if not member.isfile() or not member.name.startswith("blobs/sha256/"):
            continue
        source = bundle.extractfile(member)
        if source is None:
            continue
        try:
            value = json.loads(source.read())
        except (UnicodeDecodeError, json.JSONDecodeError):
            continue
        if isinstance(value, dict) and "predicateType" in value:
            statements.append(value)

def select(fragment):
    matches = [item for item in statements if fragment in item.get("predicateType", "")]
    if len(matches) != 1:
        raise SystemExit(f"expected one {fragment} attestation, got {len(matches)}")
    statement = matches[0]
    subject_digests = {
        f"sha256:{subject.get('digest', {}).get('sha256', '')}"
        for subject in statement.get("subject", [])
    }
    if runtime_digest not in subject_digests:
        raise SystemExit(f"{fragment} attestation does not bind {runtime_digest}")
    return statement

sbom = select("spdx")
provenance = select("slsa.dev/provenance")
for path, value in ((sbom_path, sbom), (provenance_path, provenance)):
    target = pathlib.Path(path)
    target.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    target.chmod(0o444)
PY

archive_path="$output_dir/$artifact_prefix.docker.tar"
docker save --output "$archive_path" "$image"
archive_sha256=$(sha256sum "$archive_path" | awk '{print $1}')
source_record="$output_dir/$artifact_prefix.source.json"
python3 - "$source_record" <<PY
import datetime
import json
import pathlib
import subprocess

record = {
    "schema_version": 1,
    "created_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
    "image": "$image",
    "runtime_manifest_digest": "$runtime_digest",
    "archive_sha256": "$archive_sha256",
    "symphony_revision": "$symphony_revision",
    "controller_revision": "$controller_revision",
    "phase_bridge_patch_sha256": "$patch_sha256",
    "phase_routing_patch_sha256": "$routing_patch_sha256",
    "dependency_lock_sha256": "$dependency_lock_sha256",
    "workflow_sha256": "$workflow_sha256",
    "codex_version": "$codex_version",
    "elixir_image": "$elixir_image",
    "node_image": "$node_image",
    "buildx_version": subprocess.check_output(
        ["docker", "buildx", "version"], text=True
    ).strip(),
    "sbom": pathlib.Path("$sbom_path").name,
    "provenance": pathlib.Path("$provenance_path").name,
}
path = pathlib.Path(__import__("sys").argv[1])
path.write_text(json.dumps(record, indent=2, sort_keys=True) + "\n", encoding="utf-8")
path.chmod(0o444)
PY

printf '%s\n' \
  "CONTROLLER_IMAGE_TAG=$image" \
  "CONTROLLER_IMAGE_DIGEST=$runtime_digest" \
  "CONTROLLER_IMAGE_REF=${image%:*}@$runtime_digest" \
  "CONTROLLER_IMAGE_ARCHIVE=$archive_path" \
  "CONTROLLER_IMAGE_ARCHIVE_SHA256=$archive_sha256" \
  "CONTROLLER_IMAGE_SBOM=$sbom_path" \
  "CONTROLLER_IMAGE_PROVENANCE=$provenance_path" \
  "CONTROLLER_IMAGE_SOURCE_RECORD=$source_record" \
  | tee "$output_dir/$artifact_prefix.env"
printf 'PASS: immutable controller image candidate %s\n' "$runtime_digest"
