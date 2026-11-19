const modes=['VLESS','VLESS WS','VLESS TLS','VLESS WS TLS','VMess','VMess WS','VMess TLS','VMess WS TLS','Trojan TLS','Trojan WS TLS','Shadowsocks'];
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
