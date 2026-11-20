#!/usr/bin/env python3
"""Generate both browser entries from the Android WebView assets. No native app changes."""
from pathlib import Path
import base64, hashlib, sys
root=Path(__file__).resolve().parents[1]
live=root/'scanner-vpn/app/src/main/assets/live'; web=root/'web-preview'
html=(live/'index.html').read_text().replace('<script src="core-editor.js">','<script src="app.js"></script><script src="core-editor.js">').replace('ANDROID 1.19','1.19 BROWSER SIMULATION').replace('</small><div class="scrim"','</small><p id="previewMessage" role="status"></p><div class="scrim"')
css=(live/'style.css').read_text()+'\n#previewMessage{margin:0;text-align:center;font-size:12px;color:var(--muted)}\n'
scripts={'app.js':(web/'app.js').read_text(),**{n:(live/n).read_text() for n in ('core-editor.js','ui.js')}}
outputs={web/'index.html':html,web/'style.css':css,**{web/n:s for n,s in scripts.items() if n!='app.js'}}
def digest(text):return "'sha256-"+base64.b64encode(hashlib.sha256(text.encode()).digest()).decode()+"'"
standalone=html.replace("script-src 'self'",'script-src '+' '.join(map(digest,scripts.values()))).replace("style-src 'self'",'style-src '+digest(css)).replace('<link rel="stylesheet" href="style.css">','<style>'+css+'</style>')
for name in scripts:standalone=standalone.replace(f'<script src="{name}"></script>','')
outputs[root/'Scanner-VPN-Demo.html']=standalone.replace('</body>',''.join('<script>'+s+'</script>' for s in scripts.values())+'</body>')
for folder in (root,web):outputs[folder/'Scanner-VPN-Batch-Preview.html']=(live.parent/'batch-lab.html').read_text()
for dest,text in outputs.items():
    if '--check' in sys.argv:
        if not dest.exists() or dest.read_text()!=text:sys.exit(f'Stale preview: {dest.name}')
    else:dest.write_text(text)
