package com.genusit.prms.domain

import com.genusit.prms.domain.util.DocumentParser
import com.genusit.prms.domain.util.DocumentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Synthetic OCR output only (made-up people and numbers; the Aadhaar number passes Verhoeff but is fictitious). */
class DocumentParserTest {

    @Test fun aadhaarFront() {
        val ocr = listOf("भारत सरकार", "GOVERNMENT OF INDIA", "रमेश कुमार", "RAMESH KUMAR", "जन्म तिथि/DOB: 01/01/1990", "पुरुष/ MALE",
            "2345 6789 0124", "मेरा आधार, मेरी पहचान")
        val f = DocumentParser.parse(DocumentType.AADHAAR, ocr)
        assertEquals("Ramesh Kumar", f.name)
        assertEquals("234567890124", f.number)
        assertNull(f.address)
    }

    @Test fun aadhaarBackAddressAndOcrSlips() {
        val ocr = listOf("Unique Identification Authority of India", "Address: S/O Suresh Kumar,", "Flat 12, Shanti Nagar",
            "Kothrud, Pune,", "Maharashtra - 411038", "2345 67B9 0I24", "VID : 9123 4567 8901 2345")
        val f = DocumentParser.parse(DocumentType.AADHAAR, ocr)
        assertEquals("234567890124", f.number, "B→8, I→1 corrected; VID ignored")
        assertEquals("S/O Suresh Kumar, Flat 12, Shanti Nagar, Kothrud, Pune, Maharashtra - 411038", f.address)
    }

    @Test fun invalidAadhaarIsRejected() {
        // checksum fails → never filled in
        assertNull(DocumentParser.parse(DocumentType.AADHAAR, listOf("RAMESH KUMAR", "DOB: 01/01/1990", "2345 6789 0125")).number)
        assertTrue(DocumentParser.verhoeff("234567890124"))
    }

    @Test fun panNewFormat() {
        val ocr = listOf("आयकर विभाग INCOME TAX DEPARTMENT", "भारत सरकार GOVT. OF INDIA", "स्थायी लेखा संख्या कार्ड",
            "Permanent Account Number Card", "ABCPE1234F", "नाम / Name", "RAMESH KUMAR", "पिता का नाम / Father's Name", "SURESH KUMAR")
        val f = DocumentParser.parse(DocumentType.PAN, ocr)
        assertEquals("ABCPE1234F", f.number); assertEquals("Ramesh Kumar", f.name)
    }

    @Test fun panOldFormatWithOcrSlip() {
        val ocr = listOf("INCOME TAX DEPARTMENT", "GOVT. OF INDIA", "RAMESH KUMAR", "SURESH KUMAR", "01/01/1990", "Permanent Account Number", "ABCPE12O4F")
        val f = DocumentParser.parse(DocumentType.PAN, ocr)
        assertEquals("ABCPE1204F", f.number, "O→0 in the digit part"); assertEquals("Ramesh Kumar", f.name)
    }

    @Test fun unreadablePhotoGivesNothing() {
        assertTrue(DocumentParser.parse(DocumentType.AADHAAR, listOf("~~", "blur")).isEmpty)
        assertTrue(DocumentParser.parse(DocumentType.PAN, emptyList()).isEmpty)
    }
}
