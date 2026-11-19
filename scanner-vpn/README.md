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

### 1.4: SSH first use and quick account

Paste `host:port@username:password` on the VPN tab for direct SSH. The password is masked; additional `:` and `@` in it are literal. Leaving the field blank uses the selected saved profile (including non-SSH profiles). The Log sub-tab and batch scanner remain separate. The first SSH save fetches the host public key with strict host checking and no user credentials, then asks for confirmation of its fingerprint. Compare it with the provider's fingerprint via a trusted channel. Only confirmed public keys are persisted, scoped to hostname and port. After that, real SSH sessions enforce the stored key. A key change is not trusted automatically. Profile credentials remain session-only. This is not arbitrary HTTP Custom payload, certificate-bypass or cross-host routing compatibility.

### 1.5: Android connection screen, storage and signed updates

Use the white/cyan SSH / Log / Batch tabs. On SSH, **SSL** adds verified TLS and **Use Payload** selects a standard binary WebSocket gateway (path in Edit profile). They may be combined if the provider supports WSS-to-SSH. Neither rewrites arbitrary HTTP payloads, spoofs a different hostname, or makes an ordinary website into a VPN. V2ray selects a separate core profile, with its own credentials and transport. The log distinguishes configured core options from genuinely negotiated SSH/TLS events. No fake HTTP response, cipher or connection result. Enhanced, SlowDns, UDP Custom and Psiphon have no native engine here.

At launch, grant the Android folder picker access to **Scanner** in internal storage (create it if needed), or choose **Documents** for `Documents/Scanner/debug.txt`. The writer appends each session/batch event; prior sessions aren't overwritten. Cancelling doesn't block VPN use. Settings can change or regrant the folder. This uses persisted Storage Access Framework access, not all-files or legacy write permissions. Logs contain endpoint addresses and timing; they do not include profile secrets. Delete debug.txt when no longer needed.

Release builds use the checked-in **public** update certificate `signing/update-cert.pem`, not a CI debug key. The build runner emits four v2 signing requests after tests; the operator checks the run/commit and signs the requests offline with `scripts/apk-envelope.py approve <run-id> < requests.json`. Publishing attaches only those signatures to that run's exact artifacts. Android SDK apksigner verifies every APK and the certificate digest before upload. Keep an offline backup of the private key (workspace `scanner-vpn/.private/update-key.pem`); never commit it. Loss of the key prevents signing future compatible updates. The Android package ID stays `com.scannervpn.android`; switching from the old disposable debug certificate may require one uninstall.

CI runs the app's JSch discovery/authentication against disposable direct, TLS, WS, and WSS SSH gateways and exercises direct-tcpip forwarding. It separately takes the app-generated VLESS TCP/TLS and WS/TLS outbounds into the matching desktop Xray release and transfers a random proof via a loopback server. This tests protocol behavior, not a physical phone's Android TUN, folder picker, or any particular provider.

### 1.6: provider connection fields

Paste the whole SSH account into the visible account line; **Copy / Paste / Hide** operate on that field without rewriting the password. The keyboard's usual select/paste also works. No clipboard service runs in the background. Only the explicit buttons access it.

- **Payload** accepts one HTTP/1.1 GET upgrade request, with actual line breaks, `[crlf]` or literal `\r\n`. Choose framed RFC 6455 WebSocket or an HTTP 101 followed by an unframed SSH stream. Host, Connection, Upgrade, User-Agent, Origin and X-headers are supported. No request splitting, proxy chaining, ambiguous framing or certificate bypass. The editor rejects unsupported syntax instead of silently discarding it. A normal HTTP 200 page is not a tunnel.
- **SSL / SNI** specifies the certificate name independently of the account's dial address. Blank means the account host. HTTP Host is taken from the payload. For VLESS, TLS options are in its own profile editor.
- **DNS IPs** installs the preferred and optional second IPv4 resolver on Android. SSH resolver requests still travel as TCP via SSH; this is not the bootstrap resolver for the SSH endpoint.
- **UDPgw settings** takes the provider's badvpn-udpgw IPv4:port on the remote side of SSH. With it enabled, the local authenticated SOCKS adapter can relay IPv4 UDP via direct-tcpip to that gateway. Generic UDP is otherwise off for SSH. A TCP forwarding channel opening is logged as such, not as proof of UDP delivery.

The APK build tests raw HTTP-upgrade-over-TLS, WSS, direct SSH/TLS, VLESS, and a UDP echo round trip through a disposable OpenSSH server and upstream badvpn-udpgw. The Android instrumentation test drives the installed WebView, uses the real clipboard, edits the payload/SNI/DNS/UDPgw, and checks a refused upgrade without sending credentials. Tests never use a provider account.

### 1.7: WebSocket upgrade diagnostics and session log tools

Log → **Copy** copies the current session lines only. **Clear** asks for confirmation and clears the in-memory session log, not the VPN connection, batch results or on-disk `debug.txt`. Binary WebSocket now reports observed HTTP refusal codes, malformed 101 upgrades, or missing responses separately. For providers that explicitly require it, the payload editor accepts `Sec-WebSocket-Protocol: binary`; leaving it out preserves ordinary RFC 6455 behavior. No automatic protocol downgrade, auth retries or path guessing.

`./scripts/test-and-build.sh --device` (disposable Linux CI runner with a running emulator) provisions a loopback WSS gateway with a route and mandatory binary subprotocol. The installed Android test reproduces 404 and 426 responses, discovers and verifies the SSH host key, logs in to OpenSSH and checks an HTTP 204 via the native Xray outbound and SSH forwarding. No provider credentials are used. The ephemeral test CA is scoped to localhost in the instrumentation build only; nothing is added to the released trust store. This outbound test does not assert device-wide routing.
