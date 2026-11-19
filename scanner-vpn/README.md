# Scanner VPN · Native client

The launcher is `com.example.live.LiveActivity`. Its bundled, network-disabled web UI uses the same visual design as the preview, but uses a native bridge instead of simulation code. `TunnelService` creates an Android TUN and passes its descriptor to AndroidLibXrayLite. IPv4 (including DNS) routes through the configured server. IPv6 is not enabled on the interface (Android blocks that family). The app UID is excluded to prevent the transport from routing into itself. This is not an always-on kill switch.

**Profiles:** VLESS over TCP/TLS or WebSocket/TLS, Trojan/TLS and Shadowsocks (chacha20-ietf-poly1305). TLS uses the actual server name; no certificate verification overrides. No SSH, PHP, or arbitrary payload transport is advertised. Use your own compatible endpoint. Select Configuration, enter the server settings privately, save, then connect and grant Android VPN permission. Up to 12 session-only profiles; no credentials written to preferences, reports, notifications or logs.

The scanner verifies saved profiles, not unknown targets. Health checks request `https://connectivitycheck.gstatic.com/generate_204` using the selected Xray outbound. Failure may mean the check destination is unreachable, not that a server is dead. ONLINE indicates successful HTTPS through the outbound, not independent confirmation of a specific exit IP. Rates are calculated from native outbound counters. The running tunnel repeats its check about once per minute. Stopping a batch allows the current bounded request to finish.

**Build:** JDK 17, Android SDK 36.1. Run `./scripts/fetch-core.sh` (verified SHA-256) and `./gradlew :app:assembleDebug`. The CI workflow publishes a debug-signed prerelease APK. If installing over a preview signed by a different CI debug key, Android may require uninstalling the old preview first.

Native dependency: [AndroidLibXrayLite v26.11.9](https://github.com/2dust/AndroidLibXrayLite/tree/v26.11.9), including [Xray-core](https://github.com/XTLS/Xray-core/tree/676eaf66f8f0). Corresponding source, build instructions and module revisions are in those repositories. License texts are included in the app assets. Original Dark Tunnel fork is Apache-licensed (see LICENSE); legacy preview components are not registered as Android services or activities.

### Android client 1.2

- Screenshots are permitted by default. Settings persists optional screenshot blocking, display wake lock, log following and rate visibility (never credentials).
- Scanner accepts pasted hostnames or a UTF-8 `.txt` document (up to 64 KiB) via Android's document picker. Unknown hosts must be configured first. A deduplicated list runs only matching saved profiles, using each profile's own credentials.
- Also supports VMess over WebSocket/TLS and authenticated SOCKS5 / HTTP CONNECT over TLS. The server must accept the selected TLS transport; HTTP CONNECT only carries TCP. SSH and PHP relay cards are unavailable, not simulated.
- CI ships individual standalone APKs per ABI. Most current Android phones use **arm64-v8a**. The app no longer packages the archived Compose preview (`legacy/`), and each APK contains only one compressed copy of the native engine. Compressed native libraries may use additional space after installation.

### 1.3: direct SSH

Choose **SSH** first, enter your server, username, exact password and its trusted OpenSSH public **host** key (`type base64 [comment]`). Obtain the key from your server administrator. The server must allow password authentication and TCP forwarding. Passwords are opaque text, including spaces and URL punctuation; editor drafts remain masked and in memory, not on disk. VLESS/VMess still require UUIDs.

The maintained [JSch](https://github.com/mwiede/jsch/tree/jsch-2.28.8) client verifies the host key before authenticating. Its session carries TCP via direct-tcpip channels. Xray uses a per-session authenticated loopback adapter; Android resolver DNS uses TCP through the same SSH session. Generic UDP and SSH-over-WebSocket/TLS are not implemented. See `scripts/test-and-build.sh` for the isolated OpenSSH forwarding test used before publishing.
