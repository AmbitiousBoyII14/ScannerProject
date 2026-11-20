package com.example.live

import java.net.*
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.net.ssl.*
import org.json.JSONObject

/** One fixed TLS endpoint. The list is never used as a dial address or a source of credentials. */
internal class CoreAliasTemplate(raw:String) {
    private val draft=JSONObject(raw)
    private val fields=listOf("sni","wsHost","path")
    val base:Profile
    init {
        require(raw.length<=32768 && fields.any{draft.optString(it).contains("\$\$")})
        require(draft.keys().asSequence().none{it !in fields && draft.optString(it).contains("\$\$")})
        base=expand(draft.getString("host"));require(base.mode in setOf("VMess TLS","VMess WS TLS","VLESS TLS","VLESS WS TLS","Trojan TLS","Trojan WS TLS"))
        require(" WS " in base.mode || !draft.optString("path").contains("\$\$"))
    }
    private fun expand(name:String):Profile {
        val row=JSONObject(draft.toString());for(k in fields)if(row.has(k))row.put(k,row.getString(k).replace("\$\$",name));return Profile.read(row.toString())
    }
    fun approved(alias:String,certNames:Set<String>):Profile? {
        if(alias !in certNames)return null
        val row=try{expand(alias)}catch(_:IllegalArgumentException){return null};return row.takeIf{it.sni.ifEmpty{it.host} in certNames && (" WS " !in it.mode || it.wsHost.ifEmpty{it.host} in certNames)}
    }
    fun certificateNames(ip:InetAddress,ms:Int):Set<String> = Socket().use { tcp ->
        tcp.connect(InetSocketAddress(ip,base.port),ms);tcp.soTimeout=ms
        ((SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(tcp,base.host,base.port,true) as SSLSocket).use { tls ->
            tls.sslParameters=tls.sslParameters.apply{endpointIdentificationAlgorithm="HTTPS"};tls.startHandshake()
            val cert=tls.session.peerCertificates.first() as X509Certificate
            if(base.certPin.isNotBlank())require(MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString(""){"%02x".format(it)}==base.certPin)
            cert.subjectAlternativeNames.orEmpty().filter{it.first()==2 || it.first()==7}.mapNotNull{it.getOrNull(1) as? String}.filterNot{it.contains('*')}.map{it.lowercase()}.toSet()
        }
    }
}

// gomobile may surface a Go network timeout as a generic Exception. Normalize only
// transient network errors; never retry an explicit TLS, authentication or HTTP refusal.
internal fun aliasFailure(e:Exception):Exception {
    val s=e.message.orEmpty().lowercase()
    val http=Regex("(?:http(?:/1\\.[01])?|status(?: code)?)[: =]+([45][0-9]{2})").find(s)?.groupValues?.get(1)
    if(http!=null)return GatewayFailure("Gateway returned HTTP $http.")
    if(listOf("certificate","x509","tls","auth","unauthorized","forbidden").any{s.contains(it)})return e
    return when {
        listOf("i/o timeout","deadline exceeded","timed out").any{s.contains(it)}->SocketTimeoutException("V2ray timeout")
        s.contains("connection refused")->ConnectException("Connection refused")
        else->e
    }
}
