package com.genusit.prms.domain.util

/** Identity documents the tenant form can read with OCR. */
enum class DocumentType { AADHAAR, PAN }

/**
 * Fields found on a document photo. Every value is a suggestion: the user reviews and confirms
 * each one before it is copied into the form (OCR is never assumed to be correct).
 */
data class DocumentFields(
    val name: String? = null,
    val number: String? = null,
    val address: String? = null,
) {
    val isEmpty: Boolean get() = name == null && number == null && address == null
}

/**
 * Pulls name / document number / address out of on-device OCR text (ML Kit returns lines top to bottom).
 * Pure Kotlin so the rules are unit-tested; numbers are only returned when they pass validation
 * (Aadhaar: 12 digits, first digit 2–9, Verhoeff checksum; PAN: AAAAA9999A).
 */
object DocumentParser {

    fun parse(type: DocumentType, lines: List<String>): DocumentFields {
        val clean = lines.map { it.trim() }.filter { it.isNotEmpty() }
        return when (type) {
            DocumentType.AADHAAR -> DocumentFields(aadhaarName(clean), aadhaarNumber(clean), aadhaarAddress(clean))
            DocumentType.PAN -> DocumentFields(panName(clean), panNumber(clean), null)
        }
    }

    /* ───────── Aadhaar ───────── */

    private val aadhaarRx = Regex("""(?<!\d)([2-9]\d{3})[\s-]?(\d{4})[\s-]?(\d{4})(?!\d)""")

    fun aadhaarNumber(lines: List<String>): String? =
        lines.asSequence()
            // the 16-digit VID printed on newer cards is not the Aadhaar number
            .filterNot { it.contains("VID", ignoreCase = true) }
            .flatMap { l -> aadhaarRx.findAll(fixDigits(l)).map { it.groupValues.drop(1).joinToString("") } }
            .firstOrNull { verhoeff(it) }

    /** Name is the line just above the date/year of birth or gender line on the card front. */
    private fun aadhaarName(lines: List<String>): String? {
        val idx = lines.indexOfFirst { l ->
            val u = l.uppercase()
            "DOB" in u || "D.O.B" in u || "YEAR OF BIRTH" in u || "BIRTH" in u || "जन्म" in l
        }.takeIf { it > 0 } ?: lines.indexOfFirst { l -> l.uppercase().let { "MALE" in it || "FEMALE" in it } }.takeIf { it > 0 }
        ?: return null
        return (idx - 1 downTo 0).asSequence().map { lines[it] }.firstOrNull { looksLikeLatinName(it) }?.let(::titleCase)
    }

    /** Back side: text after "Address" up to and including the 6-digit PIN code. */
    private fun aadhaarAddress(lines: List<String>): String? {
        val start = lines.indexOfFirst { it.startsWith("Address", ignoreCase = true) }.takeIf { it >= 0 } ?: return null
        val out = mutableListOf<String>()
        out += lines[start].substringAfter(":", "").trim()
        for (i in start + 1 until lines.size) {
            val l = lines[i]
            if (aadhaarRx.containsMatchIn(fixDigits(l)) || l.contains("VID", true)) break
            out += l
            if (Regex("""(?<!\d)\d{6}(?!\d)""").containsMatchIn(l)) break
            if (out.size >= 6) break
        }
        return out.filter { it.isNotBlank() }.joinToString(", ").replace(Regex(",\\s*,"), ",").trim(' ', ',').ifBlank { null }
    }

    /** Verhoeff checksum used by UIDAI for Aadhaar numbers. */
    fun verhoeff(num: String): Boolean {
        if (num.length != 12 || num.any { !it.isDigit() }) return false
        var c = 0
        num.reversed().forEachIndexed { i, ch -> c = D[c][P[i % 8][ch - '0']] }
        return c == 0
    }

    private val D = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), intArrayOf(1, 2, 3, 4, 0, 6, 7, 8, 9, 5), intArrayOf(2, 3, 4, 0, 1, 7, 8, 9, 5, 6),
        intArrayOf(3, 4, 0, 1, 2, 8, 9, 5, 6, 7), intArrayOf(4, 0, 1, 2, 3, 9, 5, 6, 7, 8), intArrayOf(5, 9, 8, 7, 6, 0, 4, 3, 2, 1),
        intArrayOf(6, 5, 9, 8, 7, 1, 0, 4, 3, 2), intArrayOf(7, 6, 5, 9, 8, 2, 1, 0, 4, 3), intArrayOf(8, 7, 6, 5, 9, 3, 2, 1, 0, 4),
        intArrayOf(9, 8, 7, 6, 5, 4, 3, 2, 1, 0),
    )
    private val P = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), intArrayOf(1, 5, 7, 6, 2, 8, 3, 0, 9, 4), intArrayOf(5, 8, 0, 3, 7, 9, 6, 1, 4, 2),
        intArrayOf(8, 9, 1, 6, 0, 4, 3, 5, 2, 7), intArrayOf(9, 4, 5, 3, 1, 2, 6, 8, 7, 0), intArrayOf(4, 2, 8, 6, 5, 7, 3, 9, 0, 1),
        intArrayOf(2, 7, 9, 3, 8, 0, 6, 4, 1, 5), intArrayOf(7, 0, 4, 6, 9, 1, 3, 2, 5, 8),
    )

    /* ───────── PAN ───────── */

    private val panRx = Regex("""(?<![A-Z0-9])([A-Z]{5}[0-9]{4}[A-Z])(?![A-Z0-9])""")

    fun panNumber(lines: List<String>): String? =
        lines.asSequence().mapNotNull { l -> panRx.find(fixPan(l.uppercase().replace(" ", "")))?.groupValues?.get(1) }.firstOrNull()

    /** New PAN cards: value on the line after "Name"; old cards: first name-like line after the header. */
    private fun panName(lines: List<String>): String? {
        val label = lines.indexOfFirst { l -> l.trim().uppercase().let { it == "NAME" || it.endsWith("/ NAME") || it.endsWith("/NAME") } }
        if (label >= 0) lines.drop(label + 1).firstOrNull { looksLikeLatinName(it) }?.let { return titleCase(it) }
        val header = lines.indexOfFirst { l -> l.uppercase().let { "INCOME TAX" in it || "GOVT" in it || "GOVERNMENT" in it } }
        return lines.drop(header + 1).firstOrNull { looksLikeLatinName(it) && !it.uppercase().contains("INDIA") }?.let(::titleCase)
    }

    /* ───────── helpers ───────── */

    private val notNames = setOf("GOVERNMENT", "INDIA", "INCOME", "TAX", "DEPARTMENT", "PERMANENT", "ACCOUNT", "NUMBER",
        "CARD", "FATHER", "SIGNATURE", "MALE", "FEMALE", "DOB", "BIRTH", "AADHAAR", "UNIQUE", "IDENTIFICATION", "AUTHORITY", "GOVT")

    private fun looksLikeLatinName(l: String): Boolean {
        val t = l.trim()
        if (t.length !in 3..60 || !t.matches(Regex("""[A-Za-z][A-Za-z .']+"""))) return false
        val words = t.uppercase().split(Regex("""[\s.]+""")).filter { it.isNotBlank() }
        return words.size in 1..5 && words.none { it in notNames }
    }

    private fun titleCase(s: String): String =
        s.trim().split(Regex("""\s+""")).joinToString(" ") { w -> w.lowercase().replaceFirstChar { it.uppercase() } }

    /** Common OCR slips inside digit groups: O→0, I/l→1, S→5, B→8. */
    private fun fixDigits(l: String): String =
        Regex("""[0-9OIlSB]{4}(?:[\s-]?[0-9OIlSB]{4}){2}""").replace(l) { m ->
            m.value.map { when (it) { 'O' -> '0'; 'I', 'l' -> '1'; 'S' -> '5'; 'B' -> '8'; else -> it } }.joinToString("")
        }

    /** PAN digits are positions 6–9: fix O→0 / I→1 there only. */
    private fun fixPan(l: String): String =
        Regex("""[A-Z]{5}[0-9OI]{4}[A-Z]""").replace(l) { m ->
            m.value.substring(0, 5) + m.value.substring(5, 9).replace('O', '0').replace('I', '1') + m.value.substring(9)
        }
}
