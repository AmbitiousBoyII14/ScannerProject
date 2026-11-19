'use strict';
const $=id=>document.getElementById(id),sample='gateway.example:443@demo:preview';
const names=['Use Payload','SSL','Enhanced','SlowDns','Enable DNS','UDP Custom','Psiphon','V2ray'];
const flags=new Set(['Use Payload','SSL','Enable DNS']);let view='ssh',state='idle',singleTimer,batchTimer,onApply;
const busy=()=>state==='pending'||state==='online'||Boolean(batchTimer);
function page(v){view=v;for(const id of ['ssh','log','batch'])$(id).hidden=id!==v;document.querySelectorAll('[data-page]').forEach(b=>b.setAttribute('aria-current',b.dataset.page===v?'page':'false'));scrollTo(0,0)}
document.querySelectorAll('[data-page]').forEach(b=>b.onclick=()=>page(b.dataset.page));
const engines=['V2ray','Psiphon','SlowDns','UDP Custom'];
const engine=()=>engines.find(n=>flags.has(n))||'SSH';
const opts={protocol:'VLESS',transport:'WebSocket',tls:true,host:'gateway.example',port:443,path:'/demo',uuid:'00000000-0000-4000-8000-000000000001',password:'preview',cipher:'chacha20-ietf-poly1305',dns:'192.0.2.53',dns2:'192.0.2.54',sni:'',payload:'WebSocket upgrade',session:'Scanner Custom',timeout:10};
const outgoing=()=>engine()==='V2ray'?opts.protocol:engine();
const profileLabel=()=>engine()==='V2ray'?`V2ray / ${opts.protocol} · ${opts.transport}${opts.tls?' + TLS':''}`:engine()==='SSH'?`SSH · ${flags.has('Use Payload')?opts.payload:'Direct TCP'}${flags.has('SSL')?' + TLS':''}`:engine()+' · preset simulation';
const inputs=new Map(),drawerButtons=new Map();let sshExtras=['Use Payload','SSL'];
function unavailable(name){
  if(busy())return 'Stop the session to edit.';
  if(name==='Enhanced')return 'Legacy behavior not verified for this release.';
  if(['Use Payload','SSL','SSH settings'].includes(name)&&engine()!=='SSH')return 'Only for the SSH profile.';
  if(name==='Payload'&&!flags.has('Use Payload'))return 'Enable Use Payload in SSH.';
  if(name==='SNI'&&!flags.has('SSL'))return 'Enable SSL in SSH.';
  if(name==='DNS Custom'&&!flags.has('Enable DNS'))return 'Enable DNS first.';
  if(engines.includes(name)&&engine()!==name)return `Select ${name} on the main screen.`;
  return '';
}
function syncControls(){
  $('modeSettings').disabled=busy();$('logStop').hidden=!busy();
  for(const [n,el] of inputs){const reason=engines.includes(n)?(busy()?'Stop the session to edit.':''):unavailable(n);el.checked=flags.has(n);el.disabled=!!reason;el.parentElement.title=reason;el.parentElement.classList.toggle('locked',!!reason)}
  document.querySelector('.account').hidden=engine()!=='SSH';$('profileLabel').textContent=profileLabel();
  $('modeNote').textContent=engine()==='SSH'?'SSL and payload can be combined for a matching SSH gateway. Enhanced is not modeled.':`Using ${engine()}. Uncheck it to return to SSH. SSH-only options are inactive.`;
  for(const [n,b] of drawerButtons){const reason=['Batch scanner','Session log','Mode guide'].includes(n)?'':unavailable(n);b.disabled=!!reason;b.title=reason;b.querySelector('small').textContent=reason}
}
for(const name of names){const label=document.createElement('label'),box=document.createElement('input');box.type='checkbox';inputs.set(name,box);box.onchange=()=>{
  if(engines.includes(name)){
    const wasSSH=engine()==='SSH';if(wasSSH)sshExtras=['Use Payload','SSL'].filter(n=>flags.has(n));
    for(const e of [...engines,'Use Payload','SSL'])flags.delete(e);
    if(box.checked)flags.add(name);else for(const n of sshExtras)flags.add(n);
  }else if(box.checked)flags.add(name);else flags.delete(name);
  paint();
};label.append(box,name);$('flags').append(label)}
function modal(title,draw,apply=()=>{},caption='Apply'){if($('drawer').open)$('drawer').close();$('sheetTitle').textContent=title;$('body').replaceChildren();draw($('body'));onApply=apply;$('apply').textContent=caption;$('sheet').showModal()}
$('apply').onclick=()=>{if(onApply()!==false)$('sheet').close()};$('dismiss').onclick=()=>$('sheet').close();
function tool(name){if(unavailable(name)&&name!=='Mode guide')return;
 if(name==='Mode guide'){modal(name,b=>{b.textContent='Choose one engine per attempt. SSH: direct or a provider-matched HTTP/TLS wrapper. V2ray: choose one outbound and its transport inside V2ray settings. Plain Shadowsocks uses a cipher + secret, not SSH or WebSocket in this preset. Enable DNS changes VPN resolver settings; SlowDns is a separate mode. Enhanced has no verified legacy rule. These are preview limits, not universal protocol restrictions.'},()=>{},'Done');return}
 const draft={...opts};let valid=()=>true;
 modal(name,b=>{const hint=document.createElement('p');hint.className='hint';hint.textContent='Local simulation. Example values only; no real credentials needed.';b.append(hint);const section=document.createElement('div');b.append(section);
 function form(){section.replaceChildren();const fields=[];
 function field(key,title,choices,type='text'){const label=document.createElement('label');label.className='field';label.textContent=title;const el=document.createElement(choices?'select':'input');if(choices)for(const v of choices)el.add(new Option(v,v));else el.type=type;el.value=draft[key];el.dataset.key=key;el.autocomplete='off';el.required=key!=='sni';if(key==='port'||key==='timeout'){el.min=1;el.max=key==='port'?65535:60}el.oninput=()=>{draft[key]=el.value;if(key==='protocol'){const ss=el.value==='Shadowsocks';draft.transport=ss?'TCP':'WebSocket';draft.tls=!ss;form()}};label.append(el);section.append(label);fields.push(el)}
 if(name==='V2ray'){field('protocol','Outbound',['VLESS','VMess','Trojan','Shadowsocks']);field('host','Server domain');field('port','Server port',null,'number');if(['VLESS','VMess'].includes(draft.protocol))field('uuid','Client UUID');else field('password','Example secret',null,'password');if(draft.protocol==='Shadowsocks'){field('cipher','AEAD method',['chacha20-ietf-poly1305','aes-256-gcm'])}else{field('transport','Transport',['TCP','WebSocket']);section.lastChild.lastChild.onchange=()=>form();if(draft.transport==='WebSocket')field('path','WebSocket path');const l=document.createElement('label'),tls=document.createElement('input');tls.type='checkbox';tls.checked=draft.tls;tls.disabled=draft.protocol==='Trojan';tls.onchange=()=>draft.tls=tls.checked;l.append(tls,' TLS'+(tls.disabled?' (required by this preset)':''));section.append(l)}}
 else if(name==='Payload')field('payload','Gateway fixture',['WebSocket upgrade','HTTP CONNECT']);
 else if(name==='SNI')field('sni','TLS server name (blank uses target)');
 else if(name==='SSH settings')field('timeout','Connection timeout (seconds)',null,'number');
 else if(name==='VPN settings')field('session','Session name');
 else if(name==='DNS Custom'){field('dns','Preferred DNS');field('dns2','Alternate DNS')}
 else {const p=document.createElement('p');p.textContent=`${name} owns this demo session. Provider-specific handshake details are not modeled.`;section.append(p)}
 valid=()=>{for(const el of fields){el.setCustomValidity('');if(el.dataset.key==='host'&&!domain.test(el.value))el.setCustomValidity('Enter a domain name.');if(el.dataset.key==='uuid'&&!/^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$/i.test(el.value))el.setCustomValidity('Enter a UUID.');if(el.dataset.key==='path'&&!el.value.startsWith('/'))el.setCustomValidity('Start the path with /.');if(['dns','dns2'].includes(el.dataset.key)&&!(el.value.split('.').length===4&&el.value.split('.').every(v=>/^\d{1,3}$/.test(v)&&+v<=255)))el.setCustomValidity('Use an IPv4 address in this preview.');if(!el.reportValidity())return false}return true};}
 form();},()=>{if(!valid())return false;Object.assign(opts,draft);paint()});}
$('modeSettings').onclick=()=>tool(engine()==='SSH'?'SSH settings':engine());
function menu(){syncControls();if(!$('drawer').open)$('drawer').showModal()};$('menu').onclick=$('add').onclick=menu;$('closeMenu').onclick=()=>$('drawer').close();
for(const [group,items] of Object.entries({Utility:['Payload','SNI'],Connection:['SSH settings','VPN settings','V2ray','SlowDns','Psiphon','UDP Custom'],Tools:['DNS Custom','Batch scanner','Session log','Mode guide']})){const caption=document.createElement('span');caption.className='group';caption.textContent=group.toUpperCase();$('links').append(caption);for(const item of items){const b=document.createElement('button'),reason=document.createElement('small');b.append(item,reason);drawerButtons.set(item,b);b.onclick=()=>{if(item==='Batch scanner'||item==='Session log'){$('drawer').close();page(item==='Batch scanner'?'batch':'log')}else tool(item)};$('links').append(b)}}
$('more').onclick=()=>{let dark;modal('Appearance',body=>{const label=document.createElement('label');dark=document.createElement('input');dark.type='checkbox';dark.checked=document.body.classList.contains('dark');label.append(dark,' Dark appearance');body.append(label)},()=>{document.body.classList.toggle('dark',dark.checked)})};
function log(text,kind=''){const d=document.createElement('div'),t=document.createElement('time');t.textContent='['+new Date().toLocaleTimeString('en-GB')+']';d.className='event';d.dataset.kind=kind||(/timed out/i.test(text)?'bad':/connecting to socket/i.test(text)?'pending':'');d.append(t);for(const part of text.split(/((?:[a-z0-9-]+\.)+[a-z0-9-]+(?::\d+)?)/ig)){const el=document.createElement('span');el.textContent=part;if(/^\d+\.\d+\./.test(part))el.className='ip';else if(/^[a-z0-9-]+\./i.test(part))el.className='host';d.append(el)}const box=$('events'),follow=box.scrollTop+box.clientHeight>=box.scrollHeight-8;box.append(d);if(follow)box.scrollTop=box.scrollHeight;while($('events').childElementCount>160)$('events').firstChild.remove()}
const domain=/^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}$/i;
function parse(text){const m=/^([^:@\s]+):(\d{1,5})@([^:\s]+):([\s\S]+)$/.exec(text);return m&&domain.test(m[1])&&+m[2]>0&&+m[2]<=65535?{host:m[1].toLowerCase(),port:+m[2]}:null}
const demoFingerprint='Zla+csd8mnMKkZtV4Ykd1TVIH16QZBwA3NfNxkgGFLs';
function steps(host,port){
 const ip='192.0.2.'+(Array.from(host).reduce((n,c)=>n+c.charCodeAt(0),0)%250+1),mode=engine(),name=outgoing();
 const list=[`Resolving ${name} hostname: ${host}`,`Starting service: ${opts.session} [simulation]`,`Starting ${profileLabel()}`,`Hostname verified: ${ip}`,'Setting up VPN interface'];
 if(flags.has('Enable DNS'))list.push('Preferred DNS: '+opts.dns,'Alternate DNS: '+opts.dns2,'DNS forwarding enabled');
 list.push('VPN interface ready',`Connecting to socket ${host}:${port}`);
 if(host==='timeout.example')return [...list,'Socket connection timed out','VPN interface released','Service stopped · failed'];
 if(!['SSH','V2ray'].includes(mode))return [...list,`${mode} provider session started (fixture)`,'Profile connectivity check passed','Service ready'];
 const tls=mode==='SSH'?flags.has('SSL'):opts.tls;
 if(mode==='SSH')list.push(`SSH connection timeout: ${opts.timeout} s`);
 if(mode==='SSH')list.push(flags.has('Use Payload')?'SSH via HTTP gateway':'Opening SSH transport');
 if(tls)list.push(`TLS handshake with ${mode==='SSH'&&opts.sni?opts.sni:host}`,`Peer certificate validated (fixture)`,`Peer host: ${mode==='SSH'&&opts.sni?opts.sni:host}`,`Protocol: TLSv1.3`,'Cipher suite: TLS_AES_128_GCM_SHA256','TLS handshake finished');
 if((mode==='SSH'&&flags.has('Use Payload')&&opts.payload==='WebSocket upgrade')||(mode==='V2ray'&&opts.transport==='WebSocket'))list.push('Opening WebSocket transport'+(mode==='V2ray'?' · '+opts.path:''),'Sending upgrade request to gateway',`Connected to socket ${host}:${port}`,'HTTP/1.1 101 Switching Protocols','WebSocket handshake validated');
 else if(mode==='SSH'&&flags.has('Use Payload'))list.push('Opening HTTP proxy tunnel','HTTP/1.1 200 Connection established');
 if(mode==='SSH')list.push('SSH-2.0-OpenSSH_9.6 (fixture)','Host key: SHA256:'+demoFingerprint+' (fixture)' ,'Key exchange algorithm: curve25519-sha256','Negotiated cipher: aes128-ctr · MAC: hmac-sha2-256','Authenticating with password','SSH authentication succeeded','TCP forwarding channel opened','SSH connected');
 else if(name==='Shadowsocks')list.push(`Shadowsocks AEAD stream: ${opts.cipher}`,'First authenticated response received');
 else list.push(`Opening ${name} outbound`,`${name} stream carried test request`);
 return [...list,'Profile connectivity check passed','Service ready'];}
function endpoint(){return engine()==='SSH'?parse($('account').value):engine()==='V2ray'?{host:opts.host,port:opts.port}:{host:'gateway.example',port:engine()==='SlowDns'?53:443}}
function targets(){return [...new Set($('domains').value.trim().toLowerCase().split(/[\s,;]+/).filter(Boolean))]}
function paint(){const active=state==='online'||state==='pending';$('reset').disabled=$('load').disabled=$('account').disabled=$('eye').disabled=busy();syncControls();$('connect').disabled=!!batchTimer;$('connect').dataset.state=state;$('connect').textContent={idle:'CONNECT',pending:'CANCEL',online:'DISCONNECT',error:'RETRY'}[state];$('domains').disabled=$('sample').disabled=$('import').disabled=!!batchTimer;const hosts=targets(),valid=hosts.every(h=>domain.test(h));$('count').textContent=`${hosts.length} unique domains`+(!valid?' · Check domain format':'')+(hosts.length>100?' · Limit 100':'');$('run').disabled=active||(!batchTimer&&(!hosts.length||hosts.length>100||!valid));$('run').textContent=batchTimer?'STOP BATCH':'CONNECT BATCH'}
$('eye').onclick=()=>{const show=$('account').type==='password';$('account').type=show?'text':'password';$('eye').setAttribute('aria-label',show?'Hide account line':'Show account line')};
$('connect').onclick=()=>{if(state==='online'||state==='pending'){clearInterval(singleTimer);state='idle';log('Stopping service');log('VPN interface released');log('Disconnected');$('sshStatus').textContent='Disconnected';paint();return}const p=endpoint();if(!p){$('sshStatus').textContent='Use host:port@username:password, or tap Load sample.';return}state='pending';paint();page('log');const list=steps(p.host,p.port);let i=0;log(list[i++]);$('sshStatus').textContent='Connecting… View LOG for stages.';singleTimer=setInterval(()=>{const last=i===list.length-1,failed=p.host==='timeout.example';log(list[i++],last?(failed?'bad':'ok'):'');if(last){clearInterval(singleTimer);state=failed?'error':'online';$('sshStatus').textContent=failed?'Connection timed out (demo).':'Connected (demo). No traffic is routed.';paint()}},460)};
$('logStop').onclick=()=>{if(batchTimer)$('run').click();else $('connect').click()};
$('clearLog').onclick=()=>$('events').replaceChildren();$('sample').onclick=()=>{$('domains').value='gateway.example\ntimeout.example\nrelay.example';paint()};$('domains').oninput=paint;$('import').onclick=()=>$('file').click();$('file').onchange=async()=>{const f=$('file').files[0];$('file').value='';if(!f)return;if(f.size>65536){$('batchStatus').textContent='File exceeds 64 KiB.';return}try{const text=await f.text();if(!batchTimer){$('domains').value=text;paint()}}catch{$('batchStatus').textContent='Could not read this file.'}};
$('run').onclick=()=>{if(batchTimer){clearInterval(batchTimer);batchTimer=null;log('Batch cancelled');$('batchStatus').textContent='Batch stopped.';paint();return}const p=endpoint();if(!p){$('batchStatus').textContent='Set a valid SSH account on the SSH tab first.';return}const hosts=targets(),port=p.port;let i=0,n=0,list=[];$('results').replaceChildren();function next(){if(n===0)list=steps(hosts[i],port);log(list[n++],n===list.length?(hosts[i]==='timeout.example'?'bad':'ok'):'');$('batchStatus').textContent=`${i+1}/${hosts.length} · ${hosts[i]}`;if(n===list.length){const d=document.createElement('div'),failed=hosts[i]==='timeout.example';d.className='result';d.dataset.kind=failed?'bad':'ok';d.textContent=hosts[i]+(failed?' · Timed out':' · Connected (demo)');$('results').append(d);n=0;if(++i===hosts.length){clearInterval(batchTimer);batchTimer=null;$('batchStatus').textContent=`${i} attempts finished · simulated`;paint()}}}batchTimer=setInterval(next,210);paint();next()};
$('load').onclick=()=>{if(busy())return;if(view==='batch')$('sample').click();else if(engine()!=='SSH'){page('ssh');tool(engine())}else{$('account').value=sample;$('sshStatus').textContent='Sample account loaded.';page('ssh')}};$('reset').onclick=()=>{if(busy())return;if(view==='ssh'){$('account').value='';$('sshStatus').textContent='';state='idle'}else if(view==='log')$('clearLog').click();else{$('domains').value='';$('results').replaceChildren();$('batchStatus').textContent=''}paint()};
// Show the fictional example on first paint, mask before the first edit.
$('account').value=sample;$('account').type='text';$('eye').setAttribute('aria-label','Hide account line');$('account').addEventListener('focus',()=>{$('account').type='password';$('eye').setAttribute('aria-label','Show account line')},{once:true});$('sample').click();log('Scanner Custom · local simulation');paint();
