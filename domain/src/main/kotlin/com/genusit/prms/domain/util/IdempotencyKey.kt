package com.genusit.prms.domain.util

import java.util.UUID

/**
 * Key sent with every money-changing request. The same key is reused when the user retries
 * after a failure, so the server records a payment at most once.
 */
object IdempotencyKey {
    fun new(): String = "and-" + UUID.randomUUID().toString()
}
