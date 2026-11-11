package mx.kompara.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import mx.kompara.ui.theme.KomparaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

/** Native Compose bounds and accessibility contract; this does not certify TalkBack speech. */
@RunWith(AndroidJUnit4::class)
class V3AccessibilityInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun fullAndCompactChipExposeOneAccessibleName() {
        var compact by mutableStateOf(false)
        val model = ReglaModel(ReglaBand.Rated(ReglaPosition.BELOW, BigDecimal("1.09")),
            counterOffers = mx.kompara.metrics.CounterOffers(203, 249))
        compose.setContent { KomparaTheme { Regla(model, compact = compact) } }
        for (isCompact in listOf(false, true)) {
            compose.runOnIdle { compact = isCompact }
            compose.onNodeWithContentDescription("Abajo de tu mínimo: 1.09 pesos por kilómetro, neto" +
                if (isCompact) "" else ". Desde 203 pesos alcanza tu mínimo; desde 249, tu meta.")
                .assertHasNoClickAction()
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
                .assertCountEquals(1)
            // The painted K, number, unit and tray must not add duplicate accessible announcements.
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
                .assertAll(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility))
        }
    }
}
