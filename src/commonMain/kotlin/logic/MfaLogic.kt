package logic

import kotlinx.serialization.Serializable
import neton.core.http.BadRequestException
import neton.database.api.DbContext
import neton.database.api.Row
import neton.logging.Logger
import neton.security.crypto.HmacSha256
import neton.security.crypto.SecretBox
import neton.security.crypto.Totp
import neton.security.random.SecureRandom
import table.UserTable
import kotlin.time.Clock

@Serializable data class MfaStatus(val required: Boolean, val bound: Boolean)
@Serializable data class MfaChallenge(val status: String, val challengeToken: String, val challengeExpiresAt: Long)
@Serializable data class MfaEnrollment(val secret: String, val otpauthUri: String)
data class MfaCompletion(val userId: Long, val revision: Long)

/** PostgreSQL row locks make challenge consumption and replay prevention atomic across instances. */
class MfaLogic(
    private val db: DbContext, private val masterKey: String?, private val log: Logger,
    private val findUser: suspend (Long) -> model.User? = { UserTable.get(it) },
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val box = masterKey?.takeIf { it.isNotBlank() }?.let { SecretBox.fromBase64(it) }
    private fun requireBox() = box ?: throw BadRequestException("MFA is not configured on this server")
    private fun now() = clock()
    private fun hash(token: String): String {
        requireBox()
        if (token.length != 64 || token.any { it !in "0123456789abcdef" }) throw BadRequestException("Invalid or expired MFA challenge")
        return HmacSha256.signHex(masterKey!!.encodeToByteArray(), token.encodeToByteArray())
    }
    private suspend fun state(id: Long, lock: Boolean = false): Row {
        db.execute("INSERT INTO system_mfa (user_id) VALUES (:id) ON CONFLICT (user_id) DO NOTHING", mapOf("id" to id))
        return db.fetchAll("SELECT * FROM system_mfa WHERE user_id = :id" + if (lock) " FOR UPDATE" else "", mapOf("id" to id)).single()
    }
    suspend fun status(id: Long): MfaStatus {
        findUser(id) ?: throw BadRequestException("User not found")
        val row = state(id)
        return MfaStatus(row.int("required") == 1, row.stringOrNull("secret") != null)
    }
    suspend fun setRequired(id: Long, required: Boolean, actor: String) {
        if (required) requireBox()
        findUser(id) ?: throw BadRequestException("User not found")
        db.transaction {
            state(id, true)
            db.execute("UPDATE system_mfa SET required = :required, revision = revision + 1 WHERE user_id = :id AND required <> :required", mapOf("id" to id, "required" to if (required) 1 else 0))
            db.execute("DELETE FROM system_mfa_challenges WHERE user_id = :id", mapOf("id" to id))
        }
        log.info("mfa.policy.updated actor=$actor user=$id required=$required")
    }
    suspend fun reset(id: Long, actor: String) {
        if (actor == id.toString()) throw BadRequestException("Ask another administrator to reset MFA")
        findUser(id) ?: throw BadRequestException("User not found")
        db.transaction {
            state(id, true)
            db.execute("UPDATE system_mfa SET secret = NULL, last_step = -1, revision = revision + 1 WHERE user_id = :id", mapOf("id" to id))
            db.execute("DELETE FROM system_mfa_challenges WHERE user_id = :id", mapOf("id" to id))
        }
        log.info("mfa.reset actor=$actor user=$id")
    }
    suspend fun challenge(id: Long, passwordHash: String): MfaChallenge? = db.transaction {
        val row = state(id, true)
        if (row.int("required") == 0 && row.stringOrNull("secret") == null) return@transaction null
        val cipher = requireBox()
        val enrollment = row.stringOrNull("secret") == null
        val token = SecureRandom.hex(32)
        val expires = now() + 300_000
        val pendingSecret = if (enrollment) cipher.seal(Totp.base32(SecureRandom.bytes(20))) else null
        db.execute("DELETE FROM system_mfa_challenges WHERE user_id = :id", mapOf("id" to id))
        db.execute("INSERT INTO system_mfa_challenges (user_id, token_hash, revision, enrollment, secret, password_hash, expires_at) VALUES (:id, :hash, :revision, :enrollment, :secret, :password, :expires)",
            mapOf("id" to id, "hash" to hash(token), "revision" to row.long("revision"), "enrollment" to if (enrollment) 1 else 0, "secret" to pendingSecret, "password" to passwordHash, "expires" to expires))
        MfaChallenge(if (enrollment) "mfa_enrollment_required" else "mfa_required", token, expires)
    }
    suspend fun enrollment(token: String): MfaEnrollment {
        val c = db.fetchAll("SELECT * FROM system_mfa_challenges WHERE token_hash = :hash", mapOf("hash" to hash(token))).singleOrNull()
            ?: throw BadRequestException("Invalid or expired MFA challenge")
        if (c.int("enrollment") != 1 || c.long("expires_at") <= now() || c.int("attempts") >= 5) throw BadRequestException("Invalid or expired MFA challenge")
        val user = findUser(c.long("user_id")) ?: throw BadRequestException("Invalid or expired MFA challenge")
        val s = state(user.id)
        if (user.status != 1 || user.passwordHash != c.string("password_hash") || s.long("revision") != c.long("revision")) throw BadRequestException("Invalid or expired MFA challenge")
        val secret = requireBox().open(c.string("secret"))
        // Numeric account label prevents URI injection; issuer is deliberately stable across business renames.
        return MfaEnrollment(secret, "otpauth://totp/Neton:admin-${user.id}?secret=$secret&issuer=Neton&algorithm=SHA1&digits=6&period=30")
    }
    suspend fun verify(token: String, code: String): MfaCompletion {
        val tokenHash = hash(token)
        // Read owner first; every writer locks state BEFORE challenge, preventing lock inversion.
        val owner = db.fetchAll("SELECT user_id FROM system_mfa_challenges WHERE token_hash = :hash", mapOf("hash" to tokenHash)).singleOrNull()?.long("user_id")
            ?: throw BadRequestException("Invalid or expired MFA challenge")
        val result = db.transaction {
            val s = state(owner, true)
            val c = db.fetchAll("SELECT * FROM system_mfa_challenges WHERE user_id = :id AND token_hash = :hash FOR UPDATE", mapOf("id" to owner, "hash" to tokenHash)).singleOrNull() ?: return@transaction null
            val user = findUser(owner)
            if (c.long("expires_at") <= now() || c.int("attempts") >= 5 || c.long("revision") != s.long("revision") || user == null || user.status != 1 || user.passwordHash != c.string("password_hash")) {
                db.execute("DELETE FROM system_mfa_challenges WHERE user_id = :id", mapOf("id" to owner))
                return@transaction null
            }
            val encrypted = if (c.int("enrollment") == 1) c.string("secret") else s.string("secret")
            val step = Totp.match(requireBox().open(encrypted), code, now() / 1000, s.long("last_step"))
            if (step == null) {
                db.execute("UPDATE system_mfa_challenges SET attempts = attempts + 1 WHERE user_id = :id", mapOf("id" to owner))
                return@transaction null // commit failed attempt; throw OUTSIDE transaction
            }
            db.execute("UPDATE system_mfa SET secret = :secret, last_step = :step WHERE user_id = :id", mapOf("id" to owner, "secret" to encrypted, "step" to step))
            db.execute("DELETE FROM system_mfa_challenges WHERE user_id = :id", mapOf("id" to owner))
            MfaCompletion(owner, s.long("revision"))
        }
        return result ?: throw BadRequestException("Invalid code or expired MFA challenge")
    }
    suspend fun revision(id: Long): Long = state(id).long("revision")
    suspend fun accepts(id: Long, revision: Long?, mfa: Boolean): Boolean {
        val s = state(id)
        return (revision ?: 0L) == s.long("revision") &&
            ((s.int("required") == 0 && s.stringOrNull("secret") == null) || (s.stringOrNull("secret") != null && mfa))
    }
}
