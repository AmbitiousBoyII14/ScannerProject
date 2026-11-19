'use strict';
const $=id=>document.getElementById(id), sample='gateway.example\ntimeout.example\nrelay.example', key='relay-preview-runs-v1';
const domain=/^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z](?:[a-z0-9-]{0,61}[a-z0-9])?$/;
function parse(text){const parts=text.trim().toLowerCase().split(/[\s,;]+/).filter(Boolean).map(s=>s.replace(/\.$/,''));const unique=[...new Set(parts)];return {valid:unique.filter(x=>domain.test(x)),invalid:unique.filter(x=>!domain.test(x)),dupes:parts.length-unique.length};}
let state='idle';
let runs=[],current=null,timer=null;
try{const saved=JSON.parse(localStorage.getItem(key));if(Array.isArray(saved))runs=saved.filter(r=>r&&Array.isArray(r.rows)&&r.rows.every(v=>typeof v.host==='string'&&typeof v.state==='string')).slice(0,15)}catch{}
function persist(){try{localStorage.setItem(key,JSON.stringify(runs));return true}catch{return false}}
function count(){const p=parse($('domains').value);$('count').textContent=`${p.valid.length} valid · ${p.dupes} duplicate${p.dupes===1?'':'s'}`+(p.invalid.length?` · ${p.invalid.length} invalid (${p.invalid[0].slice(0,35)})`:'')+(p.valid.length>100?' · limit 100':'');$('run').disabled=!timer&&(!p.valid.length||!!p.invalid.length||p.valid.length>100||!profileValid());}
function row(a,b,action){const d=document.createElement('div'),l=document.createElement('span'),r=document.createElement(action?'button':'small');d.className='result';l.textContent=a;r.textContent=b;if(action){r.className='ghost';r.onclick=action}d.append(l,r);return d;}
function render(){const r=current;if(!r)return;$('rows').replaceChildren(...r.rows.map(v=>row(v.host,v.state)));const finished=r.rows.filter(x=>x.state.startsWith('Demo:')).length;$('status').textContent=`${r.status} · ${r.profile} · ${finished}/${r.rows.length} simulated results`;$('progress').hidden=false;$('progress').max=r.rows.length;$('progress').value=finished;$('export').disabled=!!timer;}
function lock(active){$('preset').disabled=$('upload').disabled=$('domains').disabled=$('import').disabled=$('sample').disabled=active;$('batchLabel').textContent=active?'Stop batch':'Connect batch';syncProfile();count()}
function finish(stopped){clearInterval(timer);timer=null;current.status=stopped?'Stopped':'Complete';current.rows.forEach(v=>{if(!v.state.startsWith('Demo:'))v.state='Not completed'});runs.unshift(current);runs=runs.slice(0,15);const saved=persist();lock(false);render();if(!saved)$('status').append(' · History unavailable');archive()}
function run(){
  if(timer){trace('batchTrace','STOP','Batch cancelled');finish(true);return}
  const p=parse($('domains').value);if(p.invalid.length||!p.valid.length||p.valid.length>100||!profileValid())return;
  const cfg=config();current={time:new Date().toISOString(),mode:'Local simulation (no network requests)',profile:cfg.name,status:'Running',rows:p.valid.map(host=>({host,state:'Queued'}))};
  let i=0,step=0,events=[];$('batchTrace').replaceChildren();
  function tick(){const item=current.rows[i];if(step===0)events=stages(item.host,cfg);
    const [tag,message]=events[step++];item.state=tag;trace('batchTrace',tag,message);
    if(step===events.length){item.state=tag==='ONLINE'?'Demo: connected':'Demo: timeout';step=0;if(++i===current.rows.length){finish(false);return}}
    render();
  }
  timer=setInterval(tick,350);lock(true);tick();
}
function download(r){const u=URL.createObjectURL(new Blob([JSON.stringify(r,null,2)],{type:'application/json'}));const a=document.createElement('a');a.href=u;a.download='relay-demo-'+r.time.slice(0,19).replaceAll(':','-')+'.json';a.click();setTimeout(()=>URL.revokeObjectURL(u),1000)}
function archive(){$('savedRuns').textContent=`${runs.length} saved ${runs.length===1?'run':'runs'}`;const d=$('archive');d.replaceChildren();$('clear').disabled=!runs.length;if(!runs.length){d.textContent='No saved runs yet.';return}runs.forEach(r=>{const details=document.createElement('details'),summary=document.createElement('summary');summary.textContent=`${new Date(r.time).toLocaleString()} · ${r.rows.length} domains · ${r.status}`;details.append(summary,...r.rows.map(v=>row(v.host,v.state)),row('Simulated results','Export JSON',()=>download(r)));d.append(details)})}
$('run').onclick=run;$('clear').onclick=()=>{if(confirm('Delete all saved preview runs from this browser?')){runs=[];persist();archive()}};$('export').onclick=()=>current&&download(current);$('import').onclick=()=>$('file').click();$('file').onchange=async e=>{const f=e.target.files[0];e.target.value='';if(!f)return;try{if(f.size>65000)throw Error();const text=await f.text();if(timer)return;$('domains').value=text;count()}catch{$('count').textContent='Import failed. Choose a text file under 65 KB.'}};$('sample').onclick=()=>{$('domains').value=sample;count()};$('domains').oninput=count;$('sample').click();

archive();
$('preset').onclick=()=>{ $('sample').click(); $('domains').focus(); };
$('upload').onclick=()=>$('import').click();
$('recent').onclick=()=>go('past');
// Local fixture only. Both modes evaluate the same domain/profile pair the same way.
function simulate(host, profile){
  if(host==='timeout.example')return false;
  if(host==='gateway.example')return true;
  let hash=0;
  for(const c of `${host}:${profile}`)hash=(hash*31+c.charCodeAt(0))>>>0;
  return hash%4!==0;
}
function go(view){
  for(const id of ['home','work','configuration','past'])$(id).hidden=id!==view;
  document.querySelectorAll('[data-page]').forEach(b=>{
    const active=b.dataset.page===(view==='past'?'work':view);
    b.classList.toggle('active',active);b.setAttribute('aria-current',active?'page':'false');
  });
  if(view==='past')archive();window.scrollTo(0,0);
}
document.querySelectorAll('[data-page],[data-go]').forEach(b=>b.onclick=()=>go(b.dataset.page||b.dataset.go));
let delay,clock,start,lastFailure=false;
function target(){return $('target').value.trim().toLowerCase().replace(/\.$/,'')}
function paint(){
  const online=state==='connected',pending=state==='pending',valid=domain.test(target());
  $('target').disabled=online||pending;syncProfile();
  $('connect').disabled=!valid||!profileValid();
  document.querySelector('.cockpit').dataset.state=lastFailure?'failed':state;
  document.querySelectorAll('[data-fixture]').forEach(b=>b.disabled=online||pending);
  $('badge').textContent=online?'DEMO ONLINE':pending?'CONNECTING':lastFailure?'FAILED':'STANDBY';
  $('powerLabel').textContent=online?'Disconnect':pending?'Cancel':lastFailure?'Retry':'Connect';
  $('connect').setAttribute('aria-label',$('powerLabel').textContent+' demo tunnel');
  $('tunnelState').textContent=!valid?'Enter one valid domain':online?'Demo connected to '+target():pending?'Trying '+target()+'…':lastFailure?'Demo attempt timed out':'Ready to connect';
  if(valid&&(online||pending||lastFailure)){const lead=online?'Connected':pending?'Connecting':'Timed out';$('tunnelState').replaceChildren(document.createTextNode(lead+' · '),tokenize(`${target()}:${config().port}`))}
  $('address').textContent=online?'192.0.2.1':'—';$('latency').textContent=online?'27 ms':'—';
  if(!online){$('uptime').textContent='00:00';setRates(0,0)}
  count();
}
const presets=[
  {name:'SSH · WebSocket TLS',short:'WS+',detail:'SSH over secure WebSocket',proto:'SSH',tls:true,ws:true,port:443},
  {name:'SSH',short:'SSH',detail:'Direct TCP connection',proto:'SSH',port:22},
  {name:'SSH · WebSocket',short:'WS',detail:'HTTP upgrade transport',proto:'SSH',ws:true,port:80},
  {name:'SSH · SSL / SNI',short:'TLS',detail:'TLS with a server name',proto:'SSH',tls:true,port:443},
  {name:'VLESS · WebSocket TLS',short:'VL',detail:'UUID over secure WebSocket',proto:'VLESS',tls:true,ws:true,port:443},
  {name:'VLESS · TCP',short:'TCP',detail:'UUID over direct TCP',proto:'VLESS',port:443},
  {name:'Shadowsocks',short:'SS',detail:'AEAD encrypted proxy',proto:'SS',port:8388,auth:'AEAD session ready'},
  {name:'PHP proxy · HTTPS',short:'PHP',detail:'HTTPS script endpoint',proto:'PHP',tls:true,port:443,auth:'Demo script responded'},
  {name:'Trojan · TLS',short:'TR',detail:'Password over TLS',proto:'TROJAN',tls:true,port:443,auth:'Demo credential accepted'},
  {name:'VMess · WebSocket TLS',short:'VM',detail:'UUID over secure WebSocket',proto:'VMESS',tls:true,ws:true,port:443,auth:'Demo VMess session ready'},
  {name:'SOCKS5',short:'S5',detail:'SOCKS proxy transport',proto:'SOCKS5',port:1080,auth:'Demo negotiation complete'},
  {name:'HTTP CONNECT',short:'HTTP',detail:'HTTP proxy tunnel',proto:'HTTP',port:8080,auth:'Demo CONNECT accepted'}
];
let selected=0;
const drafts=new Map(), fields=[...$('profileFields').querySelectorAll('input,select')];
const defaults=Object.fromEntries(fields.map(f=>[f.id,f.value]));
presets.forEach((p,i)=>{
  const btn=document.createElement('button');btn.className='profilecard';
  for(const [cls,text] of [['sigil',p.short],['title',p.name],['desc',p.detail]]){
    const el=document.createElement('span');el.className=cls;el.textContent=text;btn.append(el);
  }
  btn.onclick=()=>{drafts.set(selected,Object.fromEntries(fields.map(f=>[f.id,f.value])));selected=i;
    const data=drafts.get(i)||{...defaults,port:p.port};fields.forEach(f=>f.value=data[f.id]);paint()};
  $('profiles').append(btn);
});
function syncProfile(){
  const p=presets[selected],locked=state!=='idle'||!!timer;
  $('profileFields').disabled=locked;
  [...$('profiles').children].forEach((el,i)=>{el.disabled=locked;el.setAttribute('aria-pressed',String(i===selected))});
  for(const [selector,visible] of [['[data-ssh]',p.proto==='SSH'],['[data-vless]',['VLESS','VMESS'].includes(p.proto)],['[data-ss]',p.proto==='SS'],['[data-php]',p.proto==='PHP'],['[data-secret]',['SS','TROJAN'].includes(p.proto)],['[data-ws]',p.ws],['[data-tls]',p.tls]]){
    document.querySelectorAll(selector).forEach(label=>{label.hidden=!visible;label.querySelector('input, select').disabled=!visible});
  }
  syncPing();
  $('summaryName').textContent=p.name;document.querySelectorAll('.profileName').forEach(el=>el.textContent=config().name);
  $('configNote').textContent=locked?'Stop active sessions to edit this profile.':'Use demo values. Credentials are never logged or saved.';
}
function profileValid(){return [...$('profileFields').querySelectorAll('input')].every(el=>el.closest('label').hidden||el.checkValidity())}
function config(){const p=presets[selected];return {...p,port:$('port').value,udp:$('udp').value==='Simulated',name:`${p.name} :${$('port').value}`}}
function stages(host,c){
  const n=Array.from(host).reduce((a,ch)=>(a*31+ch.charCodeAt(0))>>>0,0);
  const ip='192.0.2.'+(1+n%254),endpoint=`${host}:${c.port}`;
  const steps=[['START',`${c.proto} session → ${endpoint}`],['DNS',`A  ${host} → ${ip} (fixture)`],['TCP',`Dial ${ip}:${c.port}`]];
  if(!simulate(host,c.name))return [...steps,['ERROR',`Timed out at ${endpoint}`]];
  if(c.tls)steps.push(['TLS','TLS negotiation complete']);if(c.ws)steps.push(['WS','WebSocket upgrade accepted']);
  steps.push([c.proto,c.auth||(c.proto==='SSH'?'SSH authentication accepted':'Client UUID accepted')],['UDP',c.udp?'Forwarding enabled (demo)':'Forwarding disabled; skipped'],['ONLINE',`Connected to ${endpoint}`]);return steps;
}
function trace(id,tag,message){const box=$(id),line=document.createElement('div'),stamp=document.createElement('time'),badge=document.createElement('b');stamp.textContent=new Date().toLocaleTimeString('en-GB');badge.textContent=tag;line.dataset.tag=tag;line.append(stamp,badge,tokenize(message));box.append(line);while(box.children.length>120)box.firstElementChild.remove();box.scrollTop=box.scrollHeight}
$('profileFields').oninput=$('target').oninput=()=>{lastFailure=false;paint()};
$('connect').onclick=()=>{
  clearInterval(delay);clearInterval(clock);lastFailure=false;
  if(state!=='idle'){trace('singleTrace','STOP',state==='pending'?'Attempt cancelled':'Tunnel disconnected');state='idle';paint();return}
  if(!domain.test(target())||!profileValid())return;
  const events=stages(target(),config());let i=0;state='pending';paint();$('singleTrace').replaceChildren();
  function tick(){const [tag,msg]=events[i++];trace('singleTrace',tag,msg);if(i!==events.length)return;
    clearInterval(delay);lastFailure=tag==='ERROR';state=lastFailure?'idle':'connected';paint();if(lastFailure)return;
    start=Date.now();clock=setInterval(()=>{const t=Math.floor((Date.now()-start)/1000);setRates(42+21*Math.sin(t*.41),12+7*Math.cos(t*.55));$('uptime').textContent=[Math.floor(t/60),t%60].map(n=>String(n).padStart(2,'0')).join(':')},1000);
  }
  delay=setInterval(tick,350);tick();
};
function tokenize(message){
  const span=document.createElement('span');
  for(const part of message.split(/((?:[a-z0-9-]+\.)+[a-z0-9-]+(?::\d+)?)/ig)){
    const el=document.createElement('span');el.textContent=part;
    if(/^\d+\.\d+\./.test(part))el.className='ip-token';else if(/^[a-z0-9-]+\./i.test(part))el.className='host-token';span.append(el);
  }
  return span;
}
function setRates(down,up){$('downRate').textContent=down.toFixed(1);$('upRate').textContent=up.toFixed(1)}
for(const b of document.querySelectorAll('[data-fixture]'))b.onclick=()=>{$('target').value=b.dataset.fixture;lastFailure=false;paint()};
let pingKey='',pingTimer;
function syncPing(){const k=target()+'|'+config().name;if(k!==pingKey){clearTimeout(pingTimer);pingTimer=null;pingKey=k;$('pingResult').textContent='Not tested';$('pingResult').dataset.health='idle'}
  $('pingTarget').textContent=target()+':'+config().port;$('ping').disabled=!!pingTimer||!domain.test(target())||!profileValid();
}
$('ping').onclick=()=>{const host=target(),cfg=config();$('ping').disabled=true;$('pingResult').dataset.health='pending';$('pingResult').textContent='Pinging…';pingTimer=setTimeout(()=>{pingTimer=null;const ok=simulate(host,cfg.name);$('pingResult').textContent=ok?'Alive · 27 ms (demo)':'Dead · timed out (demo)';$('pingResult').dataset.health=ok?'alive':'dead';syncPing()},700)};
paint();go('home');
