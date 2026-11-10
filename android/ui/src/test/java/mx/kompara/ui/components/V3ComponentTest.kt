package mx.kompara.ui.components

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import mx.kompara.ui.fixtures.V3Fixture
import mx.kompara.ui.format.Formatters
import mx.kompara.ui.theme.KomparaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-mdpi")
class V3ComponentTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun primaryAndSwitchHaveMinimumTargetsAndCorrectActions() {
        var clicked = 0
        compose.setContent { KomparaTheme {
            Surface {
                Column {
                    PrimaryButton("Guardar", { clicked++ })
                    KomparaSwitch(true, { clicked++ }, Modifier.testTag("switch"))
                }
            }
        } }
        compose.onNodeWithText("Guardar").assertHeightIsAtLeast(56.dp).performClick()
        compose.onNodeWithTag("switch").assertHeightIsAtLeast(48.dp).assertIsOn()
        compose.runOnIdle { assertEquals(1, clicked) }
    }

    @Test fun statusGlyphsAreDecorative() {
        compose.setContent { KomparaTheme {
            Column {
                StatusDoor("Semáforo preparado", "Esperando una oferta", StatusGlyph.WAITING, {})
            }
        } }
        compose.onNodeWithText("Semáforo preparado", substring = true).assertHasClickAction().assertHeightIsAtLeast(48.dp)
        compose.onAllNodes(hasContentDescription("WAITING")).assertCountEquals(0)
    }

    @Test fun saveNoticeExpiresAfterFourSecondsAndAnnouncesPolitely() {
        val state = SaveSnackbarState().apply { saved("Guardado: tu mínimo ahora es $6.50/km") }
        compose.mainClock.autoAdvance = false
        compose.setContent { KomparaTheme { SaveSnackbar(state) } }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("Guardado: tu mínimo ahora es $6.50/km").assertExists()
            .assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.LiveRegion,
                androidx.compose.ui.semantics.LiveRegionMode.Polite))
        compose.mainClock.advanceTimeBy(4_100)
        compose.onNodeWithText("Guardado: tu mínimo ahora es $6.50/km").assertDoesNotExist()
    }

    @Test fun fixtureIsComputedAndClockIsFixed() {
        val fixture = V3Fixture(ApplicationProvider.getApplicationContext<Context>())
        assertEquals("$548", Formatters.formatMxnWhole(fixture.today))
        assertEquals("$1,026", Formatters.formatMxnWhole(fixture.closedDay))
        assertEquals("$4,980", Formatters.formatMxnWhole(fixture.weekNet))
        assertEquals("$10.34/km", Formatters.formatPerKm(fixture.rate("didi")))
        assertEquals("$1.21", Formatters.formatMxn(fixture.rate("didi").subtract(fixture.rate("uber"))))
        assertEquals("2026-10-19T20:40:00Z", java.time.Instant.ofEpochMilli(fixture.clock.nowMs()).toString())
    }

}
