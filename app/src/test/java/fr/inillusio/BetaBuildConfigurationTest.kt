package fr.inillusio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BetaBuildConfigurationTest {
    @Test fun betaIsNonDebuggableAndUsesDemoAdsWithSeparateIdentity() {
        if (BuildConfig.BUILD_TYPE != "beta") return

        assertFalse(BuildConfig.DEBUG)
        assertEquals("fr.inillusio.beta", BuildConfig.APPLICATION_ID)
        assertEquals("0.1.0-beta", BuildConfig.VERSION_NAME)
        assertTrue(BuildConfig.ADS_ENABLED)
        assertEquals("ca-app-pub-3940256099942544/5224354917", BuildConfig.ADMOB_REWARDED_UNIT_ID)
    }
}
