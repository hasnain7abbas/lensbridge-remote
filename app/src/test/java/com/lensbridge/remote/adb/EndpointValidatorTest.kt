package com.lensbridge.remote.adb

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EndpointValidatorTest {
    @Test fun acceptsIpv4AndHostnameWithoutPort() {
        assertNull(EndpointValidator.hostError("192.168.1.24"))
        assertNull(EndpointValidator.hostError("galaxy.local"))
    }

    @Test fun rejectsBlankAndCombinedEndpoint() {
        assertNotNull(EndpointValidator.hostError(""))
        assertNotNull(EndpointValidator.hostError("192.168.1.24:37123"))
    }

    @Test fun validatesPortRange() {
        assertNull(EndpointValidator.portError("37123"))
        assertNotNull(EndpointValidator.portError("0"))
        assertNotNull(EndpointValidator.portError("70000"))
        assertNotNull(EndpointValidator.portError("port"))
    }

    @Test fun pairingCodeMustBeSixDigits() {
        assertNull(EndpointValidator.pairingCodeError("123456"))
        assertNotNull(EndpointValidator.pairingCodeError("12345"))
        assertNotNull(EndpointValidator.pairingCodeError("12A456"))
    }
}
