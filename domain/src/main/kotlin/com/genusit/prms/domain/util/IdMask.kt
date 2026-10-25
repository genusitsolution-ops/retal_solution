package com.genusit.prms.domain.util

/** Shows only the last 4 characters of an ID number, like the web panel's maskId(). */
object IdMask {
    fun mask(value: String?): String? {
        val v = value?.replace(Regex("\\s+"), "").orEmpty()
        if (v.isEmpty()) return null
        if (v.length <= 4) return v
        return "X".repeat(v.length - 4) + v.takeLast(4)
    }
}
