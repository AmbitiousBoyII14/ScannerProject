package com.example.live

import java.net.SocketTimeoutException
import org.junit.Assert.*
import org.junit.Test

class CheckPolicyTest {
    @Test fun capsInputsAndNormalizesTargets(){
        assertEquals(listOf(BatchTarget("example.test",443)),BatchTarget.parse("EXAMPLE.TEST.:443\nexample.test:443"))
        for(t in listOf("https://example.test", "example.test:0", "host:65536", "user:pass@host", ""))assertThrows(IllegalArgumentException::class.java){BatchTarget.parse(t)}
        assertThrows(IllegalArgumentException::class.java){CheckPolicy(0)}
    }
    @Test fun retryIsExtraAndNeverOverridesTrust(){
        val p=CheckPolicy(2,true,1);var calls=0;var retried=0
        assertThrows(SocketTimeoutException::class.java){p.run({false},{retried++}){calls++;throw SocketTimeoutException()}}
        assertEquals(2,calls);assertEquals(1,retried)
        assertThrows(GatewayFailure::class.java){p.run({false},{fail("must not retry trust failures")}){throw GatewayFailure("Not approved")}}
    }
}
