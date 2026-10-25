package com.genusit.prms.domain.error

/**
 * Error raised for every non-success answer from the server (or the network).
 * [code] is the stable machine code from the server (e.g. AMOUNT_EXCEEDS_BALANCE,
 * SUBSCRIPTION_EXPIRED, TOKEN_EXPIRED); [message] is already localized by the server
 * in the language sent with Accept-Language.
 */
class ApiException(
    val code: String,
    override val message: String,
    val httpStatus: Int,
    /** Raw field-error object from the server, as JSON text (null when none). */
    val fieldErrors: String? = null,
) : Exception(message) {
    val isAuthError: Boolean get() = httpStatus == 401
    val isSubscriptionBlock: Boolean get() = code == "SUBSCRIPTION_EXPIRED" || httpStatus == 402
    val isNetwork: Boolean get() = code == NETWORK
    /** Server answered with a technical problem (redirect, HTML instead of JSON) – not the user's fault. */
    val isServerMisconfig: Boolean get() = code == BAD_RESPONSE || code == SERVER_REDIRECT

    companion object {
        const val NETWORK = "NETWORK_ERROR"
        const val BAD_RESPONSE = "BAD_RESPONSE"
        /** HTTP 3xx that could not be followed safely (see the data layer's redirect policy). */
        const val SERVER_REDIRECT = "SERVER_REDIRECT"
    }
}
