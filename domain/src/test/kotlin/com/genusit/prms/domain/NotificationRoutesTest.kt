package com.genusit.prms.domain

import com.genusit.prms.domain.util.NotificationRoutes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NotificationRoutesTest {
    @Test fun ownerRoutes() {
        assertEquals("owner/invoice/12", NotificationRoutes.target("owner", "invoice/12"))
        assertEquals("receipt/OC-20261101-ABCD", NotificationRoutes.target("owner", "receipt/OC-20261101-ABCD"))
        assertEquals("owner/allocation/5", NotificationRoutes.target("owner", "settlement/5"))
        assertEquals("owner/subscription", NotificationRoutes.target("owner", "subscription"))
        assertEquals("owner/collections-hub", NotificationRoutes.target("owner", "handovers"))
        assertEquals("owner/query/3", NotificationRoutes.target("owner", "query/3"))
    }

    @Test fun employeeAndTenantRoutes() {
        assertEquals("emp/collect?tenant=4&invoice=0", NotificationRoutes.target("employee", "collect/4"))
        assertEquals("emp/handover", NotificationRoutes.target("employee", "handover"))
        assertEquals("ten/invoice/9", NotificationRoutes.target("tenant", "invoice/9"))
        assertEquals("ten/deposit", NotificationRoutes.target("tenant", "settlement/5"))
        assertEquals("settings", NotificationRoutes.target("tenant", "profile"))
    }

    @Test fun unknownOrUnsafeRoutesAreIgnored() {
        assertNull(NotificationRoutes.target("tenant", "owner/invoice/1"))
        assertNull(NotificationRoutes.target("owner", "invoice/abc"))
        assertNull(NotificationRoutes.target("owner", "invoice/-1"))
        assertNull(NotificationRoutes.target("tenant", "receipt/../../x"))
        assertNull(NotificationRoutes.target("employee", "invoice/12"))
        assertNull(NotificationRoutes.target("owner", null))
        assertNull(NotificationRoutes.target("admin", "invoice/1"))
    }
}
