package mx.kompara.ui.paywall

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.ui.R
import mx.kompara.ui.components.PrimaryButton
import mx.kompara.ui.theme.KomparaTheme

/**
 * The paywall screen (route `paywall`, B-050): driver-math value framing, the premium feature list, a
 * trial CTA wired to the Play billing flow (price + trial copy from Play at runtime), a "Restaurar
 * compras" link, and a legal fine-print placeholder.
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.paywall_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.paywall_value_framing),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        FeatureList()

        Spacer(Modifier.height(4.dp))

        when (state) {
            PaywallUiState.Loading -> {
                Text(
                    text = stringResource(R.string.paywall_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is PaywallUiState.Ready -> {
                val ctaText = if (state.hasFreeTrial) {
                    stringResource(R.string.paywall_cta_trial)
                } else {
                    stringResource(R.string.paywall_cta_subscribe)
                }
                PrimaryButton(text = ctaText, onClick = onStartTrial)
                Text(
                    text = if (state.hasFreeTrial) {
                        stringResource(R.string.paywall_price_with_trial, state.formattedPrice)
                    } else {
                        stringResource(R.string.paywall_price_no_trial, state.formattedPrice)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PaywallUiState.PlayUnavailable -> {
                Text(
                    text = stringResource(R.string.paywall_play_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        TextButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.paywall_restore))
        }

        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.paywall_maybe_later))
        }

        // TODO(legal-B038): replace with reviewed subscription terms + auto-renewal + cancellation +
        // privacy fine print on the same counsel cadence as the B-036 disclosure copy.
        Text(
            text = stringResource(R.string.paywall_legal_placeholder),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(0.dp))
                Text(
                    text = stringResource(res),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 12.dp),
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
