package com.genusit.prms.domain.util

/**
 * 1.11: the server sends each notification with a role-neutral route ("invoice/12", "receipt/OC-…",
 * "settlement/5" …). This maps it to the app screen of the signed-in role; null = just show the list.
 * Only known shapes are accepted (ids must be numbers, receipt numbers the server's format).
 */
object NotificationRoutes {
    private val RECEIPT = Regex("^[A-Z]{2,4}-[0-9A-Za-z-]{4,60}$")

    fun target(role: String, route: String?): String? {
        if (route.isNullOrBlank()) return null
        val head = route.substringBefore('/')
        val arg = route.substringAfter('/', "")
        val id = arg.toIntOrNull()?.takeIf { it > 0 }
        val receipt = arg.takeIf { head == "receipt" && RECEIPT.matches(it) }?.let { "receipt/$it" }
        return when (role) {
            "owner" -> when (head) {
                "invoice" -> id?.let { "owner/invoice/$it" }
                "receipt" -> receipt
                "subscription" -> "owner/subscription"
                "tenant" -> id?.let { "owner/tenant/$it" }
                "query" -> id?.let { "owner/query/$it" }
                "handovers" -> "owner/collections-hub"
                "settlement" -> id?.let { "owner/allocation/$it" }
                "invoices" -> "owner/invoices"
                "notifications" -> "notifications"
                else -> null
            }
            "employee" -> when (head) {
                "collect" -> id?.let { "emp/collect?tenant=$it&invoice=0" }
                "receipt" -> receipt
                "tenants" -> "emp/tenants"
                "handover" -> "emp/handover"
                "home" -> "emp/home"
                "notifications" -> "notifications"
                else -> null
            }
            "tenant" -> when (head) {
                "invoice" -> id?.let { "ten/invoice/$it" }
                "receipt" -> receipt
                "settlement" -> "ten/deposit"
                "query" -> id?.let { "ten/query/$it" }
                "invoices" -> "ten/invoices"
                "profile" -> "settings"
                "home" -> "ten/home"
                "notifications" -> "notifications"
                else -> null
            }
            else -> null
        }
    }
}
