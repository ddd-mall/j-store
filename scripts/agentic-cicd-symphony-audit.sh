#!/usr/bin/env bash
set -euo pipefail

symphony_source="${SYMPHONY_SOURCE:-$HOME/source/symphony}"
output_dir=""
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
lock_file="$repo_root/config/agentic-cicd/symphony.lock.json"
builder_image="hexpm/elixir:1.19.5-erlang-28.3-debian-bookworm-20260202-slim@sha256:09279250196a9ad971ebe4673ec2df47bc760c0409a055df8ea283954ac6a099"
node_image="node:22-bookworm-slim@sha256:d649c27dae7ba0137b3cef5dd75baa422c08dc3d9e3fc0c23dfb172dc3cc6436"

usage() {
  cat <<'EOF'
Usage: agentic-cicd-symphony-audit.sh --output-dir <path> [options]

Applies the two reviewed Symphony patches in order to a clean archive of the
pinned source, installs the reviewed dependency lock, and runs the native Linux
compile, test, Hex advisory, escript, license-inventory, and Codex version gates.

Options:
  --output-dir <path>       Directory for the JSON report and license inventory
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

docker_network_arguments=()
docker_build_arguments=()
proxy_values="${HTTP_PROXY:-}${HTTPS_PROXY:-}"
if [[ -n "$proxy_values" ]]; then
  docker_build_arguments+=(
    --build-arg HTTP_PROXY
    --build-arg HTTPS_PROXY
    --build-arg NO_PROXY
  )
  if [[ "$proxy_values" == *"127.0.0.1"* \
    || "$proxy_values" == *"localhost"* ]]; then
    docker_build_arguments+=(--network host)
  else
    docker_network_arguments+=(
      --env HTTP_PROXY
      --env HTTPS_PROXY
      --env NO_PROXY
    )
  fi
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

symphony_revision=$(read_lock commit)
patch_relative=$(read_lock patch)
patch_sha256=$(read_lock patch_sha256)
routing_patch_relative=$(read_lock routing_patch)
routing_patch_sha256=$(read_lock routing_patch_sha256)
dependency_lock_relative=$(read_lock dependency_lock)
dependency_lock_sha256=$(read_lock dependency_lock_sha256)
test_fixture_relative=$(read_lock test_fixture)
test_fixture_sha256=$(read_lock test_fixture_sha256)
patch_path="$repo_root/$patch_relative"
routing_patch_path="$repo_root/$routing_patch_relative"
dependency_lock_path="$repo_root/$dependency_lock_relative"
test_fixture_path="$repo_root/$test_fixture_relative"

actual_revision=$(git -C "$symphony_source" rev-parse HEAD 2>/dev/null || true)
if [[ "$actual_revision" != "$symphony_revision" ]]; then
  printf 'ERROR: Symphony source must be pinned to %s, got %s\n' \
    "$symphony_revision" "${actual_revision:-unavailable}" >&2
  exit 2
fi
if [[ -n "$(git -C "$symphony_source" status --porcelain --untracked-files=all)" ]]; then
  printf 'ERROR: Symphony source must be clean: %s\n' "$symphony_source" >&2
  exit 2
fi

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
verify_sha256 "$patch_path" "$patch_sha256"
verify_sha256 "$routing_patch_path" "$routing_patch_sha256"
verify_sha256 "$dependency_lock_path" "$dependency_lock_sha256"
verify_sha256 "$test_fixture_path" "$test_fixture_sha256"

audit_root=$(mktemp -d "${TMPDIR:-/tmp}/jstore-symphony-audit.XXXXXX")
audit_toolchain_image_id=""
cleanup() {
  docker run --rm \
    --volume "$audit_root:/cleanup" \
    --entrypoint sh \
    "$builder_image" \
    -c "chown -R $(id -u):$(id -g) /cleanup" >/dev/null 2>&1 || true
  if [[ -n "$audit_toolchain_image_id" ]]; then
    docker image rm "$audit_toolchain_image_id" >/dev/null 2>&1 || true
  fi
  chmod -R u+rwX "$audit_root" 2>/dev/null || true
  rm -rf -- "$audit_root"
}
trap cleanup EXIT

audit_toolchain_context="$audit_root/audit-toolchain"
audit_toolchain_dockerfile="$audit_toolchain_context/Dockerfile"
audit_toolchain_iidfile="$audit_toolchain_context/image-id"
mkdir -m 0700 "$audit_toolchain_context"
cat >"$audit_toolchain_dockerfile" <<EOF
FROM $builder_image
RUN apt-get -o Acquire::Retries=2 -o Acquire::http::Timeout=30 -o Acquire::https::Timeout=30 update >/dev/null \
    && apt-get -o Acquire::Retries=2 -o Acquire::http::Timeout=30 -o Acquire::https::Timeout=30 \
      install --yes --no-install-recommends build-essential cmake git ca-certificates python3 >/dev/null \
    && rm -rf /var/lib/apt/lists/*
EOF
chmod 0444 "$audit_toolchain_dockerfile"
audit_toolchain_dockerfile_sha256=$(sha256sum "$audit_toolchain_dockerfile" | awk '{print $1}')
docker build \
  "${docker_build_arguments[@]}" \
  --file "$audit_toolchain_dockerfile" \
  --iidfile "$audit_toolchain_iidfile" \
  "$audit_toolchain_context"
audit_toolchain_image_id=$(<"$audit_toolchain_iidfile")
if [[ ! "$audit_toolchain_image_id" =~ ^sha256:[0-9a-f]{64}$ ]]; then
  printf 'ERROR: Docker returned invalid audit toolchain image ID: %s\n' \
    "${audit_toolchain_image_id:-unavailable}" >&2
  exit 2
fi

source_tree="$audit_root/symphony"
mkdir -p "$source_tree"
git -C "$symphony_source" archive "$symphony_revision" | tar -x -C "$source_tree"

(
  cd "$source_tree"
  git apply --recount --check "$patch_path"
  git apply --recount "$patch_path"
  git apply --recount --check "$routing_patch_path"
  git apply --recount "$routing_patch_path"
)
install -m 0444 "$dependency_lock_path" "$source_tree/elixir/mix.lock"

if [[ "$(basename "$test_fixture_path")" != controller.py ]]; then
  printf 'ERROR: Symphony test fixture must be named controller.py.\n' >&2
  exit 2
fi
fixture_dir="$audit_root/controller-fixture"
mkdir -m 0700 "$fixture_dir"
install -m 0444 "$test_fixture_path" "$fixture_dir/controller.py"
chmod 0555 "$fixture_dir"
verify_sha256 "$fixture_dir/controller.py" "$test_fixture_sha256"
audit_evidence_dir="$audit_root/evidence"
mkdir -m 0700 "$audit_evidence_dir"
docker run --rm \
  "${docker_network_arguments[@]}" \
  --volume "$source_tree:/work" \
  --volume "$fixture_dir:/opt/jstore-agentic-controller:ro" \
  --volume "$audit_evidence_dir:/evidence" \
  --workdir /work/elixir \
  --env MIX_HOME=/work/.mix \
  --env HEX_HOME=/work/.hex \
  --env "DEPENDENCY_LOCK_SHA256=$dependency_lock_sha256" \
  "$audit_toolchain_image_id" \
  bash -c '
    set -euo pipefail
    git config --global http.version HTTP/1.1
    mix local.hex --force
    mix local.rebar --force
    mix deps.get --locked
    printf "%s  %s\n" "$DEPENDENCY_LOCK_SHA256" /work/elixir/mix.lock | sha256sum -c -
    mix hex.audit
    elixir -e '\''
      Path.wildcard("deps/*/hex_metadata.config")
      |> Enum.map(fn path ->
        {:ok, terms} = :file.consult(String.to_charlist(path))
        metadata = Map.new(terms)
        licenses = metadata |> Map.fetch!("licenses") |> Enum.join(" OR ")
        {Map.fetch!(metadata, "name"), Map.fetch!(metadata, "version"), licenses}
      end)
      |> Enum.sort()
      |> Enum.each(fn {name, version, licenses} ->
        IO.puts([name, "\t", version, "\t", licenses])
      end)
    '\'' > /evidence/symphony-dependencies.tsv
    chmod 0444 /evidence/symphony-dependencies.tsv
  '
verify_sha256 "$source_tree/elixir/mix.lock" "$dependency_lock_sha256"

docker run --rm \
  "${docker_network_arguments[@]}" \
  --volume "$source_tree:/work" \
  --volume "$fixture_dir:/opt/jstore-agentic-controller:ro" \
  --workdir /work/elixir \
  --env MIX_HOME=/work/.mix \
  --env HEX_HOME=/work/.hex \
  --env "DEPENDENCY_LOCK_SHA256=$dependency_lock_sha256" \
  "$audit_toolchain_image_id" \
  bash -c '
    set -euo pipefail
    git config --global http.version HTTP/1.1
    printf "%s  %s\n" "$DEPENDENCY_LOCK_SHA256" /work/elixir/mix.lock | sha256sum -c -
    mix compile --warnings-as-errors
    mix test
    mix escript.build
    printf "%s  %s\n" "$DEPENDENCY_LOCK_SHA256" /work/elixir/mix.lock | sha256sum -c -
  '
verify_sha256 "$source_tree/elixir/mix.lock" "$dependency_lock_sha256"

codex_output=$(docker run --rm "${docker_network_arguments[@]}" "$node_image" sh -c \
  "npm install --global '@openai/codex@$codex_version' >/dev/null && codex --version")
if [[ "$codex_output" != "codex-cli $codex_version" ]]; then
  printf 'ERROR: expected codex-cli %s, got %s\n' \
    "$codex_version" "${codex_output:-unavailable}" >&2
  exit 1
fi

mkdir -p "$output_dir"
install -m 0444 "$audit_evidence_dir/symphony-dependencies.tsv" \
  "$output_dir/symphony-dependencies.tsv"
python3 - "$output_dir/symphony-audit.json" <<PY
import datetime
import json
import pathlib

report = {
    "schema_version": 1,
    "completed_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
    "symphony_revision": "$symphony_revision",
    "phase_bridge_patch_sha256": "$patch_sha256",
    "phase_routing_patch_sha256": "$routing_patch_sha256",
    "dependency_lock_sha256": "$dependency_lock_sha256",
    "controller_fixture_sha256": "$test_fixture_sha256",
    "builder_image": "$builder_image",
    "audit_toolchain_dockerfile_sha256": "$audit_toolchain_dockerfile_sha256",
    "audit_toolchain_image_id": "$audit_toolchain_image_id",
    "node_image": "$node_image",
    "codex_version": "$codex_version",
    "checks": {
        "patch_order": "PASS",
        "mix_compile_warnings_as_errors": "PASS",
        "mix_test": "PASS",
        "mix_hex_audit": "PASS",
        "mix_escript_build": "PASS",
        "codex_version": "PASS",
    },
}
path = pathlib.Path(__import__("sys").argv[1])
path.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
path.chmod(0o444)
PY

printf 'PASS: Symphony audit report: %s\n' "$output_dir/symphony-audit.json"
printf 'PASS: dependency license inventory: %s\n' \
  "$output_dir/symphony-dependencies.tsv"
