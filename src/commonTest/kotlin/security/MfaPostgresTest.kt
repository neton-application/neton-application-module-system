package security

import init.generated.SystemMigrationResources
import kotlinx.coroutines.*
import logic.MfaLogic
import model.User
import neton.core.config.getEnv
import neton.database.adapter.sqlx.SqlxDatabase
import neton.database.config.DatabaseConfig
import neton.database.config.DatabaseDriver
import neton.logging.*
import neton.security.crypto.Totp
import kotlin.test.*

/** Opt-in integration test: a disposable local database named mfa_integration is required. */
class MfaPostgresTest {
    @Test fun concurrentConsumptionFailureBudgetAndReset() = runBlocking<Unit> {
        val uri = getEnv("NETON_MFA_TEST_DATABASE_URI") ?: return@runBlocking
        val config = DatabaseConfig(DatabaseDriver.POSTGRESQL, uri)
        val info = config.parseUri() as neton.database.config.PostgresUriInfo
        require(info.host == "127.0.0.1" && info.database == "mfa_integration") {
            "MFA integration tests only accept a disposable localhost mfa_integration database"
        }
        val db = SqlxDatabase.initialize(config)
        try {
            // Use the production migration, not a parallel hand-written schema.
            val migration = SystemMigrationResources.sources.single().scripts.single { it.description == "system_mfa" }
            migration.content.split(';').filter { it.isNotBlank() }.forEach { db.execute(it) }
            db.execute("DELETE FROM system_mfa_challenges")
            db.execute("DELETE FROM system_mfa")
            val user = User(id = 91001, username = "mfa-test", nickname = "MFA test", passwordHash = "fixture-hash")
            var time = 1_700_000_000_000L
            val logger = object : Logger {
                override fun trace(msg: String, fields: Fields) {}
                override fun debug(msg: String, fields: Fields) {}
                override fun info(msg: String, fields: Fields) {}
                override fun warn(msg: String, fields: Fields, cause: Throwable?) {}
                override fun error(msg: String, fields: Fields, cause: Throwable?) {}
            }
            fun service() = MfaLogic(db, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", logger, { user }, { time })
            val a = service(); val b = service()
            // Confirm this adapter really uses simultaneous independent PostgreSQL sessions.
            val release = CompletableDeferred<Unit>()
            val acquired = kotlinx.coroutines.channels.Channel<Long>(4)
            val connections = (1..4).map {
                async(Dispatchers.Default) { db.transaction {
                    acquired.send(db.fetchAll("SELECT pg_backend_pid() AS pid").single().long("pid"))
                    release.await()
                } }
            }
            val pids = try {
                withTimeout(15_000) { (1..4).map { acquired.receive() } }
            } finally {
                release.complete(Unit) // release held transactions even when connection acquisition times out
            }
            withTimeout(15_000) { connections.awaitAll() }
            assertEquals(4, pids.toSet().size, "Must exercise independent real database connections")
            a.setRequired(user.id, true, "92001")
            val binding = a.challenge(user.id, user.passwordHash)!!
            val secret = a.enrollment(binding.challengeToken).secret
            fun code() = Totp.code(Totp.decode(secret), time / 30_000)
            val start = CompletableDeferred<Unit>()
            val consumers = (1..8).map { i -> async(Dispatchers.Default) {
                start.await()
                runCatching { (if (i % 2 == 0) a else b).verify(binding.challengeToken, code()) }
            } }
            start.complete(Unit)
            val results = withTimeout(20_000) { consumers.awaitAll() }
            assertEquals(1, results.count { it.isSuccess }, "One challenge may issue only one completion")
            assertEquals(0, db.fetchAll("SELECT * FROM system_mfa_challenges").size)
            assertTrue(a.status(user.id).bound)
            val completion = results.single { it.isSuccess }.getOrThrow()
            assertTrue(b.accepts(user.id, completion.revision, true))
            assertFalse(b.accepts(user.id, completion.revision, false))
            val login = a.challenge(user.id, user.passwordHash)!!
            assertEquals("mfa_required", login.status)
            assertFails { b.verify(login.challengeToken, code()) } // consumed time-step
            assertEquals(1, db.fetchAll("SELECT attempts FROM system_mfa_challenges").single().int("attempts"))
            // Concurrent errors must commit, including when the service throws to its caller.
            val wrong = (1..4).map { async(Dispatchers.Default) { runCatching { b.verify(login.challengeToken, "bad") } } }.awaitAll()
            assertTrue(wrong.all { it.isFailure })
            assertEquals(5, db.fetchAll("SELECT attempts FROM system_mfa_challenges").single().int("attempts"))
            time += 30_000
            assertFails { a.verify(login.challengeToken, code()) }
            val next = b.challenge(user.id, user.passwordHash)!!
            val authenticated = a.verify(next.challengeToken, code())
            val pending = b.challenge(user.id, user.passwordHash)!!
            a.reset(user.id, "92001")
            assertFalse(b.accepts(user.id, authenticated.revision, true))
            assertFails { b.verify(pending.challengeToken, code()) }
            val status = b.status(user.id)
            assertTrue(status.required); assertFalse(status.bound)
            assertEquals("mfa_enrollment_required", b.challenge(user.id, user.passwordHash)!!.status)
            println("MFA_POSTGRES_PASS connections=4 concurrentConsumers=8 winners=1 committedFailures=5 resetRevokesSession=true")
        } finally {
            SqlxDatabase.close()
        }
    }
}
