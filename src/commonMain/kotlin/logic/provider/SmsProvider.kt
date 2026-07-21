package logic.provider

import neton.logging.Logger

/**
 * SMS message provider (v1 placeholder — no real SMS integration yet).
 *
 * Does NOT fake success: a real send would parse `config` for API credentials
 * (Aliyun SMS, Twilio, etc.) and call the SMS API. Until then [send] throws
 * [ProviderNotImplementedException] rather than returning `true` — a fake success
 * would tell the caller a verification code was delivered when it never left the process.
 */
class SmsProvider(
    private val log: Logger
) : MessageProvider {

    override val type: String = "sms"

    override suspend fun send(receiver: String, content: String, config: String): Boolean {
        log.warn("sms.provider.not_implemented receiver=${mask(receiver)}")
        throw ProviderNotImplementedException(
            "SMS provider is not implemented. Configure a real SMS provider or inject a test double."
        )
    }

    private fun mask(receiver: String): String =
        if (receiver.length < 7) "***" else "${receiver.take(3)}****${receiver.takeLast(4)}"
}
