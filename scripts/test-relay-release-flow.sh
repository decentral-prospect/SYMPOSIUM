#!/usr/bin/env bash
set -Eeuo pipefail
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$script_dir/relay-common.sh"
work_dir="$(mktemp -d "${TMPDIR:-/tmp}/symposium-release-flow.XXXXXX")"
trap 'rm -rf -- "$work_dir"' EXIT
mkdir -p "$work_dir/repo/scripts" "$work_dir/bin" "$work_dir/fixtures"
cp "$script_dir/"{relay-common,update-relay-lock,verify-relay-provenance}.sh "$work_dir/repo/scripts/"
export FLOW_FIXTURES="$work_dir/fixtures" FLOW_CALLS="$work_dir/calls"
export FLOW_REPOSITORY="$relay_repository" FLOW_VERSION=0.3.5-rc.12
export FLOW_REVISION=1111111111111111111111111111111111111111 FLOW_FAIL=""
export FLOW_PUBLIC=false
[[ -z "${relay_source_repository:-}" ]] || FLOW_PUBLIC=true
printf 'dev or public relay fixture\n' > "$FLOW_FIXTURES/$relay_asset"
checksum="$(sha256sum "$FLOW_FIXTURES/$relay_asset")"; checksum="${checksum%% *}"
printf '%s  %s\n' "$checksum" "$relay_asset" > "$FLOW_FIXTURES/SHA256SUMS"
jq -n --arg repository "$relay_repository" --arg version "$FLOW_VERSION" \
    --arg asset "$relay_asset" --arg checksum "$checksum" --argjson immutable "$FLOW_PUBLIC" \
    '{tag_name:$version,draft:false,immutable:$immutable,assets:[
      {name:$asset,browser_download_url:("https://github.com/"+$repository+"/releases/download/"+$version+"/"+$asset),digest:("sha256:"+$checksum)},
      {name:"SHA256SUMS"}]}' > "$FLOW_FIXTURES/release.json"
cat > "$work_dir/bin/gh" <<'MOCK'
#!/usr/bin/env bash
set -Eeuo pipefail
printf '%s\n' "$*" >> "$FLOW_CALLS"
case "$1 $2" in
    'api repos/'*)
        case "$2" in
            */releases/tags/*) cat "$FLOW_FIXTURES/release.json" ;;
            */git/ref/tags/*)
                [[ "$FLOW_PUBLIC" == true ]] || exit 81
                [[ "$FLOW_FAIL" != tag ]] || exit 1
                printf '{"type":"tag","sha":"2222222222222222222222222222222222222222"}\n'
                ;;
            */git/tags/*)
                [[ "$FLOW_PUBLIC" == true ]] || exit 82
                printf '{"type":"commit","sha":"%s"}\n' "$FLOW_REVISION"
                ;;
            *) echo "Unexpected API request: $2" >&2; exit 83 ;;
        esac
        ;;
    'release download')
        dir=""; pattern=""
        while [[ $# -gt 0 ]]; do
            case "$1" in
                --dir) dir="$2"; shift ;;
                --pattern) pattern="$2"; shift ;;
            esac
            shift
        done
        cp "$FLOW_FIXTURES/$pattern" "$dir/$pattern"
        ;;
    'attestation verify')
        [[ "$FLOW_PUBLIC" == true ]] || exit 84
        [[ " $* " == *" --source-ref refs/tags/$FLOW_VERSION "* ]]
        [[ " $* " == *" --source-digest $FLOW_REVISION "* ]]
        [[ " $* " == *" --signer-digest $FLOW_REVISION "* ]]
        [[ " $* " == *" --signer-workflow $FLOW_REPOSITORY/.github/workflows/release.yml "* ]]
        [[ " $* " == *" --deny-self-hosted-runners "* ]]
        [[ "$FLOW_FAIL" != attestation ]]
        ;;
    *) echo "Unexpected gh call: $*" >&2; exit 85 ;;
esac
MOCK
chmod +x "$work_dir/bin/gh"
export PATH="$work_dir/bin:$PATH"
update_lock() { bash "$work_dir/repo/scripts/update-relay-lock.sh" "$FLOW_VERSION"; }
verify_lock() { bash "$work_dir/repo/scripts/verify-relay-provenance.sh"; }
expect_failure() {
    if "$@" > "$work_dir/failure.log" 2>&1; then
        echo "Unexpected success: $*" >&2
        exit 1
    fi
    cmp "$work_dir/accepted-lock.json" "$work_dir/repo/relay.lock.json"
}
update_lock > "$work_dir/update.log" 2>&1 || { cat "$work_dir/update.log"; exit 1; }
validate_relay_lock "$work_dir/repo/relay.lock.json"
cp "$work_dir/repo/relay.lock.json" "$work_dir/accepted-lock.json"
mkdir -p "$work_dir/repo/app/src/main/assets/symposium"
cp "$FLOW_FIXTURES/$relay_asset" "$work_dir/repo/app/src/main/assets/symposium/$relay_asset"
verify_lock > "$work_dir/verify.log" 2>&1 || { cat "$work_dir/verify.log"; exit 1; }
if [[ "$FLOW_PUBLIC" == false ]]; then
    jq -e 'has("source_revision") == false and has("source_repository") == false' "$work_dir/accepted-lock.json" >/dev/null
    if grep -Eq 'attestation|/git/|/commits/' "$FLOW_CALLS"; then
        echo "Dev flow requested public provenance" >&2; exit 1
    fi
else
    jq -e --arg revision "$FLOW_REVISION" '.source_revision == $revision' "$work_dir/accepted-lock.json" >/dev/null
    for failure in attestation tag; do
        export FLOW_FAIL="$failure"
        expect_failure update_lock
        expect_failure verify_lock
    done
    export FLOW_FAIL=""
    export FLOW_REVISION=3333333333333333333333333333333333333333
    expect_failure verify_lock
    export FLOW_REVISION=1111111111111111111111111111111111111111
fi
cp "$FLOW_FIXTURES/release.json" "$work_dir/valid-release.json"
for mutation in '.draft = true' '.tag_name = "wrong"' '.assets[0].digest = "sha256:bad"' '.assets += [.assets[0]]' '.assets[0].browser_download_url = "https://example.invalid/relay"'; do
    jq "$mutation" "$work_dir/valid-release.json" > "$FLOW_FIXTURES/release.json"
    expect_failure update_lock
    expect_failure verify_lock
 done
cp "$work_dir/valid-release.json" "$FLOW_FIXTURES/release.json"
if [[ "$FLOW_PUBLIC" == true ]]; then
    jq '.immutable = false' "$work_dir/valid-release.json" > "$FLOW_FIXTURES/release.json"
    expect_failure update_lock
    expect_failure verify_lock
    cp "$work_dir/valid-release.json" "$FLOW_FIXTURES/release.json"
    printf '%064d  %s\n' 0 "$relay_asset" > "$FLOW_FIXTURES/SHA256SUMS"
    expect_failure update_lock
fi
printf 'corrupt bundled relay\n' > "$work_dir/repo/app/src/main/assets/symposium/$relay_asset"
expect_failure verify_lock
if [[ "$FLOW_PUBLIC" == true ]]; then
    # Public source builds verify the upstream asset separately, preserving the bundle.
    before="$(sha256sum "$work_dir/repo/app/src/main/assets/symposium/$relay_asset")"
    bash "$work_dir/repo/scripts/verify-relay-provenance.sh" --download > "$work_dir/download-verify.log" 2>&1 || {
        cat "$work_dir/download-verify.log"; exit 1;
    }
    [[ "$before" == "$(sha256sum "$work_dir/repo/app/src/main/assets/symposium/$relay_asset")" ]]
fi
echo "Release flow tests passed for $relay_repository (failed checks preserve the lock)."
