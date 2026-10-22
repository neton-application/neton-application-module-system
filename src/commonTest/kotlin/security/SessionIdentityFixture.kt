package security

import neton.security.jwt.JwkRsaMaterial

// Public signed test vector from neton-security; no private key is embedded.
internal object SessionIdentityFixture {
    const val KID_V1: String = "v1"

    const val PUBLIC_JWK_N: String =
        "yK3eAX8z1ctMqvGq-90MHZOC2tHBrUVa4eLm7s9y00zmnLkkY-L4KPYFbDYoY7uca0VAHoNMp9DJE9g7ypNrw" +
            "1smr_heBpCLSCfg7lKGPWKWP8BzEPlXef3wMywQnnU4L43xDF6pt8CNnlcVV_A7dl86u2ugxIFHkqUZcxJ" +
            "YgcCogAI6ZeZSJBHLWj1z8zyhxWldmKJji9nZyFywcOJ3ur8xNMcALwFX8DuIduGhuXd4QcH9YtF0hEWJZ" +
            "KgHHoAaAxelRBsyTDc6AWvLeXAJQlzvOLWRiZsCMSPozvD5GVyyF9cdOr-PaWSbymGwHFPsWVcCzcfhImb" +
            "yEIsFRZKo6Q"

    const val PUBLIC_JWK_E: String = "AQAB"

    val DEFAULT_JWK: JwkRsaMaterial = JwkRsaMaterial(
        kid = KID_V1,
        n = PUBLIC_JWK_N,
        e = PUBLIC_JWK_E,
    )

    // ──────────── Tokens (all signed by v1.pem) ────────────
    // payload base values: iss=privchat-server, sub=42, aud=[privchat-application,privchat-server],
    // iat=1700000000, exp=2000000000 (year 2033), jti=poc-jti, typ=access, device_id=poc-dev,
    // session_version=1, scope=[user], business_system_id=privchat-application, app_id=poc.

    /** 标准 valid token；exp=2033 远期，session_version=1。 */
    const val TOKEN_VALID: String =
        "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6InYxIn0.eyJpc3MiOiJwcml2Y2hhdC1zZXJ2ZXIiLCJzdWIiOiI0MiIsImF1ZCI6WyJwcml2Y2hhdC1hcHBsaWNhdGlvbiIsInByaXZjaGF0LXNlcnZlciJdLCJpYXQiOjE3MDAwMDAwMDAsImV4cCI6MjAwMDAwMDAwMCwianRpIjoicG9jLWp0aSIsInR5cCI6ImFjY2VzcyIsImRldmljZV9pZCI6InBvYy1kZXYiLCJzZXNzaW9uX3ZlcnNpb24iOjEsInNjb3BlIjpbInVzZXIiXSwiYnVzaW5lc3Nfc3lzdGVtX2lkIjoicHJpdmNoYXQtYXBwbGljYXRpb24iLCJhcHBfaWQiOiJwb2MifQ.B1clh2esJ8HR1lOmolf0EEKWCNN6kNVljLlc3VpAqlsAyK_iFi1NZQdToyxSADFISWqqxajCmA5Dw_lmTqdMQoReHjePwjRxpyN4iAtV_X7C8ujv86UaeeEhLemgdvLnbZyVRUCx_bFQ7pUZOAbZYwSn9hMpDy5h2k7swx7yu-JUqyCLHLdbYw-nhKzElvyVU1PGIF80XB8hJVvaLds46_ZZs8fGvXASYj7oyVe5Vc1h-s5Jc9ErRi3OCsTkFXRDZ6Q0lnobaBoZYQM1E1R2hmjj2qt3uY2n_kHLP80UOaTu9ub8JJCrSU_cv7f3LnVz_gLpd4Q92RHmoDsXo1B4bQ"

}
