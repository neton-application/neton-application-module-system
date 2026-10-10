package controller.admin.user

import kotlinx.serialization.Serializable
import logic.MfaLogic
import logic.MfaStatus
import neton.core.annotations.*
import neton.core.interfaces.Identity

@Serializable data class MfaPolicyRequest(val required: Boolean)

@Controller("/system/user/mfa")
class UserMfaController(private val mfa: MfaLogic) {
    @Get("/{id}") @Permission("system:user:query")
    suspend fun get(@PathVariable id: Long): MfaStatus = mfa.status(id)

    @Put("/{id}") @Permission("system:user:update")
    suspend fun update(@PathVariable id: Long, @Body request: MfaPolicyRequest, identity: Identity) {
        mfa.setRequired(id, request.required, identity.id)
    }
    @Post("/{id}/reset") @Permission("system:user:update-password")
    suspend fun reset(@PathVariable id: Long, identity: Identity) { mfa.reset(id, identity.id) }
}
