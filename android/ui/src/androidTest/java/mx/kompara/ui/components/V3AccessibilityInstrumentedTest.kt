package mx.kompara.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import mx.kompara.ui.theme.KomparaTheme
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

/** Native Compose bounds and accessibility contract; this does not certify TalkBack speech. */
@RunWith(AndroidJUnit4::class)
class V3AccessibilityInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun shortMonthTabRetainsItsTouchTargetAtEveryTextSize() {
        var selected by mutableIntStateOf(0)
        var fontScale by mutableFloatStateOf(1f)
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                KomparaTheme {
                    TextTabs(listOf("Semana", "Mes"), selected, { selected = it }, Modifier.widthIn(max = 320.dp))
                }
            }
        }
        for (scale in listOf(1f, 1.3f, 2f)) {
            compose.runOnIdle { fontScale = scale }
            val week = compose.onNodeWithText("Semana")
            val month = compose.onNodeWithText("Mes")
            week.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            month.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            assertFalse("Tab targets overlap at $scale",
                week.fetchSemanticsNode().boundsInRoot.overlaps(month.fetchSemanticsNode().boundsInRoot))
            month.performTouchInput { click() }.assertIsSelected()
            week.performTouchInput { click() }.assertIsSelected()
        }
    }

    @Test fun selectedPeriodIsExposedBeforePreviousAndNextSteps() {
        compose.setContent {
            KomparaTheme { PeriodSelector("12 – 18 oct", PeriodUnit.WEEK, {}, {}, {}, true, false) }
        }
        compose.onNodeWithText("12 – 18 oct").assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 0f))
        compose.onNodeWithText("Semana anterior").assertIsEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 1f))
        compose.onNodeWithText("Semana siguiente").assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 2f))
    }

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
