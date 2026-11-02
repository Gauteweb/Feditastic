package app.pleromastisk.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CollapseTest {
    @Test fun kortTekstKortesIkke() = assertNull(collapsePoint("a".repeat(1000), 1000))
    @Test fun kuttesVedMellomrom() = assertEquals(998, collapsePoint("a".repeat(998) + " " + "b".repeat(50), 1000))
    @Test fun kuttesVedLinjeskift() =
        assertEquals(900, collapsePoint("a".repeat(900) + "\n" + "b c ".repeat(50), 1000))
    @Test fun utenMellomromKuttesPaaGrensen() = assertEquals(1000, collapsePoint("a".repeat(1500), 1000))
}
