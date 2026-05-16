package io.github.sporadiclemon.statementparser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PdfBankProfilesTest {

    @Test fun `resolves Monzo profile by bank name in text`() {
        val text = "Monzo Bank Limited\nStatement period: Jan 2024"
        val profile = PdfBankProfiles.all.firstOrNull { it.bankNamePattern.containsMatchIn(text) }
        assertEquals("Monzo", profile?.name)
    }

    @Test fun `resolves Starling profile by bank name in text`() {
        val text = "Starling Bank\nAccount Statement"
        val profile = PdfBankProfiles.all.firstOrNull { it.bankNamePattern.containsMatchIn(text) }
        assertEquals("Starling", profile?.name)
    }

    @Test fun `resolves HSBC profile by bank name in text`() {
        val text = "HSBC UK Bank plc\nSort Code: 40-12-34"
        val profile = PdfBankProfiles.all.firstOrNull { it.bankNamePattern.containsMatchIn(text) }
        assertEquals("HSBC", profile?.name)
    }

    @Test fun `resolves Lloyds profile by bank name in text`() {
        val text = "Lloyds Bank plc\nRegistered in England and Wales"
        val profile = PdfBankProfiles.all.firstOrNull { it.bankNamePattern.containsMatchIn(text) }
        assertEquals("Lloyds", profile?.name)
    }

    @Test fun `returns null for unrecognised bank text`() {
        val text = "Random Finance Co\nStatement"
        val profile = PdfBankProfiles.all.firstOrNull { it.bankNamePattern.containsMatchIn(text) }
        assertNull(profile)
    }
}
