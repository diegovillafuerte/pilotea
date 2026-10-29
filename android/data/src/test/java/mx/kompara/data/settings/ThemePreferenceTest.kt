package mx.kompara.data.settings

import org.junit.Assert.*
import org.junit.Test

class ThemePreferenceTest {
    @Test fun freshAndUnknownPreferencesFollowSystem() {
        for (name in listOf(null, "", "DARK_PREFERRED", "future")) {
            val settings = SettingsSerialization.decode(null, { null }, lookupString = { name })
            assertEquals(ThemePreference.AUTOMATIC, settings.themePreference)
            assertFalse(settings.themePreference.isDark(false))
            assertTrue(settings.themePreference.isDark(true))
        }
    }
    @Test fun everyStoredPreferenceRoundTripsAndOverridesSystem() {
        ThemePreference.entries.forEach { value ->
            assertEquals(value, SettingsSerialization.decode(null, { null }, lookupString = {
                if (it == SettingsSerialization.KEY_THEME) value.name else null
            }).themePreference)
        }
        assertFalse(ThemePreference.LIGHT.isDark(true))
        assertTrue(ThemePreference.DARK.isDark(false))
    }
}
