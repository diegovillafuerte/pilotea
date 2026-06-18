package mx.kompara.ui.paywall

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.ui.R
import mx.kompara.ui.components.ButtonSize
import mx.kompara.ui.components.ButtonVariant
import mx.kompara.ui.components.KomparaButton
import mx.kompara.ui.components.PrimaryButton
import mx.kompara.ui.theme.KomparaTheme

/**
 * The paywall screen (route `paywall`, B-050): driver-math value framing, the premium feature list, a
 * trial CTA wired to the Play billing flow (price + trial copy from Play at runtime), a "Restaurar
 * compras" link, and a legal fine-print placeholder.
 *
 * Layout follows the V1.0 mock: a top close (✕) affordance, a scrollable body (title, value framing,
 * feature list, emerald price card) and a fixed footer pinning the CTA + Restaurar + legal line so the
 * driver never has to scroll to reach the action.
 *
 * Opened from a [PaywallGate] CTA on a specific [surface] (or [GateSurface.GENERIC] from a generic
 * entry point), which is used purely to bucket the conversion analytics.
 */
@Composable
fun PaywallScreen(
    surface: GateSurface = GateSurface.GENERIC,
    onClose: () -> Unit = {},
    viewModel: PaywallViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(surface) { viewModel.onOpened(surface) }

    val msgText = message?.toText()
    LaunchedEffect(message) {
        if (msgText != null) {
            snackbarHostState.showSnackbar(msgText)
            viewModel.consumeMessage()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        PaywallContent(
            state = state,
            modifier = Modifier.padding(padding),
            onStartTrial = { activity?.let { viewModel.startTrial(it, surface) } },
            onRestore = viewModel::restore,
            onClose = onClose,
        )
    }
}

@Composable
private fun PaywallContent(
    state: PaywallUiState,
    modifier: Modifier = Modifier,
    onStartTrial: () -> Unit = {},
    onRestore: () -> Unit = {},
    onClose: () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize()) {
        // (1) Top bar: only the trailing close ✕ — it owns dismissal (no separate "Quizá después").
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.paywall_close),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // (2) Scrollable body: title, value framing, features, and (in Ready state) the price card.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.paywall_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.paywall_value_framing),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FeatureList()

            when (state) {
                PaywallUiState.Loading -> {
                    Text(
                        text = stringResource(R.string.paywall_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is PaywallUiState.Ready -> PriceCard(state)
                PaywallUiState.PlayUnavailable -> {
                    Text(
                        text = stringResource(R.string.paywall_play_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        // (3) Fixed footer: the CTA, Restaurar and legal line, pinned to the bottom (outside scroll).
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state is PaywallUiState.Ready) {
                val ctaText = if (state.hasFreeTrial) {
                    stringResource(R.string.paywall_cta_trial)
                } else {
                    stringResource(R.string.paywall_cta_subscribe)
                }
                PrimaryButton(text = ctaText, onClick = onStartTrial)
            }

            KomparaButton(
                text = stringResource(R.string.paywall_restore),
                onClick = onRestore,
                variant = ButtonVariant.TEXT,
                size = ButtonSize.SM,
                fullWidth = true,
            )

            // TODO(legal-B038): replace with reviewed subscription terms + auto-renewal + cancellation +
            // privacy fine print on the same counsel cadence as the B-036 disclosure copy.
            Text(
                text = stringResource(R.string.paywall_legal_placeholder),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The emerald price card (mock): a primary-tinted surface (12% fill, 45% border, radius 12) showing
 * the "Premium" label, the live Play price + "/mes" suffix, and the trial / no-trial sub-line.
 * The accent is BrandGreen / colorScheme.primary — NOT a verdict colour.
 */
@Composable
private fun PriceCard(state: PaywallUiState.Ready) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.paywall_price_card_label),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontSize = 22.sp, fontWeight = FontWeight.Black)) {
                            append(state.formattedPrice)
                        }
                        withStyle(
                            SpanStyle(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        ) {
                            append(stringResource(R.string.paywall_price_per_month))
                        }
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = if (state.hasFreeTrial) {
                    stringResource(R.string.paywall_subline_trial)
                } else {
                    stringResource(R.string.paywall_subline_no_trial)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun FeatureList() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            R.string.paywall_feature_percentiles,
            R.string.paywall_feature_compare,
            R.string.paywall_feature_history,
            R.string.paywall_feature_fiscal,
        ).forEach { res ->
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = stringResource(res),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun PaywallMessage.toText(): String = when (this) {
    PaywallMessage.Restored -> stringResource(R.string.paywall_msg_restored)
    PaywallMessage.NothingToRestore -> stringResource(R.string.paywall_msg_nothing_to_restore)
    PaywallMessage.AlreadyPremium -> stringResource(R.string.paywall_msg_already_premium)
    PaywallMessage.LaunchFailed -> stringResource(R.string.paywall_msg_launch_failed)
    PaywallMessage.PlayUnavailable -> stringResource(R.string.paywall_play_unavailable)
}

/** Walk the ContextWrapper chain to the hosting Activity (needed by the Play billing flow). */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Preview(showBackground = true, name = "Paywall — con prueba")
@Composable
private fun PaywallReadyPreview() {
    KomparaTheme {
        PaywallContent(
            state = PaywallUiState.Ready(
                formattedPrice = "$79.00",
                hasFreeTrial = true,
                product = mx.kompara.billing.SubscriptionProduct(
                    productId = "kompara_premium",
                    basePlanId = "monthly",
                    offerId = "trial-7d",
                    offerToken = "tok",
                    formattedPrice = "$79.00",
                    priceCurrencyCode = "MXN",
                    priceAmountMicros = 79_000_000,
                    hasFreeTrial = true,
                ),
            ),
        )
    }
}

@Preview(showBackground = true, name = "Paywall — Play no disponible")
@Composable
private fun PaywallUnavailablePreview() {
    KomparaTheme { PaywallContent(state = PaywallUiState.PlayUnavailable) }
}
