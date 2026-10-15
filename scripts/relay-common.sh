#!/usr/bin/env bash

relay_repository="decentral-prospect/symposium-relay"
relay_source_repository="decentral-prospect/symposium-relay"
relay_asset="symposium-server-linux-amd64"
relay_version_pattern='^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z]+([.-][0-9A-Za-z]+)*)?$'
relay_sha256_pattern='^[0-9a-f]{64}$'
relay_revision_pattern='^[0-9a-f]{40}$'

relay_error() {
    echo "Relay verification error: $*" >&2
    return 1
}

require_relay_command() {
    local command_name="$1"
    command -v "$command_name" >/dev/null 2>&1 ||
        relay_error "required command is missing: $command_name"
}

validate_relay_lock() {
    local lock_file="$1"
    local repository source_repository version asset url sha256 source_revision expected_url

    require_relay_command jq || return 1
    [[ -f "$lock_file" ]] || relay_error "lock file does not exist: $lock_file" || return 1

    if ! jq -e '
        type == "object" and
        (.repository | type == "string" and length > 0) and
        (.version | type == "string" and length > 0) and
        (.asset | type == "string" and length > 0) and
        (.url | type == "string" and length > 0) and
        (.sha256 | type == "string" and length > 0) and
        (.source_revision | type == "string" and length > 0) and
        (.source_repository | type == "string" and length > 0)
    ' "$lock_file" >/dev/null 2>&1; then
        relay_error "lock file is malformed or is missing required string fields: $lock_file"
        return 1
    fi

    repository="$(jq -er '.repository' "$lock_file")"
    source_repository="$(jq -er '.source_repository' "$lock_file")"
    version="$(jq -er '.version' "$lock_file")"
    asset="$(jq -er '.asset' "$lock_file")"
    url="$(jq -er '.url' "$lock_file")"
    sha256="$(jq -er '.sha256' "$lock_file")"
    source_revision="$(jq -er '.source_revision' "$lock_file")"

    [[ "$repository" == "$relay_repository" ]] ||
        relay_error "unexpected relay repository: $repository" || return 1
    [[ "$source_repository" == "$relay_source_repository" ]] ||
        relay_error "unexpected relay source repository: $source_repository" || return 1
    [[ "$asset" == "$relay_asset" ]] ||
        relay_error "unexpected relay asset: $asset" || return 1
    [[ "$version" =~ $relay_version_pattern ]] ||
        relay_error "invalid relay release version: $version" || return 1
    [[ "$sha256" =~ $relay_sha256_pattern ]] ||
        relay_error "relay SHA-256 must be 64 lowercase hexadecimal characters" || return 1
    [[ "$source_revision" =~ $relay_revision_pattern ]] ||
        relay_error "relay source revision must be a full lowercase Git commit SHA" || return 1

    expected_url="https://github.com/${repository}/releases/download/${version}/${asset}"
    [[ "$url" == "$expected_url" ]] ||
        relay_error "relay URL is not the canonical locked release URL" || return 1
}

resolve_relay_tag_revision() {
    local repository="$1" version="$2" object object_type object_sha depth=0
    require_relay_command gh || return 1
    require_relay_command jq || return 1
    # Resolve refs/tags explicitly: /commits/VERSION can select a same-named branch.
    object="$(gh api "repos/$repository/git/ref/tags/$version" --jq .object)" || return 1
    while :; do
        object_type="$(jq -er '.type' <<<"$object")" || return 1
        object_sha="$(jq -er '.sha' <<<"$object")" || return 1
        [[ "$object_sha" =~ $relay_revision_pattern ]] ||
            relay_error "tag object did not resolve to a full SHA" || return 1
        case "$object_type" in
            commit) printf '%s\n' "$object_sha"; return 0 ;;
            tag)
                ((depth += 1))
                [[ "$depth" -le 10 ]] || relay_error "too many nested annotated tags" || return 1
                object="$(gh api "repos/$repository/git/tags/$object_sha" --jq .object)" || return 1
                ;;
            *) relay_error "release tag points to $object_type, not a commit"; return 1 ;;
        esac
    done
}

verify_relay_attestations() {
    local binary="$1" repository="$2" version="$3" source_revision="$4"
    local predicate
    require_relay_command gh || return 1
    for predicate in https://slsa.dev/provenance/v1 https://cyclonedx.org/bom; do
        gh attestation verify "$binary" --repo "$repository" \
            --signer-workflow "$repository/.github/workflows/release.yml" \
            --signer-digest "$source_revision" \
            --source-ref "refs/tags/$version" \
            --source-digest "$source_revision" \
            --deny-self-hosted-runners \
            --predicate-type "$predicate" || return 1
    done
}

verify_relay_sha256() {
    local file_path="$1"
    local expected_sha256="$2"
    local actual_sha256

    require_relay_command sha256sum || return 1
    [[ -f "$file_path" ]] || relay_error "file does not exist: $file_path" || return 1
    [[ "$expected_sha256" =~ $relay_sha256_pattern ]] ||
        relay_error "invalid expected SHA-256" || return 1

    actual_sha256="$(sha256sum "$file_path")"
    actual_sha256="${actual_sha256%% *}"
    [[ "$actual_sha256" == "$expected_sha256" ]] ||
        relay_error "SHA-256 mismatch for $(basename "$file_path"): expected $expected_sha256, got $actual_sha256" ||
        return 1
}
