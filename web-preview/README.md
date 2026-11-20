# Scanner VPN 1.18 browser simulation

`Scanner-VPN-Demo.html` is the offline, single-file browser build. `index.html` in this directory is its multi-file equivalent for HTTP hosting. Both use the shipped Android UI from `scanner-vpn/app/src/main/assets/live/` with a **browser-only simulated bridge**, not a VPN. Do not use real credentials. The approved original design reference is `design-preview/Scanner-VPN-Preview.html`.

Regenerate: `python3 tools/build-vpn-preview.py`. Check: `python3 tools/build-vpn-preview.py --check`.
