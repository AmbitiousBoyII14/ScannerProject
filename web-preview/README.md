# Scanner VPN 1.14 browser preview
Open `../Scanner-VPN-Demo.html` directly for the standalone, offline demo. Or serve this directory with a static HTTP server and open `index.html`. Select **Load example SSH account → Connect → Log**. Batch accepts domains; `.invalid` demonstrates a failure. All network results are simulated. Browser-only settings and example accounts are in memory, not stored on disk. Native VPN, overlay, notification, file logging and real Xray config generation require Android.

To redesign the shipped app, edit `scanner-vpn/app/src/main/assets/live/`, not the generated copies here. `app.js` is the preview-only Android bridge substitute. Run `python3 tools/build-vpn-preview.py` from the extracted project or repository to refresh both browser entries.
