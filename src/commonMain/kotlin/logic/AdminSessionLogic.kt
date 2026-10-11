package logic

import neton.database.api.DbContext
import neton.logging.Logger

/**
 * 管理员会话与授权版本（spec application/identity-realm §4、评审 R04/R05/R18）。
 *
 * 管理员每次请求要确认四件事：账号仍启用、会话版本没变、MFA 状态与令牌一致、授权版本。
 * 这里用**一条** SQL 读齐，替代此前「查用户 → MFA 先 INSERT 再 SELECT」的多次往返；
 * 没有行的表按默认值处理（版本 0、未要求 MFA、未绑定），只读路径不写库。
 */
@neton.core.annotations.Logic(logger = "logic.admin-session")
class AdminSessionLogic(
    private val log: Logger,
    private val db: DbContext,
) {

    data class AccessState(
        val status: Int,
        val sessionVersion: Long,
        val mfaRevision: Long,
        val mfaRequired: Boolean,
        val mfaEnrolled: Boolean,
        val authzVersion: Long,
    ) {
        /** 与 [MfaLogic.accepts] 同一规则：要求了 MFA 就必须已绑定且本次会话通过了验证。 */
        fun acceptsMfa(tokenRevision: Long?, verified: Boolean): Boolean =
            tokenRevision == mfaRevision && ((!mfaRequired && !mfaEnrolled) || (mfaEnrolled && verified))
    }

    suspend fun accessState(userId: Long): AccessState? = db.fetchAll(
        """
        SELECT u.status AS status,
               COALESCE(s.session_version, 0) AS session_version,
               COALESCE(m.revision, 0) AS mfa_revision,
               COALESCE(m.required, 0) AS mfa_required,
               CASE WHEN m.secret IS NULL THEN 0 ELSE 1 END AS mfa_enrolled,
               COALESCE((SELECT version FROM system_authz_version WHERE id = 1), 0) AS authz_version
          FROM system_users u
          LEFT JOIN system_user_sessions s ON s.user_id = u.id
          LEFT JOIN system_mfa m ON m.user_id = u.id
         WHERE u.id = :id AND u.deleted = 0
        """.trimIndent(),
        mapOf("id" to userId),
    ).firstOrNull()?.let {
        AccessState(
            status = it.int("status"),
            sessionVersion = it.long("session_version"),
            mfaRevision = it.long("mfa_revision"),
            mfaRequired = it.int("mfa_required") == 1,
            mfaEnrolled = it.int("mfa_enrolled") == 1,
            authzVersion = it.long("authz_version"),
        )
    }

    suspend fun sessionVersion(userId: Long): Long =
        db.fetchAll("SELECT session_version FROM system_user_sessions WHERE user_id = :id", mapOf("id" to userId))
            .firstOrNull()?.long("session_version") ?: 0L

    /** 作废该管理员全部已签发令牌：改密、管理员重置密码、全端退出。原子自增，并发调用不会互相覆盖。 */
    suspend fun revokeAll(userId: Long) {
        db.execute(
            """
            INSERT INTO system_user_sessions (user_id, session_version) VALUES (:id, 1)
            ON CONFLICT (user_id) DO UPDATE SET session_version = system_user_sessions.session_version + 1
            """.trimIndent(),
            mapOf("id" to userId),
        )
        log.info("auth.admin.sessions_revoked", mapOf("userId" to userId))
    }

    /** 任何影响「谁有什么权限」的写操作之后调用。 */
    suspend fun bumpAuthorization() {
        db.execute("UPDATE system_authz_version SET version = version + 1 WHERE id = 1")
    }
}
