package security

import kotlin.test.*

class NetworkAdmissionTest {
    @Test fun ipv4AndIpv6Networks() {
        assertTrue(IpNetwork.parse("192.0.2.0/24").contains("192.0.2.255"))
        assertFalse(IpNetwork.parse("192.0.2.0/24").contains("192.0.3.0"))
        assertTrue(IpNetwork.parse("2001:db8::/32").contains("2001:0DB8:0:1::1"))
        assertFalse(IpNetwork.parse("2001:db8::/32").contains("2001:db9::1"))
        assertTrue(IpNetwork.parse("::1").contains("0:0:0:0:0:0:0:1"))
        assertTrue(IpNetwork.parse("::ffff:192.0.2.1").contains("::ffff:c000:201"))
        assertFalse(IpNetwork.parse("::/0").contains("192.0.2.1"))
        assertTrue(IpNetwork.parse("0.0.0.0/0").contains("192.0.2.1"))
    }

    @Test fun invalidPoliciesFailClosed() {
        for (bad in listOf("", "localhost", "192.168.01.1", "1.2.3.999", ":::", "1::2::3",
            "1.2.3.4/33", "::/129", "1.2.3.4/-1", "1.2.3.4:80", "fe80::1%en0", "::/")) {
            assertFails("Accepted $bad") { IpNetwork.parse(bad) }
        }
        assertFalse(IpNetwork.parse("0.0.0.0/0").contains("unknown"))
    }

    @Test fun enabledEmptyDeniesAndGroupsAreIndependent() {
        val gate = NetworkAdmission(mapOf("admin" to emptyList()))
        assertFalse(gate.permits("admin", "127.0.0.1"))
        assertTrue(gate.permits("app", "127.0.0.1"))
        val configured = NetworkAdmission(mapOf("admin" to listOf(IpNetwork.parse("192.0.2.0/24"))))
        assertTrue(configured.permits("admin", "192.0.2.42"))
        assertFalse(configured.permits("admin", "198.51.100.1"))
    }
}
