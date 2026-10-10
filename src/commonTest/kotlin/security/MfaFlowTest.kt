package security

import controller.admin.auth.dto.LoginResponse
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import logic.MfaLogic
import model.User
import neton.database.api.*
import neton.database.dsl.*
import neton.database.sql.*
import neton.logging.*
import neton.security.crypto.Totp
import kotlin.test.*

/** Service contract tests. Memory SQL adapter serializes transactions; PostgreSQL locking needs separate integration validation. */
class MfaFlowTest {
    private class Fixture {
        val db = MemoryDb()
        var time = 1_700_000_000_000L
        var user = User(id = 1, username = "tester", nickname = "Test", passwordHash = "hash")
        val service = MfaLogic(db, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", SilentLogger, { user }, { time })
        suspend fun start() = service.challenge(1, user.passwordHash)!!
        fun code(secret: String) = Totp.code(Totp.decode(secret), time / 30_000)
    }
    @Test fun enrollmentThenLoginAndReset() = runBlocking<Unit> {
        val f = Fixture()
        assertNull(f.service.challenge(1, "hash"))
        f.service.setRequired(1, true, "2")
        assertFalse(f.service.accepts(1, 0, false))
        val c = f.start()
        assertEquals("mfa_enrollment_required", c.status)
        val enrollment = f.service.enrollment(c.challengeToken)
        assertFalse(f.service.status(1).bound)
        val completion = f.service.verify(c.challengeToken, f.code(enrollment.secret))
        assertTrue(f.service.status(1).bound)
        assertTrue(f.service.accepts(1, completion.revision, true))
        assertFalse(f.service.accepts(1, completion.revision, false))
        assertFails { f.service.verify(c.challengeToken, f.code(enrollment.secret)) }
        val login = f.start()
        assertEquals("mfa_required", login.status)
        assertFails { f.service.enrollment(login.challengeToken) }
        assertFails { f.service.verify(login.challengeToken, f.code(enrollment.secret)) } // same time-step replay
        f.time += 30_000
        f.service.verify(login.challengeToken, f.code(enrollment.secret))
        f.service.setRequired(1, false, "2")
        assertEquals("mfa_required", f.start().status) // removing requirement doesn't unbind
        f.service.reset(1, "2")
        assertFalse(f.service.accepts(1, completion.revision, true))
        assertFails { f.service.reset(1, "1") }
    }
    @Test fun attemptsExpireAndPasswordChangesInvalidate() = runBlocking<Unit> {
        val f = Fixture(); f.service.setRequired(1, true, "2")
        var c = f.start(); val enrollment = f.service.enrollment(c.challengeToken)
        repeat(5) { assertFails { f.service.verify(c.challengeToken, "bad") } }
        assertFails { f.service.verify(c.challengeToken, f.code(enrollment.secret)) }
        c = f.start(); f.time += 300_001
        assertFails { f.service.enrollment(c.challengeToken) }
        assertFails { f.service.verify(c.challengeToken, "123456") }
        c = f.start(); f.user = f.user.copy(passwordHash = "changed")
        assertFails { f.service.verify(c.challengeToken, "123456") }
    }
    @Test fun concurrentConsumptionHasOneWinner() = runBlocking {
        val f = Fixture(); f.service.setRequired(1, true, "2")
        val c = f.start(); val secret = f.service.enrollment(c.challengeToken).secret
        val results = (1..4).map { async { runCatching { f.service.verify(c.challengeToken, f.code(secret)) }.isSuccess } }.awaitAll()
        assertEquals(1, results.count { it })
    }
    @Test fun explicitStatusAndNoSessionCredentialsInChallenge() {
        assertTrue(Json.encodeToString(LoginResponse.serializer(), LoginResponse()).contains("authenticated"))
        val json = Json.encodeToString(LoginResponse.serializer(), LoginResponse(status = "mfa_required", challengeToken = "challenge"))
        assertFalse(json.contains("accessToken")); assertFalse(json.contains("refreshToken"))
    }
    @Test fun missingKeyCannotEnableOrBypassMfa() = runBlocking<Unit> {
        val f = Fixture()
        val unconfigured = MfaLogic(f.db, null, SilentLogger, { f.user }, { f.time })
        assertFails { unconfigured.setRequired(1, true, "2") }
        f.service.setRequired(1, true, "2")
        assertFails { unconfigured.challenge(1, f.user.passwordHash) }
        assertFalse(unconfigured.accepts(1, 0, false))
        val c = f.start()
        f.service.setRequired(1, false, "2")
        assertFails { f.service.enrollment(c.challengeToken) }
        assertFails { f.service.verify(c.challengeToken, "123456") }
    }
}

private object SilentLogger : Logger {
    override fun trace(msg: String, fields: Fields) {}
    override fun debug(msg: String, fields: Fields) {}
    override fun info(msg: String, fields: Fields) {}
    override fun warn(msg: String, fields: Fields, cause: Throwable?) {}
    override fun error(msg: String, fields: Fields, cause: Throwable?) {}
}

private class MemoryDb : DbContext {
    private val mutex = Mutex()
    private val states = mutableMapOf<Long, MutableMap<String, Any?>>()
    private val challenges = mutableMapOf<Long, MutableMap<String, Any?>>()
    override val dialect: Dialect = PostgresDialect
    override val interceptors = emptyList<QueryInterceptor>()
    override suspend fun <R> transaction(block: suspend DbContext.() -> R): R = mutex.withLock { block(this) }
    override suspend fun inTransaction() = true
    override suspend fun fetchAll(sql: String, params: Map<String, Any?>): List<Row> {
        val source = if ("system_mfa_challenges" in sql) challenges else states
        return source.values.filter { (params["id"] == null || it["user_id"] == params["id"]) && (params["hash"] == null || it["token_hash"] == params["hash"]) }.map { TestRow(it.toMap()) }
    }
    override suspend fun execute(sql: String, params: Map<String, Any?>): Long {
        val id = params["id"] as Long
        when {
            sql.startsWith("INSERT INTO system_mfa (") -> states.getOrPut(id) { mutableMapOf("user_id" to id, "required" to 0, "secret" to null, "revision" to 0L, "last_step" to -1L) }
            sql.startsWith("DELETE FROM system_mfa_challenges") -> challenges.remove(id)
            sql.startsWith("INSERT INTO system_mfa_challenges") -> challenges[id] = mutableMapOf("user_id" to id, "token_hash" to params["hash"], "revision" to params["revision"], "enrollment" to params["enrollment"], "secret" to params["secret"], "password_hash" to params["password"], "expires_at" to params["expires"], "attempts" to 0)
            "SET required" in sql -> states.getValue(id).let { if (it["required"] != params["required"]) { it["required"] = params["required"]; it["revision"] = (it["revision"] as Long) + 1 } }
            "SET attempts" in sql -> challenges.getValue(id).let { it["attempts"] = (it["attempts"] as Int) + 1 }
            "SET secret = NULL" in sql -> states.getValue(id).let { it["secret"] = null; it["last_step"] = -1L; it["revision"] = (it["revision"] as Long) + 1 }
            "SET secret = :secret" in sql -> states.getValue(id).let { it["secret"] = params["secret"]; it["last_step"] = params["step"] }
            else -> error("Unhandled SQL: $sql")
        }
        return 1
    }
    override fun <T : Any> from(table: Table<T, *>): Pair<SelectBuilder, TableRef<T>> = error("unused")
    override suspend fun query(built: BuiltSql): List<Row> = error("unused")
    override suspend fun executeBuilt(built: BuiltSql): Long = error("unused")
}
private class TestRow(private val values: Map<String, Any?>) : Row {
    override fun long(name: String) = (values[name] as Number).toLong()
    override fun int(name: String) = (values[name] as Number).toInt()
    override fun string(name: String) = values[name] as String
    override fun double(name: String) = (values[name] as Number).toDouble()
    override fun boolean(name: String) = values[name] as Boolean
    override fun bytes(name: String) = values[name] as ByteArray
    override fun longOrNull(name: String) = (values[name] as? Number)?.toLong()
    override fun intOrNull(name: String) = (values[name] as? Number)?.toInt()
    override fun stringOrNull(name: String) = values[name] as? String
    override fun doubleOrNull(name: String) = (values[name] as? Number)?.toDouble()
    override fun booleanOrNull(name: String) = values[name] as? Boolean
    override fun bytesOrNull(name: String) = values[name] as? ByteArray
}
