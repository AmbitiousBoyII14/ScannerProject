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
internal class Profile(val mode:String,val host:String,val port:Int,private val secret:String,private val path:String,private val user:String){
    companion object {
        fun read(raw:String):Profile {require(raw.length<=4096);val j=JSONObject(raw);val m=j.getString("mode");val h=j.getString("host").trim().lowercase();val p=j.getInt("port");val u=j.optString("user","");val s=j.getString("secret");val w=j.optString("path","/");require(m in listOf("VLESS TLS","VLESS WS TLS","Trojan TLS","Shadowsocks","VMess WS TLS","SOCKS5 TLS","HTTP CONNECT TLS"));require(h.length in 1..253 && h.split('.').all {it.matches(Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?"))});require(p in 1..65535 && s.isNotBlank() && s.length<=512);if(m in listOf("SOCKS5 TLS","HTTP CONNECT TLS"))require(u.isNotBlank() && u.length<=128);if(m.startsWith("VLESS")||m.startsWith("VMess"))require(UUID.fromString(s).toString().equals(s,true));if(m.endsWith("WS TLS"))require(w.startsWith('/') && w.length<=512 && w.none {it.isISOControl()});return Profile(m,h,p,s,w,u)}
    }
    val label get()="$mode · $host:$port"
    fun config(address:String):String {
        val type=if(mode.startsWith("VLESS"))"vless" else if(mode=="Trojan TLS")"trojan" else when(mode){"VMess WS TLS"->"vmess";"SOCKS5 TLS"->"socks";"HTTP CONNECT TLS"->"http";else->"shadowsocks"}
        val node=obj("address" to address,"port" to port)
        val settings=if(type=="vless"){node.put("users",arr(obj("id" to secret,"encryption" to "none")));obj("vnext" to arr(node))}else if(type=="vmess"){node.put("users",arr(obj("id" to secret,"security" to "auto")));obj("vnext" to arr(node))}else if(type=="socks"||type=="http"){node.put("users",arr(obj("user" to user,"pass" to secret)));obj("servers" to arr(node))}else{node.put("password",secret);if(type=="shadowsocks")node.put("method","chacha20-ietf-poly1305");obj("servers" to arr(node))}
        val out=obj("tag" to "vpn","protocol" to type,"settings" to settings)
        if(type!="shadowsocks"){val ws=mode.endsWith("WS TLS");val stream=obj("network" to if(ws)"ws" else "tcp","security" to "tls","tlsSettings" to obj("serverName" to host));if(ws)stream.put("wsSettings",obj("path" to path,"host" to host));out.put("streamSettings",stream)}
        return obj("log" to obj("loglevel" to "none"),"stats" to obj(),"policy" to obj("system" to obj("statsOutboundUplink" to true,"statsOutboundDownlink" to true)),"inbounds" to arr(obj("protocol" to "tun","settings" to obj("name" to "scanner0","mtu" to 1500))),"outbounds" to arr(out)).toString()
    }
}
