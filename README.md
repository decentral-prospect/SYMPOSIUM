# Symposium Android Client



Privacy-focused conference calls for communities operating under censorship and network restrictions.



## About



Symposium is an open-source Android application for audio and video conferences designed to remain usable in environments with unstable connectivity, network interference, and censorship.

The application connects to self-hosted Symposium Relay servers and uses WebRTC for real-time media transmission.










## Requirements



* Android 7.0 (API 24) or newer

* Microphone permission

* Camera permission (video calls only)

* Internet connection





## Telemetry



Symposium uses its own minimal telemetry service. Telemetry is disabled by default and no installation ID, lifecycle event, consent event, or diagnostic data is transmitted until the user explicitly opts in.

After explicit consent, Symposium sends a limited set of anonymous lifecycle and operational metrics to improve reliability. Revoking consent immediately stops delivery and clears queued telemetry identifiers and events on the device.



Examples include:



* Application version

* Connection success and failure events

* Conference duration

* Network quality indicators



No conference content, audio, video, messages, or participant identities are transmitted through telemetry systems.

Set `TELEMETRY_ENDPOINT` as a Gradle property or environment variable to enable
delivery. If the endpoint is empty, no telemetry leaves the device. The public
ingest endpoint is protected server-side by IP validation and request limits.


## Media end-to-end encryption

Symposium encrypts encoded Opus and video frames on the sending Android device
and authenticates/decrypts them only on receiving devices. The SFU relay forwards
the encrypted RTP payloads without receiving the conference key, so it cannot
listen to, view, transcode, or record the media content.

Each room invitation contains a random 32-byte secret in the URL fragment:
`#e2ee=...`. URL fragments are not part of HTTP requests, WebSocket signaling,
or relay logs. Android stores imported/generated room secrets in encrypted local
preferences backed by Android Keystore. Legacy invitations without a valid E2EE
fragment are rejected.

The current media suite is `frame-aes-gcm-v1`: WebRTC FrameCryptor with AES-GCM,
a key derived with PBKDF2-HMAC-SHA256 (100,000 iterations), and fail-closed frame
discarding until the encryptor/decryptor and key are ready. Audio and video both
use the same room secret. RTP headers and routing metadata remain outside the
room-key encryption so the SFU can route streams; each media hop still uses
DTLS-SRTP for transport protection.

This protects media content from the relay, hosting provider, and network
observers. It does not hide IP addresses or packet timing/sizes from network
observers, nor room membership and track metadata from the relay. Anyone who
obtains an invitation gets the room secret. This first version uses a shared room
key and does not yet provide MLS-style member-specific keys, forward secrecy, or
post-compromise security; closing and reopening a room creates a fresh secret.


## License



Licensed under the Apache License 2.0.



See the LICENSE file for details.
