package logic.provider

import neton.logging.Logger

/**
 * Email message provider (v1 placeholder — no real SMTP/HTTP integration yet).
 *
 * Does NOT fake success: a real send would parse `config` for SMTP settings or
 * HTTP API credentials (SendGrid, AWS SES, etc.). Until then [send] throws
 * [ProviderNotImplementedException] rather than returning `true`.
 */
class EmailProvider(
    private val log: Logger
) : MessageProvider {

    override val type: String = "email"

    override suspend fun send(receiver: String, content: String, config: String): Boolean {
        log.warn("email.provider.not_implemented")
        throw ProviderNotImplementedException(
            "Email provider is not implemented. Configure a real email provider or inject a test double."
        )
    }
}
