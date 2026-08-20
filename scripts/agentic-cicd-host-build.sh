#!/usr/bin/env bash
set -euo pipefail

output_dir=""
target_repository=""
github_app_login=""
reviewer=""
symphony_source="${SYMPHONY_SOURCE:-$HOME/source/symphony}"
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
lock_file="$repo_root/config/agentic-cicd/symphony.lock.json"

usage() {
  cat <<'EOF'
Usage: agentic-cicd-host-build.sh --output-dir <path> --repository <owner/name> [options]

Builds a reviewed host-native Symphony/Codex bundle. Both source repositories
must be clean; the output contains no credentials and performs no model call.

Options:
  --output-dir <path>       New or empty output directory
  --symphony-source <path>  Clean checkout at the locked Symphony revision
  --repository <owner/name> Exact disposable Level 2 repository
  --github-app-login <name> GitHub App bot login
  --reviewer <name>         Human reviewer login
EOF
}

while (($#)); do
  case "$1" in
    --output-dir)
      output_dir=${2:?missing output directory}
      shift 2
      ;;
    --symphony-source)
      symphony_source=${2:?missing Symphony source}
      shift 2
      ;;
    --repository)
      target_repository=${2:?missing repository}
      shift 2
      ;;
    --github-app-login)
      github_app_login=${2:?missing GitHub App login}
      shift 2
      ;;
    --reviewer)
      reviewer=${2:?missing reviewer}
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      printf 'Unknown argument: %s\n' "$1" >&2
      exit 2
      ;;
  esac
done

[[ -n "$output_dir" ]] || {
  printf '%s\n' 'ERROR: --output-dir is required.' >&2
  exit 2
}
[[ -n "$target_repository" && -n "$github_app_login" && -n "$reviewer" ]] || {
  printf '%s\n' 'ERROR: repository, GitHub App login, and reviewer are required.' >&2
  exit 2
}
for command in codex erl escript git mix node python3 sha256sum tar; do
  command -v "$command" >/dev/null || {
    printf 'ERROR: required command is missing: %s\n' "$command" >&2
    exit 2
  }
done

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
verify_sha256() {
  local path=$1
  local expected=$2
  local actual
  actual=$(sha256sum "$path" | awk '{print $1}')
  [[ "$actual" == "$expected" ]] || {
    printf 'ERROR: digest mismatch for %s: %s\n' "$path" "$actual" >&2
    exit 2
  }
}

symphony_revision=$(read_lock commit)
patch_relative=$(read_lock patch)
patch_sha256=$(read_lock patch_sha256)
routing_patch_relative=$(read_lock routing_patch)
routing_patch_sha256=$(read_lock routing_patch_sha256)
dependency_lock_relative=$(read_lock dependency_lock)
dependency_lock_sha256=$(read_lock dependency_lock_sha256)
[[ "$patch_relative" == deploy/kubernetes/agentic-cicd/patches/symphony-phase-bridge.patch \
  && "$routing_patch_relative" == deploy/kubernetes/agentic-cicd/patches/symphony-phase-routing.patch \
  && "$dependency_lock_relative" == deploy/kubernetes/agentic-cicd/patches/symphony-mix.lock ]] || {
  printf '%s\n' 'ERROR: Symphony lock references unexpected inputs.' >&2
  exit 2
}
verify_sha256 "$repo_root/$patch_relative" "$patch_sha256"
verify_sha256 "$repo_root/$routing_patch_relative" "$routing_patch_sha256"
verify_sha256 "$repo_root/$dependency_lock_relative" "$dependency_lock_sha256"

controller_revision=$(git -C "$repo_root" rev-parse HEAD 2>/dev/null || true)
[[ "$controller_revision" =~ ^[0-9a-f]{40}$ \
  && -z "$(git -C "$repo_root" status --porcelain --untracked-files=all)" ]] || {
  printf '%s\n' 'ERROR: j-store controller source must be a clean full revision.' >&2
  exit 2
}
[[ "$(git -C "$symphony_source" rev-parse HEAD 2>/dev/null || true)" == "$symphony_revision" \
  && -z "$(git -C "$symphony_source" status --porcelain --untracked-files=all)" ]] || {
  printf 'ERROR: Symphony source must be clean at %s.\n' "$symphony_revision" >&2
  exit 2
}

codex_output=$(codex --version 2>/dev/null || true)
[[ "$codex_output" =~ ^codex-cli\ ([0-9]+\.[0-9]+\.[0-9]+)$ ]] || {
  printf 'ERROR: Codex CLI must be stable, got: %s\n' "$codex_output" >&2
  exit 2
}
codex_version=${BASH_REMATCH[1]}
codex sandbox -- /bin/true
codex_entry=$(readlink -f "$(command -v codex)")
codex_module=${codex_entry%/bin/codex.js}
[[ -f "$codex_module/package.json" && -x "$(command -v node)" ]] || {
  printf '%s\n' 'ERROR: Codex npm module layout is unsupported.' >&2
  exit 2
}

if [[ -e "$output_dir" && -n "$(find "$output_dir" -mindepth 1 -maxdepth 1 -print -quit)" ]]; then
  printf 'ERROR: output directory is not empty: %s\n' "$output_dir" >&2
  exit 2
fi
mkdir -p "$output_dir"
temporary_root=$(mktemp -d "${TMPDIR:-/tmp}/jstore-host-build.XXXXXX")
cleanup() {
  rm -rf -- "$temporary_root"
}
trap cleanup EXIT
symphony_build="$temporary_root/symphony"
controller_source="$temporary_root/controller-source"
bundle_root="$temporary_root/jstore-agentic-cicd"
payload="$bundle_root/payload"
runtime_profile="$temporary_root/runtime-profile"
mkdir -p "$symphony_build" "$controller_source" "$payload/bin" \
  "$payload/lib/node_modules/@openai" "$payload/controller" "$bundle_root/deploy"

profile_digests=$(PYTHONPATH="$repo_root/scripts" python3 - \
  "$repo_root/config/agentic-cicd/state-contract.json" \
  "$repo_root/config/agentic-cicd/state-contract.level2-disposable.example.json" \
  "$target_repository" "$runtime_profile" "$github_app_login" "$reviewer" <<'PY'
import pathlib
import re
import sys

from agentic_cicd.github_adapter import validate_handoff_logins
from agentic_cicd.runtime_binding import prepare_disposable_runtime_profile

authoritative, candidate, repository, output, app_login, reviewer = sys.argv[1:]
if re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository) is None:
    raise SystemExit("invalid target repository")
validate_handoff_logins(
    github_app_login=app_login,
    reviewer=reviewer,
    require_app_login=True,
    require_reviewer=True,
)
digests = prepare_disposable_runtime_profile(
    authoritative_contract_path=pathlib.Path(authoritative),
    candidate_contract_path=pathlib.Path(candidate),
    repository=repository,
    output_directory=pathlib.Path(output),
)
print(*digests)
PY
)
read -r state_contract_sha256 runtime_binding_sha256 <<<"$profile_digests"

git -C "$symphony_source" archive "$symphony_revision" \
  | tar -x -C "$symphony_build"
git -C "$repo_root" archive "$controller_revision" \
  WORKFLOW.md config/agentic-cicd deploy/host/agentic-cicd \
  scripts/agentic_cicd scripts/agentic-cicd-controller.py \
  | tar -x -C "$controller_source"
git -C "$symphony_build" apply --recount "$repo_root/$patch_relative"
git -C "$symphony_build" apply --recount "$repo_root/$routing_patch_relative"
install -m 0444 "$repo_root/$dependency_lock_relative" "$symphony_build/elixir/mix.lock"
(
  cd "$symphony_build/elixir"
  mix deps.get
  mix compile --warnings-as-errors
  mix test
  MIX_ENV=prod mix deps.get --only prod
  MIX_ENV=prod mix escript.build
)

install -m 0555 "$symphony_build/elixir/bin/symphony" "$payload/bin/symphony"
install -m 0555 "$(command -v node)" "$payload/bin/node"
cp -a "$codex_module" "$payload/lib/node_modules/@openai/codex"
ln -s ../lib/node_modules/@openai/codex/bin/codex.js "$payload/bin/codex"
install -m 0555 "$controller_source/deploy/host/agentic-cicd/run-symphony.sh" \
  "$payload/bin/run-symphony"
install -m 0444 "$controller_source/deploy/host/agentic-cicd/WORKFLOW.md" \
  "$payload/WORKFLOW.md"
install -m 0444 "$controller_source/scripts/agentic-cicd-controller.py" \
  "$payload/controller/controller.py"
cp -a "$controller_source/scripts/agentic_cicd" "$payload/controller/agentic_cicd"
cp -a "$controller_source/config/agentic-cicd" "$payload/config"
install -m 0444 "$runtime_profile/state-contract.json" \
  "$payload/controller/state-contract.json"
install -m 0444 "$runtime_profile/runtime-binding.json" \
  "$payload/controller/runtime-binding.json"
install -m 0444 "$controller_source/deploy/host/agentic-cicd/jstore-agentic-cicd.service" \
  "$bundle_root/deploy/jstore-agentic-cicd.service"
python3 - "$bundle_root/deploy/runtime.env" \
  "$target_repository" "$github_app_login" "$reviewer" <<'PY'
import pathlib
import sys

path, repository, app_login, reviewer = sys.argv[1:]
pathlib.Path(path).write_text(
    f"JSTORE_SYMPHONY_REPOSITORY={repository}\n"
    f"JSTORE_SYMPHONY_REPOSITORY_URL=https://github.com/{repository}.git\n"
    f"JSTORE_GITHUB_APP_LOGIN={app_login}\n"
    f"JSTORE_GITHUB_REVIEWER={reviewer}\n",
    encoding="utf-8",
)
PY
chmod 0444 "$bundle_root/deploy/runtime.env"

workflow_sha256=$(sha256sum "$payload/WORKFLOW.md" | awk '{print $1}')
otp_release=$(erl -noshell -eval 'io:format("~s", [erlang:system_info(otp_release)]), halt().')
cat >"$payload/runtime-revisions" <<EOF
SYMPHONY_REVISION=$symphony_revision
JSTORE_CONTROLLER_REVISION=$controller_revision
CODEX_VERSION=$codex_version
SYMPHONY_PATCH_SHA256=$patch_sha256
SYMPHONY_ROUTING_PATCH_SHA256=$routing_patch_sha256
SYMPHONY_DEPENDENCY_LOCK_SHA256=$dependency_lock_sha256
WORKFLOW_SHA256=$workflow_sha256
JSTORE_STATE_CONTRACT_SHA256=$state_contract_sha256
JSTORE_RUNTIME_BINDING_SHA256=$runtime_binding_sha256
OTP_RELEASE=$otp_release
EOF
chmod 0444 "$payload/runtime-revisions"

(
  cd "$bundle_root"
  find deploy payload -type f -print0 \
    | LC_ALL=C sort -z \
    | xargs -0 sha256sum >manifest.sha256
  printf '%s  %s\n' \
    "$(sha256sum payload/bin/codex | awk '{print $1}')" \
    payload/bin/codex >>manifest.sha256
)
bundle_name="jstore-agentic-cicd-host-${controller_revision:0:12}-symphony-${symphony_revision:0:12}-codex-$codex_version.tar.gz"
bundle_path="$output_dir/$bundle_name"
tar --sort=name --mtime='UTC 1970-01-01' --owner=0 --group=0 --numeric-owner \
  --create --use-compress-program='gzip -n' --file "$bundle_path" \
  --directory "$temporary_root" jstore-agentic-cicd
bundle_sha256=$(sha256sum "$bundle_path" | awk '{print $1}')

python3 - "$output_dir/${bundle_name%.tar.gz}.source.json" <<PY
import json
import pathlib
import sys

path = pathlib.Path(sys.argv[1])
path.write_text(json.dumps({
    "schema_version": 1,
    "artifact": "$bundle_name",
    "artifact_sha256": "$bundle_sha256",
    "controller_revision": "$controller_revision",
    "symphony_revision": "$symphony_revision",
    "codex_version": "$codex_version",
    "otp_release": "$otp_release",
    "workflow_sha256": "$workflow_sha256",
    "state_contract_sha256": "$state_contract_sha256",
    "runtime_binding_sha256": "$runtime_binding_sha256",
    "target_repository": "$target_repository",
    "contains_credentials": False,
    "sandbox_smoke": "PASS",
}, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY
printf 'HOST_BUNDLE=%s\nHOST_BUNDLE_SHA256=%s\nMODEL_CALLS=0\n' \
  "$bundle_path" "$bundle_sha256"
