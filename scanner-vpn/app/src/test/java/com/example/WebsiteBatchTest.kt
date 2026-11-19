package com.example.live

import java.net.*
import java.io.File
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.*
import org.junit.Assert.*
import org.junit.Test

class WebsiteBatchTest {
    @Test fun domainListsAndTemplate(){
        val list=(1..351).joinToString("\r\n"){"x$it.example"}+"\n X1.EXAMPLE. \ninvalid:443\n"
        val input=WebsiteInput.hosts(list);assertEquals(351,input.hosts.size);assertEquals(1,input.skipped)
        assertEquals("/health",WebsiteInput.path("https://\$\$/health"));assertEquals("/",WebsiteInput.path("https://\$\$"))
        for(t in listOf("https://a/\$\$","https://\$\$:8443/","https://\$\$@a/","https://\$\$/\r\nBad","https://\$\$//elsewhere"))assertThrows(IllegalArgumentException::class.java){WebsiteInput.path(t)}
    }
    @Test fun groupedReportAndBoundedBoard(){
        val hosts=(1..25).map{"ok$it.test"}.toSet()+setOf("missing.test","bad.test");val dir=java.nio.file.Files.createTempDirectory("reports").toFile()
        try{val run=WebsiteBatch(hosts,"/health",1,{h,_->if(h=="bad.test")throw UnknownHostException();InetAddress.getByName("1.1.1.1")},{h,_,p,_->assertEquals("/health",p);if(h=="missing.test")404 else 200});run.run(dir,CheckPolicy()){false}
            val s=run.snapshot();assertEquals(27,s.getInt("done"));assertEquals(1,s.getInt("failed"));assertEquals(20,s.getJSONArray("groups").getJSONObject(0).getJSONArray("rows").length());val text=File(dir,"output.txt").readText();assertTrue(text.contains("[HTTP 4xx · 1]\nmissing.test\tHTTP 404"));assertTrue(text.contains("[Failed · 1]\nbad.test\tDNS lookup failed."));assertTrue(text.contains("ok1.test\tHTTP 200"));assertTrue(text.contains("[Known CDN matches"));assertEquals("Complete",s.getString("state"));WebsiteBatch(hosts,"/",0).run(dir,CheckPolicy()){true};assertTrue(File(dir,"output.txt").readText().contains("Stopped"))
        }finally{dir.deleteRecursively()}
    }
    @Test fun directVerifiedHeadGetsReal404(){
        val pem=System.getenv("GATEWAY_TEST_CERT");org.junit.Assume.assumeNotNull(pem)
        val ks=KeyStore.getInstance(KeyStore.getDefaultType()).apply{load(null,null);File(pem).inputStream().use{setCertificateEntry("fixture",CertificateFactory.getInstance("X.509").generateCertificate(it))}}
        val tm=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply{init(ks)};val factory=SSLContext.getInstance("TLS").apply{init(null,tm.trustManagers,null)}.socketFactory;val ip=InetAddress.getByName("127.0.0.1")
        assertEquals(404,WebsiteInput.head("localhost",ip,"/missing",3000,22261,factory));assertThrows(SSLException::class.java){WebsiteInput.head("mismatch.test",ip,"/",3000,22261,factory)}
    }
}
