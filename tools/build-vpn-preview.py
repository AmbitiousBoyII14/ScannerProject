#!/usr/bin/env python3
"""Generate both browser entries from the Android WebView assets. No native app changes."""
from pathlib import Path
import base64, hashlib, sys
root=Path(__file__).resolve().parents[1]
live=root/'scanner-vpn/app/src/main/assets/live'; web=root/'web-preview'
html=(live/'index.html').read_text().replace('<title>Scanner VPN</title>','<title>Scanner VPN · browser preview</title>').replace('<script defer src="core-editor.js">','<script defer src="app.js"></script><script defer src="core-editor.js">').replace('</nav><main>','</nav><main><aside class="preview-banner"><strong>1.14 UI preview · simulation only</strong><p id="previewMessage" role="status">No network traffic. Use example credentials only.</p><button id="previewExample">Load example SSH account</button></aside>')
css=(live/'style.css').read_text()+'\n.preview-banner{border-left:3px solid var(--accent);padding:8px 12px;background:var(--tint);margin-bottom:14px;font-size:13px}.preview-banner p{margin:4px 0}.preview-banner button{color:var(--accent);background:var(--surface);border:1px solid var(--line);padding:6px 9px;border-radius:4px}\n'
scripts={'app.js':(web/'app.js').read_text(),**{n:(live/n).read_text() for n in ('core-editor.js','ui.js')}}
outputs={web/'index.html':html,web/'style.css':css,**{web/n:s for n,s in scripts.items() if n!='app.js'}}
def digest(text):return "'sha256-"+base64.b64encode(hashlib.sha256(text.encode()).digest()).decode()+"'"
standalone=html.replace("script-src 'self'",'script-src '+' '.join(map(digest,scripts.values()))).replace("style-src 'self'",'style-src '+digest(css)).replace('<link rel="stylesheet" href="style.css">','<style>'+css+'</style>')
for name in scripts:standalone=standalone.replace(f'<script defer src="{name}"></script>','')
outputs[root/'Scanner-VPN-Demo.html']=standalone.replace('</body>',''.join('<script>'+s+'</script>' for s in scripts.values())+'</body>')
for dest,text in outputs.items():
    if '--check' in sys.argv:
        if not dest.exists() or dest.read_text()!=text:sys.exit(f'Stale preview: {dest.name}')
    else:dest.write_text(text)
