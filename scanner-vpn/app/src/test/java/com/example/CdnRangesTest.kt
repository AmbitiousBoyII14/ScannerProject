package com.example.live

import java.net.InetAddress
import org.junit.Assert.*
import org.junit.Test

class CdnRangesTest {
    private fun label(ip:String)=CdnRanges.label(InetAddress.getByName(ip))
    @Test fun prefixBoundaries(){
        for(ip in listOf("172.64.0.0","172.67.161.217","172.71.255.255","104.21.42.119","2606:4700::1"))assertTrue(label(ip),label(ip).contains("Cloudflare"))
        for(ip in listOf("172.63.255.255","172.72.0.0","2001:db8::1"))assertEquals("No known CDN range",label(ip))
        assertTrue(label("151.101.1.1").contains("Fastly"));assertTrue(label("23.228.249.1").contains("CloudFront"));assertTrue(label("127.0.0.1").startsWith("Non-CDN"))
    }
}
