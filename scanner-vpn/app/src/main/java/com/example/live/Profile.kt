package com.example.live

import android.content.Context
import go.Seq
import libv2ray.Libv2ray
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal fun obj(vararg kv: Pair<String, Any>)=JSONObject().apply {kv.forEach { put(it.first,it.second) }}
internal fun arr(vararg xs: Any)=JSONArray().apply {xs.forEach { put(it) }}
internal const val CHECK_URL="https://connectivitycheck.gstatic.com/generate_204"
internal object Engine {
    private var ready=false
    @Synchronized fun init(context:Context){if(!ready){Seq.setContext(context.applicationContext);Libv2ray.initCoreEnv(context.filesDir.absolutePath, "");ready=true}}
}
internal class Profile(val mode:String,val host:String,val port:Int,private val secret:String,val path:String,private val user:String, private val hostKey:String,val dns:String,val dns2:String,val sni:String,val payload:String,val udpGw:String){
    companion object {
        private fun ipv4(s:String)=s.split('.').let{it.size==4&&it.all{o->o.matches(Regex("[0-9]{1,3}"))&&o.toInt()<=255}}
        fun read(raw:String,forDiscovery:Boolean=false):Profile {require(raw.length<=32768);val j=JSONObject(raw);val m=j.getString("mode");val h=j.getString("host").trim().lowercase();val p=j.getInt("port");val u=j.optString("user","");val key=j.optString("hostKey","");val s=j.getString("secret");val w=j.optString("path","/");require(m in listOf("VLESS TLS","VLESS WS TLS","SSH","SSH TLS","SSH WS","SSH WS TLS","SSH HTTP","SSH HTTP TLS","Trojan TLS","Shadowsocks","VMess WS TLS","SOCKS5 TLS","HTTP CONNECT TLS"));require(h.length in 1..253 && h.split('.').all {it.matches(Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?"))});require(p in 1..65535 && s.isNotEmpty() && s.length<=8192);if(m.startsWith("SSH") && !(key.isEmpty() && forDiscovery))require(key.length<=4096 && key.trim().matches(Regex("(ssh-ed25519|ssh-rsa|ecdsa-sha2-nistp(256|384|521)) +[A-Za-z0-9+/=]+( +[^\\r\\n]*)?")));if(m.startsWith("SSH")||m in listOf("SOCKS5 TLS","HTTP CONNECT TLS"))require(u.isNotBlank() && u.length<=128);if(m.startsWith("VLESS")||m.startsWith("VMess"))require(UUID.fromString(s).toString().equals(s,true));if("WS" in m)require(w.startsWith('/') && w.length<=512 && w.none {it.isISOControl()});require(w.none{it=='#'||it.isWhitespace()}); val dns=j.optString("dns","1.1.1.1");require(dns.split('.').let{it.size==4&&it.all{o->o.matches(Regex("[0-9]{1,3}"))&&o.toInt()<=255}});val secondary=j.optString("dns2");if(secondary.isNotEmpty())require(ipv4(secondary));val name=j.optString("sni").trim().lowercase();if(name.isNotEmpty())require(name.length<=253&&name.split('.').all{it.matches(Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?"))});val payload=j.optString("payload");val udp=j.optString("udpGw");if(udp.isNotEmpty())require(m.startsWith("SSH")&&udp.count{it==':'}==1&&ipv4(udp.substringBefore(':'))&&(udp.substringAfter(':').toIntOrNull()?:0) in 1..65535);return Profile(m,h,p,s,w,u,key,dns,secondary,name,payload,udp).also{it.gateway{_,_->}}}
    }
    fun accountLine()=if(isSsh) "$host:$port@$user:$secret" else ""
    val label get()="$mode · $host:$port"
    val isSsh get()=mode.startsWith("SSH")
    fun gateway(log:(String,String)->Unit)=if(isSsh&&mode!="SSH")SshGateway(sni.ifBlank{host},mode.endsWith("TLS"),if(" WS" in mode||" HTTP" in mode) GatewayRequest.parse(payload,sni.ifBlank{host},port,path," WS" in mode) else null," WS" in mode,log) else null
    fun openSsh(ip:String,log:(String,String)->Unit={_,_->}):SshTransport? {
        if(!isSsh){
            log("OUTBOUND","${mode.substringBefore(' ')} · $host:$port · using Xray")
            log("CONFIG",if(" WS" in mode) "WebSocket transport selected" else "TCP transport selected")
            if(mode.endsWith("TLS"))log("CONFIG","TLS peer verification enabled for ${sni.ifBlank{host}}")
            return null
        }
        log("SSH","Opening $mode session to $host:$port; verifying pinned host key")
        return SshTransport.open(ip,port,user,secret,hostKey,gateway(log),udpGw,log)
    }
    fun config(address:String,ssh:SshTransport?=null):String {
        val type=if(mode.startsWith("VLESS"))"vless" else if(mode=="Trojan TLS")"trojan" else when(mode){"VMess WS TLS"->"vmess";"SOCKS5 TLS"->"socks";"HTTP CONNECT TLS"->"http";else->"shadowsocks"}
        val node=obj("address" to address,"port" to port)
        val settings=if(type=="vless"){node.put("users",arr(obj("id" to secret,"encryption" to "none")));obj("vnext" to arr(node))}else if(type=="vmess"){node.put("users",arr(obj("id" to secret,"security" to "auto")));obj("vnext" to arr(node))}else if(type=="socks"||type=="http"){node.put("users",arr(obj("user" to user,"pass" to secret)));obj("servers" to arr(node))}else{node.put("password",secret);if(type=="shadowsocks")node.put("method","chacha20-ietf-poly1305");obj("servers" to arr(node))}
        val out=if(isSsh){requireNotNull(ssh);obj("tag" to "vpn","protocol" to "socks","settings" to obj("address" to "127.0.0.1","port" to ssh.port,"user" to "vpn","pass" to ssh.token))}else obj("tag" to "vpn","protocol" to type,"settings" to settings)
        if(type!="shadowsocks"){val ws=mode.endsWith("WS TLS");val stream=obj("network" to if(ws)"ws" else "tcp","security" to "tls","tlsSettings" to obj("serverName" to sni.ifBlank{host}));if(ws)stream.put("wsSettings",obj("path" to path,"host" to host));out.put("streamSettings",stream)}
        val config=obj("log" to obj("loglevel" to "none"),"stats" to obj(),"policy" to obj("system" to obj("statsOutboundUplink" to true,"statsOutboundDownlink" to true)),"inbounds" to arr(obj("protocol" to "tun","settings" to obj("name" to "scanner0","mtu" to 1500))),"outbounds" to arr(out))
        if(isSsh){
            // SSH forwards TCP only. Translate Android resolver UDP into TCP DNS via the same SSH channel.
            config.getJSONArray("outbounds").put(obj("tag" to "resolver","protocol" to "dns","proxySettings" to obj("tag" to "vpn"),"settings" to obj("rewriteNetwork" to "tcp","rules" to arr(obj("action" to "direct")))))
            config.put("routing",obj("rules" to arr(obj("ip" to arr(*listOf(dns,dns2).filter{it.isNotEmpty()}.distinct().toTypedArray()),"port" to "53","network" to "udp","outboundTag" to "resolver"))))
        }
        return config.toString()
    }
}
