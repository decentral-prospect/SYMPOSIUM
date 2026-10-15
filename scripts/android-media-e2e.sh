#!/usr/bin/env bash
set -Eeuo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
android_dir="$(cd "$script_dir/.." && pwd)"
relay_dir_input="${SYMPOSIUM_RELAY_DIR:-$android_dir/../S-server}"

if [[ ! -d "$relay_dir_input" ]]; then
    echo "Relay source directory does not exist: $relay_dir_input" >&2
    echo "Set SYMPOSIUM_RELAY_DIR to an explicit symposium-relay checkout." >&2
    exit 1
fi
relay_dir="$(cd "$relay_dir_input" && pwd)"

if [[ ! -f "$relay_dir/go.mod" ]]; then
    echo "Relay source directory does not contain go.mod: $relay_dir" >&2
    exit 1
fi

if [[ -n "${SYMPOSIUM_RELAY_REVISION:-}" ]]; then
    if ! git -C "$relay_dir" rev-parse --verify HEAD >/dev/null 2>&1; then
        echo "SYMPOSIUM_RELAY_REVISION requires a Git relay checkout." >&2
        exit 1
    fi
    expected_revision="$(git -C "$relay_dir" rev-parse "${SYMPOSIUM_RELAY_REVISION}^{commit}")"
    actual_revision="$(git -C "$relay_dir" rev-parse HEAD)"
    if [[ "$actual_revision" != "$expected_revision" ]]; then
        echo "Relay checkout is at $actual_revision, expected $expected_revision." >&2
        exit 1
    fi
fi

relay_host="${SYMPOSIUM_E2E_RELAY_HOST:-10.0.2.2}"
relay_port="${SYMPOSIUM_E2E_RELAY_PORT:-38443}"
admin_port="${SYMPOSIUM_E2E_ADMIN_PORT:-38002}"
room="android-media-e2e"
require_outbound_audio="${SYMPOSIUM_ANDROID_E2E_REQUIRE_OUTBOUND_AUDIO:-true}"
relay_mode="${SYMPOSIUM_E2E_RELAY_MODE:-source}"
artifact_dir="${SYMPOSIUM_E2E_ARTIFACT_DIR:-}"
work_dir="$(mktemp -d /tmp/symposium-android-media-e2e.XXXXXX)"
admin_token_file="$work_dir/admin-token"
relay_pid=""
peer_pid=""

if [[ "$require_outbound_audio" != "true" && "$require_outbound_audio" != "false" ]]; then
    echo "SYMPOSIUM_ANDROID_E2E_REQUIRE_OUTBOUND_AUDIO must be true or false" >&2
    exit 1
fi
if [[ "$relay_mode" != "source" && "$relay_mode" != "bundled" ]]; then
    echo "SYMPOSIUM_E2E_RELAY_MODE must be source or bundled" >&2
    exit 1
fi

cleanup() {
    status=$?
    if [[ -n "$peer_pid" ]]; then
        kill "$peer_pid" 2>/dev/null || true
        wait "$peer_pid" 2>/dev/null || true
    fi
    if [[ -n "$relay_pid" ]]; then
        kill "$relay_pid" 2>/dev/null || true
        wait "$relay_pid" 2>/dev/null || true
    fi
    if [[ $status -ne 0 ]]; then
        echo "Android media E2E failed. Relay log:"
        tail -n 80 "$work_dir/relay.log" 2>/dev/null || true
        echo "Host media peer log:"
        tail -n 80 "$work_dir/peer.log" 2>/dev/null || true
        echo "Android instrumentation log:"
        tail -n 80 "$work_dir/android.log" 2>/dev/null || true
    fi
    if [[ -n "$artifact_dir" ]]; then
        mkdir -p "$artifact_dir"
        for log_name in relay peer android; do
            [[ ! -f "$work_dir/$log_name.log" ]] || cp "$work_dir/$log_name.log" "$artifact_dir/$log_name.log"
        done
    fi
    if [[ "$work_dir" == /tmp/symposium-android-media-e2e.* ]]; then
        rm -rf -- "$work_dir"
    fi
    exit "$status"
}
trap cleanup EXIT INT TERM

printf '%s\n' "android-media-e2e-admin" >"$admin_token_file"
chmod 600 "$admin_token_file"

admin_header() {
    printf 'X-Relay-Key: '
    tr -d '\r\n' <"$admin_token_file"
    printf '\n'
}

for command_name in adb curl go jq openssl timeout tr; do
    if ! command -v "$command_name" >/dev/null 2>&1; then
        echo "Required command is missing: $command_name" >&2
        exit 1
    fi
done

if ! adb get-state >/dev/null 2>&1; then
    echo "No Android device/emulator is connected. Start an AVD or connect a device first." >&2
    exit 1
fi

if [[ "$relay_host" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    relay_san="IP:$relay_host"
else
    relay_san="DNS:$relay_host"
fi

openssl req -x509 -newkey rsa:2048 -nodes -days 1 \
    -subj "/CN=symposium-android-e2e" \
    -addext "subjectAltName=$relay_san,IP:127.0.0.1,DNS:localhost" \
    -keyout "$work_dir/relay.key" \
    -out "$work_dir/relay.crt" >/dev/null 2>&1

tls_pin="sha256/$(openssl x509 -in "$work_dir/relay.crt" -pubkey -noout \
    | openssl pkey -pubin -outform DER 2>/dev/null \
    | openssl dgst -sha256 -binary \
    | openssl base64 -A)"
e2ee_secret="$(openssl rand -base64 32 | tr '+/' '-_' | tr -d '=\n')"

if [[ "$relay_mode" == "bundled" ]]; then
    # Exercise exactly the source-built binary packaged in the APK.
    source "$script_dir/relay-common.sh"
    validate_relay_lock "$android_dir/relay.lock.json"
    bundled_binary="$android_dir/app/src/main/assets/symposium/symposium-server-linux-amd64"
    python3 "$script_dir/relay-source.py" verify-bundle
    cp "$bundled_binary" "$work_dir/symposium-relay"
else
    (cd "$relay_dir" && go build -o "$work_dir/symposium-relay" .)
fi
chmod 700 "$work_dir/symposium-relay"
echo "Testing relay mode: $relay_mode"

"$work_dir/symposium-relay" \
    --addr "0.0.0.0:$relay_port" \
    --admin-addr "127.0.0.1:$admin_port" \
    --admin-token-file "$admin_token_file" \
    --instance-name "android-media-e2e" \
    --rooms-db "$work_dir/rooms.db" \
    --db-table open_rooms \
    --db-room-column room_name \
    --db-key-column moderator_key \
    --tls-cert "$work_dir/relay.crt" \
    --tls-key "$work_dir/relay.key" \
    --loopback \
    --allow-private-ice >"$work_dir/relay.log" 2>&1 &
relay_pid=$!

relay_ready=false
for _ in $(seq 1 80); do
    if admin_header | curl -fsS -H @- \
        "http://127.0.0.1:$admin_port/admin/version" >/dev/null 2>&1; then
        relay_ready=true
        break
    fi
    if ! kill -0 "$relay_pid" 2>/dev/null; then
        echo "Relay exited before becoming ready" >&2
        exit 1
    fi
    sleep 0.25
done
if [[ "$relay_ready" != true ]]; then
    echo "Relay did not become ready" >&2
    exit 1
fi

room_response="$(admin_header | curl -fsS -X POST -H @- \
    "http://127.0.0.1:$admin_port/admin/open-room?name=$room")"
moderator_key="$(jq -er '.moderator_key' <<<"$room_response")"
room_instance="$(jq -r '.roomInstance // empty' <<<"$room_response")"

# Build before starting the bounded host peer so a cold Gradle compilation
# or APK installation cannot consume the media verification timeout.
(
    cd "$android_dir"
    ./gradlew --dependency-verification strict \
        :app:assembleDebug :app:assembleDebugAndroidTest \
        -PTELEMETRY_ENDPOINT= \
        -PTELEMETRY_TOKEN=
)
# API 25 can stall indefinitely on streamed installation. Push installation is
# supported on both the oldest supported emulator and current Android devices.
timeout -k 5s 90s adb install --no-streaming -r -t "$android_dir/app/build/outputs/apk/debug/app-debug.apk"
timeout -k 5s 90s adb install --no-streaming -r -t "$android_dir/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
(cd "$relay_dir" && go test -c -o "$work_dir/host-peer.test" .)

SYMPOSIUM_ANDROID_MEDIA_E2E=1 \
    SYMPOSIUM_ANDROID_E2E_WS_URL="wss://127.0.0.1:$relay_port/ws" \
    SYMPOSIUM_ANDROID_E2E_ROOM="$room" \
    SYMPOSIUM_ANDROID_E2E_ROOM_INSTANCE="$room_instance" \
    SYMPOSIUM_ANDROID_E2E_MODERATOR_KEY="$moderator_key" \
    SYMPOSIUM_ANDROID_E2E_SECRET="$e2ee_secret" \
    SYMPOSIUM_ANDROID_E2E_REQUIRE_OUTBOUND_AUDIO="$require_outbound_audio" \
    "$work_dir/host-peer.test" -test.run '^TestAndroidClientMediaE2EPeer$' -test.count=1 -test.v \
    >"$work_dir/peer.log" 2>&1 &
peer_pid=$!

peer_ready=false
for _ in $(seq 1 120); do
    if grep -q "ANDROID_MEDIA_E2E_PEER_READY" "$work_dir/peer.log"; then
        peer_ready=true
        break
    fi
    if ! kill -0 "$peer_pid" 2>/dev/null; then
        echo "Host media peer exited before becoming ready" >&2
        exit 1
    fi
    sleep 0.25
done
if [[ "$peer_ready" != true ]]; then
    echo "Host media peer did not become ready" >&2
    exit 1
fi

timeout -k 5s 180s adb shell am instrument -w -r \
    -e class com.decentralprospect.symposium.AndroidMediaE2ETest \
    -e e2eRelayAddress "$relay_host:$relay_port" \
    -e e2eRoom "$room" \
    -e e2eRoomInstance "$room_instance" \
    -e e2eModeratorKey "$moderator_key" \
    -e e2eTlsPin "$tls_pin" \
    -e e2eSecret "$e2ee_secret" \
    -e e2eRequireOutboundAudio "$require_outbound_audio" \
    com.decentralprospect.symposium.test/androidx.test.runner.AndroidJUnitRunner \
    >"$work_dir/android.log" 2>&1
cat "$work_dir/android.log"
# adb exits successfully even when the instrumentation itself fails or crashes.
if ! grep -Eq '^OK \(1 test\)' "$work_dir/android.log"; then
    echo "Android instrumentation did not pass its media test" >&2
    exit 1
fi

if ! wait "$peer_pid"; then
    peer_pid=""
    echo "Host media peer did not authenticate the required Android tracks" >&2
    exit 1
fi
peer_pid=""

if grep -Fq -- "$e2ee_secret" "$work_dir/relay.log"; then
    echo "Conference E2EE secret leaked into the relay log" >&2
    exit 1
fi

if [[ "$require_outbound_audio" == "true" ]]; then
    echo "Android media E2E passed: authenticated audio/video frame encryption in both directions through the standalone relay."
else
    echo "Android media E2E passed: authenticated inbound audio/video and outbound video through the standalone relay; outbound audio was skipped because the AVD has no input device."
fi
