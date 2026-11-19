'use strict';
// Import only outbound fields the editor and bundled Xray can represent. Never fetch a link.
function decodeShare64(text){
 if(!/^[A-Za-z0-9+/_-]+={0,2}$/.test(text))throw Error('Invalid base64 share link.');
 return new TextDecoder('utf-8',{fatal:true}).decode(Uint8Array.from(atob(text.replace(/-/g,'+').replace(/_/g,'/')),c=>c.charCodeAt(0)))
}
function readCoreLink(input){
 const bad=msg=>{throw Error(msg)},requireValue=(ok,msg)=>{if(!ok)bad(msg)};
 const src=input.trim().replace(/&amp;/gi,'&').replace(/\[([\w.-]+)\]\(https?:\/\/\1\/?\)/gi,'$1');
 requireValue(src.length>0&&src.length<=16384,'Paste one share link.');
 const p={remark:'',host:'',secret:'',port:0,sni:'',wsHost:'',path:'/',mux:false,certPin:''};let proto,net,tls,insecure=false;
 const truth=v=>{requireValue([undefined,null,'',false,true,0,1,'0','1','true','false'].includes(v),'Unsupported boolean value.');return [true,1,'1','true'].includes(v)};
 if(/^vmess:\/\//i.test(src)){
  let j;try{j=JSON.parse(decodeShare64(src.slice(8)))}catch{bad('Expected a base64-encoded VMess JSON share link.')}
  requireValue(j&&typeof j==='object'&&Object.keys(j).every(k=>['v','ps','add','port','id','aid','scy','net','type','host','path','tls','sni','allowInsecure','alpn','fp','mux'].includes(k)),'Unsupported VMess extension.');
  requireValue(+ (j.aid||0)===0&&['','auto',undefined].includes(j.scy)&&['none','',undefined].includes(j.type)&&!j.alpn&&!j.fp,'Only VMess AEAD / auto without transport extensions is supported.');
  requireValue(['tls','none','',undefined].includes(j.tls),'Unsupported VMess security.');proto='VMess';net=j.net||'tcp';tls=j.tls==='tls';insecure=truth(j.allowInsecure);
  Object.assign(p,{host:j.add,port:+j.port,secret:j.id,wsHost:j.host||'',path:j.path||'/',sni:j.sni||'',remark:j.ps||'',mux:truth(j.mux)});
 }else{
  // Older Android WebViews treat non-HTTP schemes as opaque and leave URL.hostname empty.
  const link=/^(vless|trojan|ss):\/\/([^/?#]+)([/?#].*)?$/i.exec(src);requireValue(link&&/@[^@:]+:\d+$/.test(link[2]),'Expected a supported share link with an explicit server port.');
  let u;try{u=new URL('https://'+link[2]+(link[3]||''))}catch{bad('Invalid share link.')}
  proto=({vless:'VLESS',trojan:'Trojan',ss:'Shadowsocks'})[link[1].toLowerCase()];requireValue(proto,'Use a VLESS, VMess, Trojan or SIP002 Shadowsocks share link.');
  requireValue(!u.password&&u.username&&u.hostname&&['','/'].includes(u.pathname),'Expected credential@server:port.');
  const q=u.searchParams,allowed=proto==='Shadowsocks'?[]:['type','security','encryption','flow','host','path','sni','allowInsecure','mux','pcs','headerType'];
  for(const k of q.keys())requireValue(allowed.includes(k)&&q.getAll(k).length===1,'Unsupported or repeated share-link option.');
  requireValue(!q.get('flow')&&(!q.get('encryption')||q.get('encryption')==='none')&&(!q.get('headerType')||q.get('headerType')==='none'),'This flow, encryption or header type is not supported.');
  const security=q.get('security')||(proto==='Trojan'?'tls':'none');requireValue(['none','tls'].includes(security),'This transport security is not supported.');
  net=q.get('type')||'tcp';tls=security==='tls';insecure=truth(q.get('allowInsecure'));
  Object.assign(p,{host:u.hostname,port:Number(u.port||443),secret:decodeURIComponent(u.username),wsHost:q.get('host')||'',sni:q.get('sni')||'',path:q.get('path')||'/',remark:decodeURIComponent(u.hash.slice(1)),certPin:q.get('pcs')||'',mux:truth(q.get('mux'))});
  if(proto==='Shadowsocks'){const method='chacha20-ietf-poly1305:',auth=decodeShare64(p.secret);requireValue(auth.startsWith(method),'Only chacha20-ietf-poly1305 SIP002 links without plugins are supported.');p.secret=auth.slice(method.length)}
 }
 requireValue(['tcp','raw','ws'].includes(net),'Only TCP and WebSocket transports are supported.');
 requireValue(tls||(!insecure&&!p.sni&&!p.certPin),'TLS fields require TLS.');requireValue(net==='ws'||(!p.wsHost&&p.path==='/'),'WebSocket fields require WebSocket transport.');
 p.mode=proto+(net==='ws'?' WS':'')+(tls?' TLS':'');requireValue(modes.includes(p.mode),'Unsupported protocol / transport combination.');return {p,insecure}
}
function receiveCoreLink(value){const area=document.getElementById('coreShare');if(area&&$('sheet').open){area.value=value;area.focus()}}
function importCore(connect){
 const root=dialog('Import V2ray link');const area=document.createElement('textarea');area.id='coreShare';area.rows=4;area.maxLength=16384;area.placeholder='vless://, vmess://, trojan:// or ss://';area.spellcheck=false;root.append(area);btn(root,'Paste from clipboard',()=>Client.pasteCoreLink()).className='dismiss';
 const msg=note(root,'Imported locally. Review the settings before saving.');const consent=document.createElement('label'),agree=document.createElement('input');agree.type='checkbox';consent.hidden=true;consent.append(agree,' Import using verified TLS instead');root.append(consent);area.oninput=()=>{agree.checked=false;consent.hidden=true};
 btn(root,'Review settings',()=>{let parsed;try{parsed=readCoreLink(area.value)}catch(e){msg.textContent=e instanceof URIError?'Invalid encoding in link.':e.message;return}
  if(parsed.insecure&&!agree.checked){msg.textContent='This link requests Insecure TLS. This Xray version removed allowInsecure. Use verified TLS, or obtain a certificate pin from your provider.';consent.hidden=false;return}
  const p={...parsed.p,dns:custom?dns:'1.1.1.1',dns2:custom?dns2:''};if(!Client.previewCore(JSON.stringify(p))){msg.textContent='Invalid address, port, UUID, path or certificate pin.';return}out=p.mode;drafts.set(out,p);paint();editCore(connect)
 }).className='apply'
}
function editCore(connect){
 const root=dialog('V2ray settings');$('sheet').classList.add('coreEditor');const d=drafts.get(out)||{},proto=out.split(' ')[0],family=['VLESS','VMess','Trojan'].includes(proto),ws=out.includes(' WS'),tls=out.endsWith('TLS');
 const heading=text=>{const h=document.createElement('h3');h.textContent=text;root.append(h)};
 const select=(title,values,value)=>{const l=document.createElement('label'),s=document.createElement('select');l.className='field';l.textContent=title;values.forEach(v=>s.add(new Option(v,v)));s.value=value;l.append(s);root.append(l);return s};
 const toggle=(title,value,disabled=false)=>{const l=document.createElement('label'),x=document.createElement('input');x.type='checkbox';x.checked=value;x.disabled=disabled;l.className='pref';l.append(x,' '+title);root.append(l);return x};
 const tools=document.createElement('div');tools.className='tools';root.append(tools);btn(tools,'Paste / import link',()=>importCore(connect));const json=btn(tools,'〈 〉 JSON',()=>showJson());
 const protocol=select('Protocol',['VLESS','VMess','Trojan','Shadowsocks','HTTP','SOCKS5'],proto);heading(proto+' settings');
 const name=field(root,'Profile name',d.remark||''),host=field(root,'Server address (IP or domain)',d.host||''),port=field(root,'Server port',d.port??443,'number');
 const user=/HTTP|SOCKS5/.test(proto)?field(root,'Proxy username',d.user||''):null;
 const secret=field(root,['VLESS','VMess'].includes(proto)?'Client UUID':proto==='Shadowsocks'?'Password · chacha20-ietf-poly1305':'Password',d.secret||'','password');const mux=family?toggle('Mux',!!d.mux):null;
 heading(ws?'WS settings':'Transport settings');const net=family?select('Network',['TCP','WebSocket'],ws?'WebSocket':'TCP'):null;
 const secure=proto!=='Shadowsocks'?toggle('TLS',tls,!['VLESS','VMess'].includes(proto)):null;
 const wh=ws?field(root,'Host (WebSocket request header)',d.wsHost||''):null,wp=ws?field(root,'Path',d.path||'/'):null;
 const sn=tls?field(root,'SNI (TLS server name)',d.sni||''):null,pin=tls?field(root,'Certificate SHA-256 pin (optional)',d.certPin||''):null;
 if(tls)note(root,'Insecure: off. This core verifies TLS; a provider-supplied certificate pin is also supported.');
 const msg=note(root,'');function values(){return {mode:out,remark:name.value.trim(),host:host.value.trim(),port:Number(port.value),user:user?.value||'',secret:secret.value,mux:!!mux?.checked,wsHost:wh?.value.trim()||'',path:wp?.value||'/',sni:sn?.value.trim()||'',certPin:pin?.value.trim()||'',dns:custom?dns:'1.1.1.1',dns2:custom?dns2:''}}
 function change(next,carry){const p=values();drafts.set(out,p);if(carry)drafts.set(next,{...p,mode:next});out=next;paint();editCore(connect)}
 protocol.onchange=()=>change(({VLESS:'VLESS WS TLS',VMess:'VMess WS TLS',Trojan:'Trojan TLS',HTTP:'HTTP CONNECT TLS',SOCKS5:'SOCKS5 TLS',Shadowsocks:'Shadowsocks'})[protocol.value],false);
 function transport(){change(proto+(net.value==='WebSocket'?' WS':'')+(secure.checked?' TLS':''),true)}if(net)net.onchange=transport;if(secure&&!secure.disabled)secure.onchange=transport;
 function showJson(){const p=values(),txt=Client.previewCore(JSON.stringify(p));if(!txt){msg.textContent='Complete the server and credential fields to view JSON.';return}drafts.set(out,p);const pane=dialog('Xray JSON');$('sheet').classList.add('coreEditor');note(pane,'Contains your credential. Server names are resolved at connect time.');const pre=document.createElement('pre');pre.textContent=txt;pane.append(pre);btn(pane,'Copy JSON',()=>Client.copyAccount(txt));btn(pane,'Back to settings',()=>editCore(connect))}
 btn(root,connect?'Save & connect':'Save & select',()=>{const p=values();if(!Client.save(JSON.stringify(p))){msg.textContent='Check the server address, port, credential, path, Host, SNI and certificate pin.';return}drafts.set(out,p);$('sheet').close();refresh();if(connect){Client.connect();page('log')}}).className='apply'
}
