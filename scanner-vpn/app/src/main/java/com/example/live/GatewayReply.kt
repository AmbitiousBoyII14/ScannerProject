package com.example.live

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Stop exactly at CRLF CRLF: the same TLS record can contain SSH identification or a WS frame. */
internal fun readGatewayHeaders(input: InputStream): ByteArray {
    val out = ByteArrayOutputStream()
    var tail = 0
    while (out.size() < 16384) {
        val byte = input.read()
        if (byte < 0) break
        out.write(byte)
        tail = (tail shl 8) or byte
        if (tail == 0x0d0a0d0a) return out.toByteArray()
    }
    throw GatewayFailure("Incomplete or oversized HTTP response headers")
}

/** A present but invalid/empty accept always goes to RFC 6455 validation, never to raw SSH. */
internal fun mayUseRaw(headers: ByteArray): Boolean {
    val lines = headers.toString(Charsets.US_ASCII).removeSuffix("\r\n\r\n").split("\r\n")
    if (!Regex("HTTP/1\\.1 101(?: [^\\r\\n]*)?").matches(lines.first())) return false
    val fields = lines.drop(1)
    val header = Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+:[\\t -~]*")
    if (!fields.all(header::matches)) throw GatewayFailure("Malformed HTTP upgrade response header")
    return fields.none { it.substringBefore(':').equals("Sec-WebSocket-Accept", true) }
}
