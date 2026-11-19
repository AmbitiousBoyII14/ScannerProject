'use strict';
const $=id=>document.getElementById(id), sample='gateway.example\nedge.example\nrelay.example', key='relay-preview-runs-v1';
const domain=/^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z](?:[a-z0-9-]{0,61}[a-z0-9])?$/;
function parse(text){const parts=text.trim().toLowerCase().split(/[\s,;]+/).filter(Boolean).map(s=>s.replace(/\.$/,''));const unique=[...new Set(parts)];return {valid:unique.filter(x=>domain.test(x)),invalid:unique.filter(x=>!domain.test(x)),dupes:parts.length-unique.length};}
let runs=[],current=null,timer=null;
try{const saved=JSON.parse(localStorage.getItem(key));if(Array.isArray(saved))runs=saved.filter(r=>r&&Array.isArray(r.rows)&&r.rows.every(v=>typeof v.host==='string'&&typeof v.state==='string')).slice(0,15)}catch{}
function persist(){try{localStorage.setItem(key,JSON.stringify(runs));return true}catch{return false}}
function count(){const p=parse($('domains').value);$('count').textContent=`${p.valid.length} valid · ${p.dupes} duplicate${p.dupes===1?'':'s'}`+(p.invalid.length?` · ${p.invalid.length} invalid (${p.invalid[0].slice(0,35)})`:'')+(p.valid.length>100?' · limit 100':'');$('run').disabled=!timer&&(!p.valid.length||!!p.invalid.length||p.valid.length>100);}
function row(a,b,action){const d=document.createElement('div'),l=document.createElement('span'),r=document.createElement(action?'button':'small');d.className='result';l.textContent=a;r.textContent=b;if(action){r.className='ghost';r.onclick=action}d.append(l,r);return d;}
function render(){const r=current;if(!r)return;$('rows').replaceChildren(...r.rows.map(v=>row(v.host,v.state)));const finished=r.rows.filter(x=>x.state.startsWith('Demo:')).length;$('status').textContent=`${r.status} · ${finished}/${r.rows.length} simulated results`;$('progress').hidden=false;$('progress').max=r.rows.length;$('progress').value=finished;$('export').disabled=!!timer;}
function lock(active){$('preset').disabled=$('upload').disabled=$('domains').disabled=$('import').disabled=$('sample').disabled=active;$('run').textContent=active?'■  Stop batch':'▶  Run batch';count()}
function finish(stopped){clearInterval(timer);timer=null;current.status=stopped?'Stopped':'Complete';current.rows.forEach(v=>{if(!v.state.startsWith('Demo:'))v.state='Not completed'});runs.unshift(current);runs=runs.slice(0,15);const saved=persist();lock(false);render();if(!saved)$('status').append(' · History unavailable');archive()}
function run(){if(timer){finish(true);return}const p=parse($('domains').value);if(p.invalid.length||!p.valid.length||p.valid.length>100)return;current={time:new Date().toISOString(),mode:'Local simulation (no network requests)',status:'Running',rows:p.valid.map(host=>({host,state:'Queued'}))};let i=0;current.rows[0].state='Simulating…';timer=setInterval(()=>{const item=current.rows[i];item.state=i%3===1?'Demo: timeout':'Demo: handshake OK';if(++i===current.rows.length){finish(false);return}current.rows[i].state='Simulating…';render()},650);lock(true);render()}
function page(isHistory){$('work').hidden=isHistory;$('past').hidden=!isHistory;$('console').classList.toggle('active',!isHistory);$('history').classList.toggle('active',isHistory);$('pageName').textContent=isHistory?'Run history':'Scan console';window.scrollTo(0,0);if(isHistory)archive()}
function download(r){const u=URL.createObjectURL(new Blob([JSON.stringify(r,null,2)],{type:'application/json'}));const a=document.createElement('a');a.href=u;a.download='relay-demo-'+r.time.slice(0,19).replaceAll(':','-')+'.json';a.click();setTimeout(()=>URL.revokeObjectURL(u),1000)}
function archive(){$('savedRuns').textContent=`${runs.length} saved ${runs.length===1?'run':'runs'}`;const d=$('archive');d.replaceChildren();$('clear').disabled=!runs.length;if(!runs.length){d.textContent='No saved runs yet.';return}runs.forEach(r=>{const details=document.createElement('details'),summary=document.createElement('summary');summary.textContent=`${new Date(r.time).toLocaleString()} · ${r.rows.length} domains · ${r.status}`;details.append(summary,...r.rows.map(v=>row(v.host,v.state)),row('Simulated results','Export JSON',()=>download(r)));d.append(details)})}
$('run').onclick=run;$('console').onclick=()=>page(false);$('history').onclick=()=>page(true);$('clear').onclick=()=>{if(confirm('Delete all saved preview runs from this browser?')){runs=[];persist();archive()}};$('export').onclick=()=>current&&download(current);$('import').onclick=()=>$('file').click();$('file').onchange=async e=>{const f=e.target.files[0];e.target.value='';if(!f)return;try{if(f.size>65000)throw Error();const text=await f.text();if(timer)return;$('domains').value=text;count()}catch{$('count').textContent='Import failed. Choose a text file under 65 KB.'}};$('sample').onclick=()=>{$('domains').value=sample;count()};$('domains').oninput=count;$('sample').click();

archive();
$('preset').onclick=()=>{ $('sample').click(); $('domains').focus(); };
$('upload').onclick=()=>$('import').click();
$('recent').onclick=()=>page(true);
let demoState='idle', connectionTimer;
function setDemo(state){
  demoState=state;
  const hero=document.querySelector('.hero'); hero.dataset.connection=state;
  $('tunnelState').textContent={idle:'Tunnel on standby',pending:'Starting demo…',connected:'Demo connected'}[state];
  $('connect').textContent={idle:'▷  Connect demo',pending:'×  Cancel',connected:'□  Disconnect demo'}[state];
}
$('connect').onclick=()=>{
  clearTimeout(connectionTimer);
  if(demoState!=='idle'){setDemo('idle');return;}
  setDemo('pending');connectionTimer=setTimeout(()=>setDemo('connected'),800);
};
