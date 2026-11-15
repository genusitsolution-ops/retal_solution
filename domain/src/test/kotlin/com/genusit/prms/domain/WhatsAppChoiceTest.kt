package com.genusit.prms.domain

import com.genusit.prms.domain.usecase.WhatsAppChoice
import com.genusit.prms.domain.usecase.WhatsAppChoice.BUSINESS
import com.genusit.prms.domain.usecase.WhatsAppChoice.Decision
import com.genusit.prms.domain.usecase.WhatsAppChoice.WHATSAPP
import kotlin.test.Test
import kotlin.test.assertEquals

class WhatsAppChoiceTest {
    private val both = setOf(WHATSAPP, BUSINESS)

    @Test fun savedChoiceIsUsedWithoutAsking() {
        assertEquals(Decision.Open(BUSINESS), WhatsAppChoice.decide(BUSINESS, both))
        assertEquals(Decision.Open(WHATSAPP), WhatsAppChoice.decide(WHATSAPP, both))
        assertEquals(Decision.Open(WHATSAPP), WhatsAppChoice.decide(WHATSAPP, setOf(WHATSAPP)))
    }

    @Test fun nothingSavedAsksOnlyWhenBothAreInstalled() {
        assertEquals(Decision.Ask(listOf(WHATSAPP, BUSINESS), null), WhatsAppChoice.decide(null, both))
        assertEquals(Decision.Open(BUSINESS), WhatsAppChoice.decide(null, setOf(BUSINESS)))
        assertEquals(Decision.Open(WHATSAPP), WhatsAppChoice.decide(null, setOf(WHATSAPP)))
    }

    @Test fun savedAppMissingOffersTheOther() {
        assertEquals(Decision.Ask(listOf(WHATSAPP), BUSINESS), WhatsAppChoice.decide(BUSINESS, setOf(WHATSAPP)))
        assertEquals(Decision.Ask(listOf(BUSINESS), WHATSAPP), WhatsAppChoice.decide(WHATSAPP, setOf(BUSINESS)))
    }

    @Test fun noWhatsAppKeepsTheOldFallback() {
        assertEquals(Decision.NoApp, WhatsAppChoice.decide(null, emptySet()))
        assertEquals(Decision.NoApp, WhatsAppChoice.decide(BUSINESS, emptySet()))
        assertEquals(Decision.NoApp, WhatsAppChoice.decide(null, setOf("com.example.other")))
    }

    @Test fun unknownSavedValueIsIgnored() {
        assertEquals(Decision.Ask(listOf(WHATSAPP, BUSINESS), null), WhatsAppChoice.decide("com.example.other", both))
    }
}
