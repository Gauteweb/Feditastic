package app.pleromastisk.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test fun nyereVersjon() {
        assertTrue(UpdateChecker.isNewer("v2.2.5", "2.2.4"))
        assertTrue(UpdateChecker.isNewer("v2.3", "2.2.4"))
        assertTrue(UpdateChecker.isNewer("v3.0", "2.10.1"))
        assertTrue(UpdateChecker.isNewer("2.2.10", "2.2.9"))
    }

    @Test fun likEllerEldreVersjon() {
        assertFalse(UpdateChecker.isNewer("v2.2.4", "2.2.4"))
        assertFalse(UpdateChecker.isNewer("v2.2", "2.2.0"))
        assertFalse(UpdateChecker.isNewer("v2.2.3", "2.2.4"))
        assertFalse(UpdateChecker.isNewer("v2.1.9", "2.2"))
    }
}
