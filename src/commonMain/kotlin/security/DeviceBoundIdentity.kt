package security

import neton.security.identity.Identity
import neton.security.identity.UserId

/**
 * 来自 server 统一 token 的应用身份，带上该会话的设备与会话代次。
 *
 * 业务需要「当前请求来自哪台设备」时（如签发绑定设备的 Room 票据）从这里取，
 * 不能信任请求体自报的 deviceId。
 */
interface DeviceBoundIdentity : neton.core.interfaces.Identity {
    val deviceId: String
    val sessionVersion: Long
}

data class UnifiedSessionIdentity(
    override val userId: UserId,
    override val deviceId: String,
    override val sessionVersion: Long,
    override val roles: Set<String> = emptySet(),
    override val permissions: Set<String> = emptySet(),
) : Identity, DeviceBoundIdentity
