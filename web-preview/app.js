'use strict';
const $=id=>document.getElementById(id), sample='gateway.example\nedge.example\nrelay.example', key='relay-preview-runs-v1';
const domain=/^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z](?:[a-z0-9-]{0,61}[a-z0-9])?$/;
function parse(text){const parts=text.trim().toLowerCase().split(/[\s,;]+/).filter(Boolean).map(s=>s.replace(/\.$/,''));const unique=[...new Set(parts)];return {valid:unique.filter(x=>domain.test(x)),invalid:unique.filter(x=>!domain.test(x)),dupes:parts.length-unique.length};}
let state='idle';
let runs=[],current=null,timer=null;
try{const saved=JSON.parse(localStorage.getItem(key));if(Array.isArray(saved))runs=saved.filter(r=>r&&Array.isArray(r.rows)&&r.rows.every(v=>typeof v.host==='string'&&typeof v.state==='string')).slice(0,15)}catch{}
function persist(){try{localStorage.setItem(key,JSON.stringify(runs));return true}catch{return false}}
function count(){const p=parse($('domains').value);$('count').textContent=`${p.valid.length} valid · ${p.dupes} duplicate${p.dupes===1?'':'s'}`+(p.invalid.length?` · ${p.invalid.length} invalid (${p.invalid[0].slice(0,35)})`:'')+(p.valid.length>100?' · limit 100':'');$('run').disabled=!timer&&(!p.valid.length||!!p.invalid.length||p.valid.length>100);}
function row(a,b,action){const d=document.createElement('div'),l=document.createElement('span'),r=document.createElement(action?'button':'small');d.className='result';l.textContent=a;r.textContent=b;if(action){r.className='ghost';r.onclick=action}d.append(l,r);return d;}
function render(){const r=current;if(!r)return;$('rows').replaceChildren(...r.rows.map(v=>row(v.host,v.state)));const finished=r.rows.filter(x=>x.state.startsWith('Demo:')).length;$('status').textContent=`${r.status} · ${r.profile} · ${finished}/${r.rows.length} simulated results`;$('progress').hidden=false;$('progress').max=r.rows.length;$('progress').value=finished;$('export').disabled=!!timer;}
function lock(active){$('preset').disabled=$('upload').disabled=$('domains').disabled=$('import').disabled=$('sample').disabled=active;$('run').textContent=active?'■  Stop batch':'▶  Run batch';count()}
function finish(stopped){clearInterval(timer);timer=null;current.status=stopped?'Stopped':'Complete';current.rows.forEach(v=>{if(!v.state.startsWith('Demo:'))v.state='Not completed'});runs.unshift(current);runs=runs.slice(0,15);const saved=persist();lock(false);render();if(!saved)$('status').append(' · History unavailable');archive()}
function run(){if(timer){finish(true);return}const p=parse($('domains').value);if(p.invalid.length||!p.valid.length||p.valid.length>100)return;current={time:new Date().toISOString(),mode:'Local simulation (no network requests)',profile:$('profile').value,status:'Running',rows:p.valid.map(host=>({host,state:'Queued'}))};let i=0;current.rows[0].state='Connecting…';timer=setInterval(()=>{const item=current.rows[i];item.state=simulate(item.host,current.profile)?'Demo: connected':'Demo: timeout';if(++i===current.rows.length){finish(false);return}current.rows[i].state='Connecting…';render()},650);lock(true);render()}
function download(r){const u=URL.createObjectURL(new Blob([JSON.stringify(r,null,2)],{type:'application/json'}));const a=document.createElement('a');a.href=u;a.download='relay-demo-'+r.time.slice(0,19).replaceAll(':','-')+'.json';a.click();setTimeout(()=>URL.revokeObjectURL(u),1000)}
function archive(){$('savedRuns').textContent=`${runs.length} saved ${runs.length===1?'run':'runs'}`;const d=$('archive');d.replaceChildren();$('clear').disabled=!runs.length;if(!runs.length){d.textContent='No saved runs yet.';return}runs.forEach(r=>{const details=document.createElement('details'),summary=document.createElement('summary');summary.textContent=`${new Date(r.time).toLocaleString()} · ${r.rows.length} domains · ${r.status}`;details.append(summary,...r.rows.map(v=>row(v.host,v.state)),row('Simulated results','Export JSON',()=>download(r)));d.append(details)})}
$('run').onclick=run;$('clear').onclick=()=>{if(confirm('Delete all saved preview runs from this browser?')){runs=[];persist();archive()}};$('export').onclick=()=>current&&download(current);$('import').onclick=()=>$('file').click();$('file').onchange=async e=>{const f=e.target.files[0];e.target.value='';if(!f)return;try{if(f.size>65000)throw Error();const text=await f.text();if(timer)return;$('domains').value=text;count()}catch{$('count').textContent='Import failed. Choose a text file under 65 KB.'}};$('sample').onclick=()=>{$('domains').value=sample;count()};$('domains').oninput=count;$('sample').click();

archive();
$('preset').onclick=()=>{ $('sample').click(); $('domains').focus(); };
$('upload').onclick=()=>$('import').click();
$('recent').onclick=()=>go('past');
// Local fixture only. Both modes evaluate the same domain/profile pair the same way.
function simulate(host, profile){
  let hash=0;
  for(const c of `${host}:${profile}`)hash=(hash*31+c.charCodeAt(0))>>>0;
  return hash%4!==0;
}
function go(view){
  for(const id of ['home','work','past'])$(id).hidden=id!==view;
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
  $('target').disabled=$('profile').disabled=online||pending;
  $('connect').disabled=!valid;
  document.querySelector('.cockpit').dataset.state=state;
  $('badge').textContent=online?'DEMO ONLINE':pending?'LINKING':'STANDBY';
  $('powerLabel').textContent=online?'Disconnect':pending?'Cancel':'Connect';
  $('connect').setAttribute('aria-label',$('powerLabel').textContent+' demo tunnel');
  $('tunnelState').textContent=!valid?'Enter one valid domain':online?'Demo connected to '+target():pending?'Trying '+target()+'…':lastFailure?'Demo attempt timed out':'Ready to connect';
  $('address').textContent=online?'192.0.2.1':'—';$('latency').textContent=online?'27 ms':'—';
  if(!online)$('uptime').textContent='00:00';
  $('scanSession').textContent=$('profile').value+' · One simulated connection attempt per domain. No active VPN needed.';
}
$('target').oninput=$('profile').onchange=()=>{lastFailure=false;paint()};
$('connect').onclick=()=>{
  clearTimeout(delay);clearInterval(clock);lastFailure=false;
  if(state!=='idle'){state='idle';paint();return}
  if(!domain.test(target()))return;
  state='pending';paint();delay=setTimeout(()=>{
    lastFailure=!simulate(target(),$('profile').value);state=lastFailure?'idle':'connected';paint();
    if(lastFailure)return;
    start=Date.now();clock=setInterval(()=>{const t=Math.floor((Date.now()-start)/1000);$('uptime').textContent=[Math.floor(t/60),t%60].map(n=>String(n).padStart(2,'0')).join(':')},1000);
  },900);
};
paint();go('home');
