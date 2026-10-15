#!/usr/bin/env bash
set -Eeuo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# shellcheck source=relay-common.sh
source "$script_dir/relay-common.sh"

for command_name in jq sha256sum mktemp; do
    require_relay_command "$command_name"
done

work_dir="$(mktemp -d "${TMPDIR:-/tmp}/symposium-relay-tests.XXXXXX")"

cleanup() {
    local status=$?
    if [[ "$work_dir" == "${TMPDIR:-/tmp}/symposium-relay-tests."* ]]; then
        rm -rf -- "$work_dir"
    fi
    exit "$status"
}
trap cleanup EXIT INT TERM

printf '{not-json}\n' >"$work_dir/malformed.json"
if validate_relay_lock "$work_dir/malformed.json" >/dev/null 2>&1; then
    echo "Malformed relay lock was accepted" >&2
    exit 1
fi

printf 'relay fixture\n' >"$work_dir/relay"
if verify_relay_sha256 "$work_dir/relay" \
    aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa \
    >/dev/null 2>&1; then
    echo "Incorrect relay SHA-256 was accepted" >&2
    exit 1
fi

actual_sha256="$(sha256sum "$work_dir/relay")"
actual_sha256="${actual_sha256%% *}"
verify_relay_sha256 "$work_dir/relay" "$actual_sha256"

cat >"$work_dir/valid.json" <<EOF
{
  "repository": "$relay_repository",
  "version": "0.3.4-rc.2",
  "asset": "$relay_asset",
  "url": "https://github.com/$relay_repository/releases/download/0.3.4-rc.2/$relay_asset",
  "sha256": "$actual_sha256",
  "source_revision": "1111111111111111111111111111111111111111",
  "source_repository": "$relay_source_repository"
}
EOF
validate_relay_lock "$work_dir/valid.json"

for revision in '' main 1111111; do
    jq --arg revision "$revision" '.source_revision = $revision' "$work_dir/valid.json" > "$work_dir/bad-revision.json"
    if validate_relay_lock "$work_dir/bad-revision.json" >/dev/null 2>&1; then
        echo "Unpinned source revision was accepted" >&2
        exit 1
    fi
done

# Verify the policy passed to gh for both provenance and SBOM. A rejected
# attestation must propagate failure to callers before they replace the lock.
gh() {
    printf '%s\n' "$*" >> "$work_dir/attestation-calls"
    [[ " $* " == *" --source-ref refs/tags/0.3.4-rc.2 "* ]] &&
        [[ " $* " == *" --source-digest 1111111111111111111111111111111111111111 "* ]] &&
        [[ " $* " == *" --signer-digest 1111111111111111111111111111111111111111 "* ]] &&
        [[ " $* " == *" --signer-workflow $relay_source_repository/.github/workflows/release.yml "* ]] &&
        [[ " $* " == *" --deny-self-hosted-runners "* ]]
}
verify_relay_attestations "$work_dir/relay" "$relay_source_repository" 0.3.4-rc.2 1111111111111111111111111111111111111111
[[ "$(wc -l < "$work_dir/attestation-calls")" -eq 2 ]]
grep -q 'https://slsa.dev/provenance/v1' "$work_dir/attestation-calls"
grep -q 'https://cyclonedx.org/bom' "$work_dir/attestation-calls"
gh() { return 1; }
if verify_relay_attestations "$work_dir/relay" "$relay_source_repository" 0.3.4-rc.2 1111111111111111111111111111111111111111; then
    echo "Failed attestation was accepted" >&2
    exit 1
fi
unset -f gh

echo "Relay lock validation and checksum failure tests passed."

bash "$script_dir/test-relay-release-flow.sh"
