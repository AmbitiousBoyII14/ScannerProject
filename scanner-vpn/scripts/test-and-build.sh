#!/usr/bin/env bash
# Disposable loopback-only SSH endpoint for the CI integration test. Never uses a user's server.
set -euo pipefail
cd "$(dirname "$0")/.."
work=$(mktemp -d)
device=${1:-build}
# Temporary device fixtures live only in this disposable CI checkout, never in release sources.
phone_generated=()

account="svpn${RANDOM}test"
export SSH_TEST_USER="$account" SSH_TEST_PASSWORD="https://fixture.invalid/u?token=$(openssl rand -hex 16)&test=1"
cleanup(){ if [[ ${DEVICE_PROOF:-0} == 1 ]]; then sudo iptables -D OUTPUT -d 192.0.2.148 -p tcp --dport 22269 -m owner ! --uid-owner "$account" -j REJECT || true; sudo ip addr del 192.0.2.148/32 dev lo || true; fi; if ((${#phone_generated[@]})); then rm -f -- "${phone_generated[@]}"; fi; if [[ -n "${udpgw_pid:-}" ]]; then kill "$udpgw_pid" || true; fi; if [[ -n "${gateway_pid:-}" ]]; then kill "$gateway_pid" || true; fi; if [[ -f "$work/pid" ]]; then sudo kill "$(cat "$work/pid")" || true; fi; sudo userdel "$account" || true; sudo rm -rf "$work"; }
trap cleanup EXIT
sudo useradd -M -s /usr/sbin/nologin "$account"
printf '%s:%s\n' "$account" "$SSH_TEST_PASSWORD" | sudo chpasswd
ssh-keygen -q -t ecdsa -b 256 -f "$work/host" -N ''
ssh-keygen -q -t ecdsa -b 256 -f "$work/different_host" -N ''
export SSH_TEST_OTHER_KEY="$(cat "$work/different_host.pub")"
export SSH_TEST_KEY="$(cat "$work/host.pub")"
cat >"$work/sshd_config" <<CONFIG
ListenAddress 127.0.0.1
Port 22257
HostKey $work/host
PidFile $work/pid
PasswordAuthentication yes
KbdInteractiveAuthentication no
UsePAM no
PermitRootLogin no
AllowUsers $account
AllowTcpForwarding local
PermitOpen 127.0.0.1:* 192.0.2.148:22269
MaxSessions 0
CONFIG
sudo mkdir -p /run/sshd
sudo /usr/sbin/sshd -f "$work/sshd_config"
python3 -m venv "$work/wsenv"
"$work/wsenv/bin/pip" -q install websockets==14.2
openssl req -x509 -newkey rsa:2048 -nodes -keyout "$work/key.pem" -out "$work/cert.pem" -days 1 -subj '/CN=localhost' -addext 'subjectAltName=DNS:localhost,IP:127.0.0.1' 2>/dev/null
if [[ $device != --device ]]; then
mkdir "$work/badvpn"
curl -fsSL --retry 3 https://codeload.github.com/ambrop72/badvpn/tar.gz/07268f02706e78e282e19641b5d1d41e8e89bf31 | tar xz --strip-components=1 -C "$work/badvpn"
cmake -S "$work/badvpn" -B "$work/udpbuild" -DCMAKE_POLICY_VERSION_MINIMUM=3.5 -DBUILD_NOTHING_BY_DEFAULT=1 -DBUILD_UDPGW=1
cmake --build "$work/udpbuild" --target badvpn-udpgw --parallel 2
"$work/udpbuild/udpgw/badvpn-udpgw" --listen-addr 127.0.0.1:22266 & udpgw_pid=$!
fi
export XRAY_TEST_DIR="$work"
export GATEWAY_TEST_CERT="$work/cert.pem"
if [[ $device == --device ]]; then
  # The protected proof socket can only be reached by sshd's disposable user,
  # not by the emulator's ordinary NAT path. All rules die with this CI VM.
  sudo ip addr add 192.0.2.148/32 dev lo
  sudo iptables -I OUTPUT -d 192.0.2.148 -p tcp --dport 22269 -m owner ! --uid-owner "$account" -j REJECT
  export DEVICE_PROOF=1
fi
"$work/wsenv/bin/python" scripts/test-gateway.py "$work" & gateway_pid=$!
for i in {1..30}; do [[ -f "$work/ready" ]] && break; sleep .2; done
test -f "$work/ready"
if [[ $device == --device ]]; then
  assets=app/src/androidTest/assets
  dbg=app/src/debug
  mkdir -p "$assets" "$dbg/res/raw" "$dbg/res/xml" "$dbg/res/values"
  candidates=("$assets/ci_gateway.json" "$dbg/AndroidManifest.xml" "$dbg/res/raw/ci_gateway_ca.pem" "$dbg/res/xml/ci_gateway_trust.xml" "$dbg/res/values/ci_health.xml")
  for f in "${candidates[@]}"; do [[ ! -e "$f" ]] || { echo "Refusing to overwrite $f"; exit 1; }; done
  phone_generated=("${candidates[@]}")
  cp "$work/cert.pem" "$dbg/res/raw/ci_gateway_ca.pem"
  echo '<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application android:networkSecurityConfig="@xml/ci_gateway_trust"/></manifest>' > "$dbg/AndroidManifest.xml"
  echo '<network-security-config><domain-config><domain>localhost</domain><trust-anchors><certificates src="@raw/ci_gateway_ca"/></trust-anchors></domain-config></network-security-config>' > "$dbg/res/xml/ci_gateway_trust.xml"
  python3 - <<'PYFIX'
import json,os,pathlib
pathlib.Path('app/src/androidTest/assets/ci_gateway.json').write_text(json.dumps({k:os.environ[v] for k,v in [('user','SSH_TEST_USER'),('secret','SSH_TEST_PASSWORD'),('hostKey','SSH_TEST_KEY'),('otherKey','SSH_TEST_OTHER_KEY')]}))
PYFIX
  echo '<resources><string name="vpn_health_url" translatable="false">http://127.0.0.1:22268/proof</string></resources>' > "$dbg/res/values/ci_health.xml"
  # Shell UID probe with bounded runtime; close stdin without blocking instrumentation's stdout reader.
  cat > "$work/scanner-proof.sh" <<'PROBE'
{ printf 'GET /proof HTTP/1.0\r\nHost: fixture\r\n\r\n'; sleep 1; } | toybox timeout 8 toybox nc -w 4 192.0.2.148 22269
PROBE
  adb push "$work/scanner-proof.sh" /data/local/tmp/scanner-proof.sh
  adb reverse tcp:22267 tcp:22267
  ./gradlew :app:connectedDebugAndroidTest --no-daemon --console=plain
  python3 - <<'PYCHECK'
import glob,xml.etree.ElementTree as ET
cases=[t for f in glob.glob('app/build/outputs/androidTest-results/**/*.xml',recursive=True) for t in ET.parse(f).iter('testcase')]
for name in ('rejectedRouteThenAuthenticatedForward','pasteConfigureAndObserveGatewayRefusal'):
    assert any(t.get('name')==name and all(t.find(tag) is None for tag in ['failure','error','skipped']) for t in cases), name+' did not pass'
PYCHECK
  exit 0
fi
if ! ./gradlew :app:testDebugUnitTest :app:assembleRelease --no-daemon --console=plain 2>&1 | tee "$work/build.log"; then
  grep -E '(Could not (GET|HEAD|download|resolve)|status code|timed out|Temporary failure)' "$work/build.log" | head -8 | while IFS= read -r line; do echo "::error::$line"; done || true
  # Surface only build/test diagnostics (never environment variables or passwords).
  grep -E '(^(e: |ERROR: |\* What went wrong:|> |Execution failed)| FAILED$|Exception at )' "$work/build.log" | tail -25 | while IFS= read -r line; do echo "::error::$line"; done
  python3 - <<'PYTEST'
import glob, xml.etree.ElementTree as ET
for name in glob.glob('app/build/test-results/testDebugUnitTest/TEST-*.xml'):
    for failure in ET.parse(name).iter('failure'):
        print('::error::' + failure.attrib.get('message','test failed').splitlines()[0])
PYTEST
  exit 1
fi

# Same upstream revision as the Android engine; only run against local fixtures.
curl -fsSL --retry 3 https://github.com/XTLS/Xray-core/releases/download/v26.11.9/Xray-linux-64.zip -o "$work/xray.zip"
echo "b651f7e1d9966ee2c1b1ee1bf8b52c72771824d3065dce96bbc8bcc19ed95f82  $work/xray.zip" | sha256sum -c -
unzip -q "$work/xray.zip" xray -d "$work"; chmod +x "$work/xray"
if ! python3 scripts/test-vless.py "$work" > "$work/vless.log" 2>&1; then
  grep -Ei "fail|error|block|invalid|rejected|certificate|Verified VLESS" "$work/vless.log" | tail -20 | while IFS= read -r line; do echo "::error::$line"; done
  exit 1
fi
cat "$work/vless.log"
