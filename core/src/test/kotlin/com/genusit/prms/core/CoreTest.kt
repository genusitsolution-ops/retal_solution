package com.genusit.prms.core

import kotlin.test.Test

class CoreTest {
    @Test fun moneyFormatting() = CoreChecks.moneyFormatting()
    @Test fun validators() = CoreChecks.validators()
    @Test fun lenientDecoding() = CoreChecks.lenientDecoding()
    @Test fun envelopeAndHeaders() = CoreChecks.envelopeAndHeaders()
    @Test fun redirects() = CoreChecks.redirects()
}
