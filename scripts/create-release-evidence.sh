#!/usr/bin/env bash
set -euo pipefail

usage() {
  printf '%s\n' "Usage: $0 <tag> [--ci]"
  printf '%s\n' 'Creates a release evidence bundle without publishing it.'
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  usage
  exit 0
fi

tag="${1:-}"
mode="${2:-}"
if [[ -z "$tag" || ( -n "$mode" && "$mode" != "--ci" ) || $# -gt 2 ]]; then
  usage >&2
  exit 2
fi
if [[ ! "$tag" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]]; then
  printf 'Invalid release tag: %s\n' "$tag" >&2
  exit 2
fi

repo_root="$(git rev-parse --show-toplevel)"
cd "$repo_root"

head_commit="$(git rev-parse HEAD)"
tag_commit="$(git rev-parse "$tag^{commit}")"
if [[ "$tag_commit" != "$head_commit" ]]; then
  printf 'Tag %s points to %s, not current HEAD %s.\n' "$tag" "$tag_commit" "$head_commit" >&2
  exit 1
fi
if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
  printf '%s\n' 'Tracked worktree changes exist; release evidence requires a clean commit.' >&2
  exit 1
fi

tag_signature="unverified-in-ci"
if [[ "$mode" != "--ci" ]]; then
  git verify-tag "$tag"
  tag_signature="verified"
elif git verify-tag "$tag" >/dev/null 2>&1; then
  tag_signature="verified"
fi

evidence_dir="$repo_root/build/release-evidence/$tag"
if [[ -e "$evidence_dir" ]]; then
  printf 'Evidence directory already exists: %s\n' "$evidence_dir" >&2
  exit 1
fi
mkdir -p \
  "$evidence_dir/artifacts" \
  "$evidence_dir/dependency-licenses" \
  "$evidence_dir/metadata" \
  "$evidence_dir/source"

./scripts/quality-gate.sh 2>&1 | tee "$evidence_dir/quality-gate.log"
./gradlew :j-store-boot:bootJar :j-store-boot:cyclonedxDirectBom licensee \
  --no-daemon --console=plain

git archive \
  --format=tar.gz \
  --prefix="j-store-$tag/" \
  --output="$evidence_dir/source/j-store-$tag.tar.gz" \
  "$tag"

find j-store-boot/build/libs -maxdepth 1 -type f -name '*.jar' -exec cp {} "$evidence_dir/artifacts/" \;
cp j-store-boot/build/reports/cyclonedx-direct/bom.json "$evidence_dir/artifacts/cyclonedx-bom.json"
cp LICENSE THIRD_PARTY.md config/licenses/file-ownership.toml "$evidence_dir/metadata/"
cp build/reports/licenses/file-ownership.json "$evidence_dir/metadata/"

while IFS= read -r report_dir; do
  module="${report_dir#./}"
  module="${module%%/build/reports/licensee}"
  destination="$evidence_dir/dependency-licenses/$module"
  mkdir -p "$destination"
  cp "$report_dir/artifacts.json" "$report_dir/validation.txt" "$destination/"
done < <(find . -type d -path '*/build/reports/licensee' | sort)

{
  printf 'tag=%s\n' "$tag"
  printf 'commit=%s\n' "$head_commit"
  printf 'tree=%s\n' "$(git rev-parse HEAD^{tree})"
  printf 'commit_time=%s\n' "$(git show -s --format=%cI HEAD)"
  printf 'evidence_time=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'tag_signature=%s\n' "$tag_signature"
  printf 'java=%s\n' "$(java -version 2>&1 | sed -n '1p')"
  printf 'gradle=%s\n' "$(./gradlew --version --no-daemon | sed -n 's/^Gradle /Gradle /p')"
} > "$evidence_dir/metadata/build-info.txt"

(
  cd "$evidence_dir"
  find . -type f ! -name SHA256SUMS -print0 | sort -z | xargs -0 sha256sum > SHA256SUMS
)

printf 'Release evidence created at %s\n' "$evidence_dir"
