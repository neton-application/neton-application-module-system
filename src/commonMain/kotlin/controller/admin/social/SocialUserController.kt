package controller.admin.social

import controller.admin.social.dto.SocialBindRequest
import controller.admin.social.dto.SocialUserVO
import logic.SocialUserLogic
import neton.core.annotations.*
import neton.core.interfaces.Identity

@Controller("/system/social-user")
class SocialUserController(
    private val socialUserLogic: SocialUserLogic
) {

    @Get("/list")
    @Permission("system:social-user:query")
    suspend fun list(identity: Identity): List<SocialUserVO> {
        val userId = identity.id.toLong()
        return socialUserLogic.listByUser(userId, userType = 1)
    }

    @Get("/get/{id}")
    @Permission("system:social-user:query")
    suspend fun get(@PathVariable id: Long): SocialUserVO {
        return socialUserLogic.getById(id)
    }

    @Post("/bind")
    @Permission("system:social-user:create")
    suspend fun bind(identity: Identity, @Body request: SocialBindRequest): SocialUserVO {
        val userId = identity.id.toLong()
        return socialUserLogic.bind(
            userId = userId,
            userType = 1,  // admin user
            socialType = request.socialType,
            code = request.code,
            redirectUri = request.redirectUri ?: ""
        )
    }

    @Delete("/unbind")
    @Permission("system:social-user:delete")
    suspend fun unbind(identity: Identity, @Query socialType: String) {
        val userId = identity.id.toLong()
        socialUserLogic.unbind(userId, userType = 1, socialType = socialType)
    }
}
