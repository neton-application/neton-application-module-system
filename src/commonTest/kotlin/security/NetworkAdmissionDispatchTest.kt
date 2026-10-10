package security

import kotlinx.coroutines.runBlocking
import neton.core.component.NetonContext
import neton.core.http.*
import neton.core.http.adapter.HttpServerConfig
import neton.core.interfaces.*
import neton.http.adapter.*
import neton.routing.ratelimit.ClientIpResolver
import kotlin.test.*

class NetworkAdmissionDispatchTest {
    @Test fun anonymousAdminIsProtectedButMemberRemainsPublic() = runBlocking {
        var handled = 0
        fun route(group: String) = RouteDefinition(
            pattern = "/login", method = HttpMethod.POST, routeGroup = group, allowAnonymous = true,
            handler = object : RouteHandler {
                override suspend fun invoke(context: HttpContext, args: HandlerArgs): Any {
                    handled++
                    return Unit
                }
            },
        )
        val routes = listOf(route("admin"), route("app"))
        val ctx = NetonContext(emptyArray()).apply {
            bind(RequestEngine::class, object : RequestEngine {
                override fun registerRoute(route: RouteDefinition) = Unit
                override fun getRoutes() = routes
            })
            bind(ClientAddressResolver::class, ClientAddressResolver { request ->
                ClientIpResolver.resolve(request.peerAddress, request.header("X-Forwarded-For"), null, setOf("127.0.0.1"))
            })
            bind(RequestAdmissionGate::class, NetworkAdmission(mapOf("admin" to listOf(IpNetwork.parse("192.0.2.0/24")))))
        }
        val dispatcher = BufferedHttpDispatcher(HttpServerConfig(port = 0)).also { it.bind(ctx) }
        suspend fun status(group: String, peer: String, forwarded: String) = dispatcher.dispatch(
            BufferedHttpRequest("POST", "/$group/login", "",
                mapOf("X-Forwarded-For" to listOf(forwarded), "X-Real-IP" to listOf("192.0.2.1")),
                ByteArray(0), remoteAddress = peer),
        ).status
        assertEquals(403, status("admin", "198.51.100.2", "192.0.2.1")) // forged XFF from a direct client
        assertEquals(200, status("admin", "127.0.0.1", "192.0.2.1"))
        assertEquals(403, status("admin", "127.0.0.1", "198.51.100.2"))
        assertEquals(403, status("admin", "127.0.0.1", "127.0.0.1")) // no X-Real-IP fallback
        assertEquals(200, status("app", "198.51.100.2", ""))
        assertEquals(2, handled)
    }
}
