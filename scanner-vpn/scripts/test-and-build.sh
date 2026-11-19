#!/usr/bin/env bash
# Disposable loopback-only SSH endpoint for the CI integration test. Never uses a user's server.
set -euo pipefail
cd "$(dirname "$0")/.."
work=$(mktemp -d)
account="svpn${RANDOM}test"
export SSH_TEST_USER="$account" SSH_TEST_PASSWORD="https://fixture.invalid/u?token=$(openssl rand -hex 16)&test=1"
cleanup(){ if [[ -f "$work/pid" ]]; then sudo kill "$(cat "$work/pid")" || true; fi; sudo userdel "$account" || true; sudo rm -rf "$work"; }
trap cleanup EXIT
sudo useradd -M -s /usr/sbin/nologin "$account"
printf '%s:%s\n' "$account" "$SSH_TEST_PASSWORD" | sudo chpasswd
ssh-keygen -q -t ecdsa -b 256 -f "$work/host" -N ''
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
PermitOpen 127.0.0.1:*
MaxSessions 0
CONFIG
sudo mkdir -p /run/sshd
sudo /usr/sbin/sshd -f "$work/sshd_config"
if ! ./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain 2>&1 | tee "$work/build.log"; then
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
