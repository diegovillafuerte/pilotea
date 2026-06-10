package mx.kompara.ui

import mx.kompara.data.model.Platform
import mx.kompara.data.settings.Settings
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeUiTextTest {

    @Test
    fun `lists active platforms sorted`() {
        val settings = Settings(
            enabledPlatforms = setOf(Platform.DIDI, Platform.UBER),
            thresholds = emptyMap(),
        )
        assertEquals("Plataformas activas: DIDI, UBER", HomeUiText.activePlatformsLabel(settings))
    }

    @Test
    fun `falls back when no platforms are active`() {
        val settings = Settings(enabledPlatforms = emptySet(), thresholds = emptyMap())
        assertEquals("Sin plataformas activas", HomeUiText.activePlatformsLabel(settings))
    }
}
