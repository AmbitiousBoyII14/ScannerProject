# Scanner VPN — Android source and progress

**Current release: [1.8, signed APKs](https://github.com/AmbitiousBoyII14/ScannerProject/releases/tag/scanner-vpn-signed-44535731667)** · [1.8 source as built](https://github.com/AmbitiousBoyII14/ScannerProject/tree/fba880d02ddc031cbbc8120c7052a8c6c2850e49/scanner-vpn) · [Passing build and Android tests](https://github.com/AmbitiousBoyII14/ScannerProject/actions/runs/44534968663)

This directory (`scanner-vpn/`) is the Gradle project to open. The repository-root `app/` is a different application; `web-preview/` is a simulation. The Android app here uses a bundled WebView for controls and a native `VpnService` for traffic. Package: `com.scannervpn.android`; minimum Android 7.0 (API 24).

## Status as of 19 November 2026

**Verified:** On an Android 15 emulator, the installed client negotiated WSS with a controlled gateway, verified the SSH host key, authenticated to OpenSSH and started the VPN. A request from the emulator shell (a different UID from the app) reached a protected endpoint through the TUN that it could not reach before connection. Separate tests cover direct SSH, SSH over TLS, raw HTTP upgrade, UDP echo via `badvpn-udpgw`, and VLESS TCP/TLS and WS/TLS outbounds. These are tests against controlled servers, not a certification of a provider preset.

**Still open — provider connection:** The 1.8 phone trace shows DNS resolution, TCP connect and TLS 1.3 success, followed by `HTTP 101 without Sec-WebSocket-Accept`. That is not a valid RFC 6455 WebSocket handshake. The attempt stopped before SSH host-key discovery completed or a password was sent. The trace does **not** prove the server is down, that the Host/SNI difference caused the response, or that the bytes following the headers are raw SSH.

**Next diagnostic:** Confirm whether the provider expects binary WebSocket frames or an unframed SSH stream after its HTTP upgrade, and confirm the dial address, TLS name, HTTP Host and path from the same working profile. If it specifies raw SSH after upgrade, the existing control is **Payload → After HTTP upgrade → Raw SSH stream (no WebSocket framing)**. Keep SSL enabled if the gateway requires TLS. Turning SSL off changes encryption, not framing. Do not weaken certificate, WebSocket or SSH key checks to force a connection.

## Release milestones

| Version | What changed |
| --- | --- |
| 1.1–1.2 | Native IPv4 VPN with Xray profiles, HTTPS health checks, traffic counters, settings, per-CPU APKs and batch import for saved server domains. |
| 1.3–1.4 | Direct SSH TCP forwarding via JSch; one-line account entry; first-use host-key discovery, fingerprint confirmation and pinning. |
| 1.5 | SSH, Log and Batch tabs; verified TLS and WebSocket SSH gateways; optional append-only `Scanner/debug.txt`; stable release signing identity. |
| 1.6 | Separate editors for HTTP payload/framing, TLS/SNI, two IPv4 DNS resolvers and optional remote UDPgw. Added explicit account Copy, Paste and Hide controls. |
| 1.7 | More precise WebSocket refusal diagnostics, Log Copy/Clear, and a fix for obsolete DNS outbound configuration that prevented the native core starting after successful SSH login. |
| 1.8 | Preselected WSS route with editable Host/path. Opt-in **Remember SSH profile on this phone** stores an AES-GCM encrypted draft with an Android Keystore key; no login is bundled. |

## Where to work

| Area | Source |
| --- | --- |
| Home screen, payload editor, presets | [Bundled HTML, CSS and JS](app/src/main/assets/live/) |
| Android permissions, picker, host-key trust and UI bridge | [LiveActivity.kt](app/src/main/java/com/example/live/LiveActivity.kt) |
| VPN lifecycle, routing and health checks | [TunnelService.kt](app/src/main/java/com/example/live/TunnelService.kt) and [Profile.kt](app/src/main/java/com/example/live/Profile.kt) |
| Upgrade request, response validation and stream framing | [GatewayRequest.kt](app/src/main/java/com/example/live/GatewayRequest.kt), [GatewayDraft.kt](app/src/main/java/com/example/live/GatewayDraft.kt), [SshGateway.kt](app/src/main/java/com/example/live/SshGateway.kt) |
| SSH forwarding and remote UDP gateway | [SshTransport.kt](app/src/main/java/com/example/live/SshTransport.kt) and [UdpGateway.kt](app/src/main/java/com/example/live/UdpGateway.kt) |
| Tests | [JVM protocol tests](app/src/test/), [Android device tests](app/src/androidTest/), [fixture setup](scripts/test-and-build.sh) |

## Build and test

With JDK 17 and Android SDK 36.1 installed, run from the **repository root**:

```sh
cd scanner-vpn
./scripts/fetch-core.sh  # downloads the pinned AAR and checks its SHA-256
./gradlew :app:assembleDebug
```

Debug APKs are under `app/build/outputs/apk/debug/`, split by ABI. Most current phones use `arm64-v8a`. A locally debug-signed APK **cannot update** the published release in place. For a normal phone update, use the signed release above. CI verifies each release APK against the [public update certificate](signing/update-cert.pem); the private key is not part of the source.

For the full test suite, use the [build workflow](../.github/workflows/scanner-vpn-preview.yml). It runs `scripts/test-and-build.sh` on a **disposable Linux runner** (creates a temporary system user and loopback servers) and runs `--device` with an emulator. The test CA and local health endpoint are for the test build only. Release health checks use HTTPS.

## Operational limits

- **SSH** carries TCP. Resolver DNS is forwarded over TCP via SSH; general IPv4 UDP requires a compatible remote `badvpn-udpgw`. **VLESS/VMess, Trojan, Shadowsocks, SOCKS5 and HTTP CONNECT** use distinct Xray outbounds, not SSH login.
- The VPN installs an IPv4 default route and excludes its own UID to prevent loops. It is not an always-on kill switch. A successful host-key fetch or an HTTP 101 alone does not mark the VPN connected.
- **Batch** checks saved, configured endpoints independently of an active VPN. **Log → Clear** clears the in-memory session log, not batch results or `debug.txt`. File logging needs a user-selected folder; endpoints and timings may be present in the log.
- Enhanced, SlowDns, Psiphon and arbitrary multi-request payloads are not implemented. The SSH payload editor supports a single HTTP upgrade request; binary mode generates a fresh WebSocket key for each connection.

[Project license](LICENSE) · [Dependency notices](app/src/main/assets/licenses/) · [AndroidLibXrayLite v26.11.9](https://github.com/2dust/AndroidLibXrayLite/tree/v26.11.9) · [Earlier version-by-version notes](https://github.com/AmbitiousBoyII14/ScannerProject/blob/a0434bdeeede0149dc0fe9a1673a4d238d2ed74e/scanner-vpn/README.md)
