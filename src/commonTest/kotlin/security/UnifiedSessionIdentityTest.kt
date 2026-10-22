package security

import kotlinx.coroutines.runBlocking
import neton.core.interfaces.RequestContext
import neton.security.jwt.*
import kotlin.test.*

class UnifiedSessionIdentityTest {
    private fun authenticator(active: Boolean = true): RealmAuthenticator {
        val keys = object : JwksKeyProvider {
            override suspend fun resolve(kid: String) = SessionIdentityFixture.DEFAULT_JWK.takeIf { kid == it.kid }
        }
        val introspector = object : UnifiedTokenIntrospector {
            override suspend fun introspect(token: String) = IntrospectionResult(active = active, sessionVersion = 1)
        }
        val realms = TokenRealmRegistry().apply {
            register(object : TokenRealm {
                override val name = "account"
                override val routeGroups = setOf("app")
                override val acceptsExternalTokens = true
                override suspend fun resolve(token: VerifiedJwtToken) = null
            })
        }
        return RealmAuthenticator(
            JwtAuthenticator(secretKey = "test-only-secret-0123456789abcdef-0123456789"),
            UnifiedTokenAuthenticator(RsaJwtVerifier(keys), introspector, NoopSessionVersionCache),
            realms, "Authorization", "Bearer ", "neton_session",
        )
    }

    private fun request(token: String = SessionIdentityFixture.TOKEN_VALID) = object : RequestContext {
        override val path = "/app/auth/logout"
        override val method = "POST"
        override val routeGroup = "app"
        override val headers = mapOf("Authorization" to "Bearer $token", "X-Device-Id" to "victim-device")
    }

    @Test fun authenticatedDeviceSurvivesIdentityMappingAndIgnoresCallerHeader() = runBlocking {
        val identity = assertIs<DeviceBoundIdentity>(authenticator().authenticate(request()))
        assertEquals("42", identity.id)
        assertEquals("poc-dev", identity.deviceId)
        assertEquals(1L, identity.sessionVersion)
    }

    @Test fun revokedAndTamperedCredentialsCannotSupplyDeviceIdentity() = runBlocking {
        assertNull(authenticator(active = false).authenticate(request()))
        val token = SessionIdentityFixture.TOKEN_VALID
        val tampered = token.substringBeforeLast('.') + "." + "A" + token.substringAfterLast('.').drop(1)
        assertNull(authenticator().authenticate(request(tampered)))
    }
}
