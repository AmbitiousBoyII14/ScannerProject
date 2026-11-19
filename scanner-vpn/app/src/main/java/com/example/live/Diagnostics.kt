package com.example.live

/** Never display raw library errors, which may contain endpoint config or credentials. */
internal fun failureReason(error:Exception,fallback:String):String {
    val chain=generateSequence<Throwable>(error){it.cause}.take(6).toList()
    val text=chain.joinToString(" "){it.message.orEmpty()}.lowercase()
    chain.filterIsInstance<GatewayFailure>().firstOrNull()?.let{return it.detail}
    return when {
        chain.any{it is javax.net.ssl.SSLHandshakeException || it is javax.net.ssl.SSLPeerUnverifiedException}->"TLS handshake failed. Check the SNI name, certificate and TLS port."
        chain.any{it is java.net.UnknownHostException}->"DNS lookup failed."
        chain.any{it is com.jcraft.jsch.JSchHostKeyException}->"SSH host key does not match the trusted server. Connection refused."
        "auth fail" in text || "userauth fail" in text->"SSH server rejected authentication. Check the account and authentication method."
        "407" in text->"Proxy authentication required or rejected (HTTP 407)."
        "x509" in text || "certificate" in text->"TLS certificate verification failed. Check the server hostname and certificate."
        "connection refused" in text->"The endpoint refused the TCP connection. Check the port and service."
        "timed out" in text || "timeout" in text || "deadline exceeded" in text->"The connection timed out before verification finished."
        else->fallback
    }
}
