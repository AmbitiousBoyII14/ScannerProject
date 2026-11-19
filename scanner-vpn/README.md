# Scanner VPN — Android source and progress

**Historical release: [1.8, signed APKs](https://github.com/AmbitiousBoyII14/ScannerProject/releases/tag/scanner-vpn-signed-44535731667)** · [1.8 source as built](https://github.com/AmbitiousBoyII14/ScannerProject/tree/fba880d02ddc031cbbc8120c7052a8c6c2850e49/scanner-vpn) · [Passing build and Android tests](https://github.com/AmbitiousBoyII14/ScannerProject/actions/runs/44534968663)

This directory (`scanner-vpn/`) is the Gradle project to open. The repository-root `app/` is a different application; `web-preview/` is a simulation. The Android app here uses a bundled WebView for controls and a native `VpnService` for traffic. Package: `com.scannervpn.android`; minimum Android 7.0 (API 24).

## 1.15 · Custom layout (Android)

The dark SSH / Log / Batch interface reuses the approved standalone preview's markup and stylesheet. Its data comes from the Android service, not the browser simulator. Choose **puzzle → V2ray Settings** to import a share link, **drawer → SSH Settings** to enter an SSH account, and **Payload → After upgrade** for raw SSH compatibility. VPN Settings retains the background shortcut, display and debug-file controls. Batch uses each matching saved profile's own credentials, or a credential-free TLS check on other targets. Local configs and logs can be opened/saved using the Android document picker. Config files include secrets: keep them private. Enhanced, SlowDns, Psiphon and utilities without an installed backend remain visibly listed but cannot enable a fake tunnel. Insecure TLS is not supported by the bundled Xray version.

## 1.14 · Background VPN & optional shortcut

After **Connected**, press Home, switch apps, or use **Settings & debug file → Continue in background**. The user-started VPN foreground service owns the tunnel, independent of the activity. Enable its ongoing notification from the same settings to return or disconnect. **Floating VPN icon over other apps** is off by default and requests Android's display-over-other-apps permission only on opt-in. It appears only outside the app during a verified connection. Tap to return or drag to reposition. Disconnect, failure or revocation of the VPN removes it. Denying overlay permission does not stop the VPN. This is not a boot launcher or a way to survive force-stop, OS termination, loss of network or OEM power restrictions.

## 1.13 · V2ray settings

Select **V2ray → Edit profile → Paste / import link**. VLESS, VMess (base64 JSON) and Trojan support separate TCP server address, WebSocket Host/path and TLS SNI. The form supports TCP/WS, Mux and verified TLS (mandatory for Trojan). SIP002 Shadowsocks import is limited to chacha20-ietf-poly1305 without plugins. REALITY, Vision/flow, gRPC, XHTTP and unhandled link extensions are not silently converted. The included Xray v26.11.9 removed `allowInsecure`; importing such a link requires explicit approval to use verified TLS. A certificate SHA-256 pin may be set manually. Use **〈 〉 JSON** to inspect/copy the config made by the native profile builder; unlike a generic outbound snippet, it includes Android routing and the configured connection policy. JSON contains the login/UUID. No example account is bundled or tested. The editor and parser are in [core-editor.js](app/src/main/assets/live/core-editor.js).

## 1.12 · UI handoff

Open **`scanner-vpn/`** as the Gradle project. The working Android UI is in [`app/src/main/assets/live/`](app/src/main/assets/live/) (`index.html`, `style.css`, `ui.js`), **not** the old web demo. `ui.js` reads `Client.snapshot()` from [LiveActivity](app/src/main/java/com/example/live/LiveActivity.kt). [LiveState](app/src/main/java/com/example/live/LiveState.kt) supplies `log` and `checks` as timestamp, raw stage tag and message separated by two spaces; `results` contains per-endpoint summaries. Keep full values, including errors, visible and selectable; don't reconstruct handshake details or log account input. Session Copy and the optional SAF debug file use the native records.

The full live trace is expanded on both tabs, with 500 recent events per stream. CDN lines identify the **actual DNS address used** and the matching CIDR; batch cards retain that information on success or failure after DNS. [CdnRanges](app/src/main/java/com/example/live/CdnRanges.kt) uses a bundled [IPv4/IPv6 snapshot with provenance](app/src/main/resources/cdn-ranges.tsv), without an extra network lookup. It covers Cloudflare, Amazon CloudFront, Fastly, CDNetworks and ArvanCloud. A public address outside these prefixes is **No known CDN range**, not proof of direct origin. A match identifies a provider range, not what service a particular site is running. The source ZIP is built from tracked project files only; download the pinned engine with `scripts/fetch-core.sh` before building.

**1.11 (code 12):** The phone trace in 1.10 reached `ONLINE` twice against the provider (1421 ms and 596 ms HTTPS checks). Batch now accepts pasted/imported hostnames or `host:port` with no saved profiles required. An exact saved hostname, mode and matching port (if supplied) runs its own tunnel check. Other endpoints get a certificate-verified TLS reachability check only; credentials and custom HTTP payloads are never copied across hosts. Results are per endpoint, separate from the event timeline. VPN and Batch share **Timeout & auto-retry**: 2–60 seconds for DNS and transport setup, 1–5 optional additional attempts on transient network errors. This is not a whole-session deadline, an always-on reconnect service, or a change to the native HTTPS health check's internal timeout.

**1.10 (code 11):** SSH verifies the host key inside the same key exchange that precedes password login. On first use, compare the displayed SHA-256 fingerprint with the provider. A changed pin requires explicit approval of the old/new fingerprint dialog; Cancel, timeout, or leaving the activity fails closed. Pins are shared by normalized account hostname and port (including batch), never by resolved IP. Matching 1.9 route pins migrate; conflicting legacy keys require review. To remove just this endpoint’s trust, use **SSH settings → Reset / Forget Pinned Host Key**. Clearing app data is not necessary.

**1.9 (code 10):** On the VPN screen, enable **Use Payload → Raw SSH after upgrade** to allow either framing on one connection. The client sends a fresh WebSocket handshake. A valid accept uses binary frames; HTTP/1.1 101 without an accept header selects raw SSH only with this option enabled. A present but invalid accept or any non-101 is still rejected. SSL and SSH key verification are unchanged. The log reports which transport was selected; selecting raw is not proof of SSH login.

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
