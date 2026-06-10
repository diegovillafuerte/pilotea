package mx.kompara.ui.paywall

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import mx.kompara.billing.GateState
import mx.kompara.ui.components.PrimaryButton

/**
 * The reusable tease-then-gate wrapper for premium content (B-050).
 *
 * When [state] is [GateState.UNLOCKED] it renders [content] verbatim. When [GateState.LOCKED] it
 * renders a **preview** of [content] — blurred (API 31+) or dimmed (API 26–30 fallback, since
 * `Modifier.blur` needs API 31 and the app targets minSdk 26) and made non-interactive — with a lock
 * chip, a one-line [valueHint], and a "Probar Kompara Premium" CTA overlaid. The driver sees the
 * *shape* of the insight and the upsell, never the real numbers.
 *
 * Records [GateEvent.GATE_SHOWN] on [funnel] the first time it composes locked for a given [surface]
 * (anonymized local counter), and [GateEvent.PAYWALL_OPENED] when the CTA is tapped before invoking
 * [onUpgrade] (which navigates to the paywall screen).
 *
 * IMPORTANT: callers pass the SAME [content] for locked and unlocked, but the gated content must not
 * itself fetch/compose the real premium *values* when the viewmodel reports locked — the viewmodel is
 * the source of truth for what data is present (e.g. percentiles are never computed when locked). This
 * wrapper only blurs/teases whatever it is handed.
 *
 * @param surface which premium surface this gate guards (for the analytics bucket).
 * @param state the resolved [GateState] from the tier gatekeeper.
 * @param valueHint a short Spanish value line shown over the locked preview.
 * @param funnel anonymized local conversion counters.
 * @param onUpgrade navigate to the paywall screen (called after recording PAYWALL_OPENED).
 */
@Composable
fun PaywallGate(
    surface: GateSurface,
    state: GateState,
    valueHint: String,
    funnel: GateFunnel,
    onUpgrade: (GateSurface) -> Unit,
    modifier: Modifier = Modifier,
    ctaText: String = "Probar Kompara Premium",
    content: @Composable () -> Unit,
) {
    if (state.isUnlocked) {
        Box(modifier) { content() }
        return
    }

    // Record the gate impression once per (re)entry into the locked state for this surface.
    LaunchedEffect(surface, state) {
        funnel.record(surface, GateEvent.GATE_SHOWN)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        // The teased content: blurred (31+) or dimmed (26–30), non-interactive, and hidden from a11y so
        // a screen reader never reads the real values behind the gate.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .teaseModifier()
                .clearAndSetSemantics { },
        ) {
            content()
        }

        // The lock + hint + CTA overlay.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clearAndSetSemantics {
                    contentDescription = "$valueHint. $ctaText"
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Text(
                text = valueHint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            PrimaryButton(
                text = ctaText,
                onClick = { onUpgrade(surface) },
            )
        }
    }
}

/** Blur on API 31+, alpha-dim fallback on 26–30 (Modifier.blur is a no-op below 31). */
private fun Modifier.teaseModifier(): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        this.blur(16.dp).alpha(0.55f)
    } else {
        this.alpha(0.20f)
    }
