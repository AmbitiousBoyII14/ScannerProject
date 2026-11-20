#!/usr/bin/env bash
# Package the exact 1.17 Android project; never copy the working directory or signing keys.
set -euo pipefail
src=HEAD
output="$(pwd)/Scanner-VPN-1.17-Android-project.zip"
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
git archive "$src" scanner-vpn .github/workflows/scanner-vpn-{preview,sign}.yml | tar -x -C "$tmp"
# Preview is generated from the same shipped UI; fail rather than package a stale demo.
git diff --exit-code "$src" HEAD -- scanner-vpn/app/src/main/assets/live
python3 tools/build-vpn-preview.py --check
git archive HEAD design-preview web-preview Scanner-VPN-Batch-Preview.html Scanner-VPN-Demo.html tools/build-vpn-preview.py | tar -x -C "$tmp"
project="$tmp/scanner-vpn"
"$project/scripts/fetch-core.sh"
echo 'cdfef146218233a31a8b52c8b1016557730d3b63fc121d4c84cacea50e669155  app/libs/libv2ray.aar' | (cd "$project" && sha256sum -c -)
fetch_source(){
  local repo="$1" rev="$2" dest="$project/upstream-src/${1##*/}"
  mkdir -p "$dest"
  curl --fail --location --retry 3 --silent --show-error "https://codeload.github.com/$repo/tar.gz/$rev" | tar -xz --strip-components=1 -C "$dest"
  printf '%s %s\n' "$repo" "$rev" >> "$project/upstream-src/REVISIONS.txt"
}
fetch_source 2dust/AndroidLibXrayLite b5a377087e74eaa34e86718710b04c50f4b767f5
fetch_source XTLS/Xray-core 676eaf66f8f05d4d558ee7a74b4736623d3feeb9
fetch_source mwiede/jsch 36c4d26a58ee06b100a9f0f3509320168be4c43a
fetch_source TooTallNate/Java-WebSocket da3cf2a777aed862f2f5b5cf060cae7969958667
grep -Fq '676eaf66f8f0' "$project/upstream-src/AndroidLibXrayLite/go.mod"
cat > "$tmp/START-HERE.txt" <<'DOC'
Scanner VPN 1.17 — Android Studio project + engine handoff
Open scanner-vpn/ (the directory with settings.gradle.kts).

app/src/main/java/com/example/live/TunnelService.kt implements Android VpnService.
Profile.kt builds Xray configs. SshGateway.kt and SshTransport.kt implement SSH transport.
app/src/main/assets/live/ is the shipped WebView UI. It calls the native Android bridge.
app/libs/libv2ray.aar is the SHA-256-verified native dependency used by 1.17.
upstream-src/ contains the pinned native engine, Android bindings, SSH and WebSocket
library source trees, with their licenses. These are reference sources, not additional
Gradle modules. No private signing key or user account data is included.

Open Scanner-VPN-Demo.html to preview the 1.17 UI offline (simulated only).
web-preview/ contains the multi-file browser preview. See web-preview/README.md.

Build: see scanner-vpn/README.md. Build tools, Gradle/Maven dependencies, and Go
transitive dependencies are not vendored. This isn't an offline Android SDK.
DOC
(cd "$tmp" && zip -q -r "$tmp/bundle.zip" START-HERE.txt scanner-vpn .github design-preview web-preview Scanner-VPN-Batch-Preview.html Scanner-VPN-Demo.html tools)
unzip -tq "$tmp/bundle.zip" >/dev/null
mv "$tmp/bundle.zip" "$output"
