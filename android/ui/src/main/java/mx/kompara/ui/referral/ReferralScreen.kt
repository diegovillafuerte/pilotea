package mx.kompara.ui.referral

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.ui.R
import mx.kompara.ui.components.PrimaryButton

/**
 * The B-056 "Invita y gana" screen. Renders [ReferralViewModel]'s [ReferralUiState]: signed-out gate →
 * the driver's big code + copy/WhatsApp share + stats, plus an inline "¿te recomendaron?" redeem field
 * with Spanish error/success states.
 *
 * Android side-effects (clipboard, WhatsApp share intent) live here so the ViewModel stays free of the
 * Android framework and unit-testable (mirrors B-045's ImportScreen).
 *
 * @param onClose pop back (signed-out "Volver" and the top "cerrar").
 */
@Composable
fun ReferralScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReferralViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        when (val s = state) {
            ReferralUiState.Loading -> CenteredProgress()
            ReferralUiState.SignedOut -> SignedOutState(onBack = onClose)
            is ReferralUiState.LoadError -> LoadErrorState(message = s.message, onRetry = viewModel::load)
            is ReferralUiState.Ready -> ReadyState(
                state = s,
                onCopy = { copyToClipboard(context, s.code) },
                onShareWhatsApp = { shareViaWhatsApp(context, s.code) },
                onRedeemInputChange = viewModel::onRedeemInputChange,
                onRedeem = viewModel::redeem,
            )
        }
    }
}

@Composable
private fun CenteredProgress() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun SignedOutState(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.referral_signed_out_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.referral_signed_out_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        PrimaryButton(text = stringResource(R.string.referral_signed_out_back), onClick = onBack)
    }
}

@Composable
private fun LoadErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        PrimaryButton(text = stringResource(R.string.referral_retry), onClick = onRetry)
    }
}

@Composable
private fun ReadyState(
    state: ReferralUiState.Ready,
    onCopy: () -> Unit,
    onShareWhatsApp: () -> Unit,
    onRedeemInputChange: (String) -> Unit,
    onRedeem: () -> Unit,
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text(
            text = stringResource(R.string.referral_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.referral_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        // Big code card.
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.referral_your_code),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.code,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onCopy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.referral_copy))
        }
        Spacer(Modifier.height(10.dp))
        PrimaryButton(text = stringResource(R.string.referral_share_whatsapp), onClick = onShareWhatsApp)

        Spacer(Modifier.height(24.dp))
        StatsRow(
            redemptionsCount = state.redemptionsCount,
            premiumDaysEarned = state.premiumDaysEarned,
        )

        Spacer(Modifier.height(28.dp))
        RedeemSection(
            input = state.redeemInput,
            phase = state.redeem,
            onInputChange = onRedeemInputChange,
            onRedeem = onRedeem,
        )
    }
}

@Composable
private fun StatsRow(redemptionsCount: Int, premiumDaysEarned: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        StatPill(
            value = redemptionsCount.toString(),
            label = stringResource(R.string.referral_stat_redemptions),
        )
        StatPill(
            value = premiumDaysEarned.toString(),
            label = stringResource(R.string.referral_stat_days_earned),
        )
    }
}

@Composable
private fun StatPill(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RedeemSection(
    input: String,
    phase: RedeemPhase,
    onInputChange: (String) -> Unit,
    onRedeem: () -> Unit,
) {
    Text(
        text = stringResource(R.string.referral_redeem_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.referral_redeem_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))

    val isSuccess = phase is RedeemPhase.Success
    OutlinedTextField(
        value = input,
        onValueChange = onInputChange,
        singleLine = true,
        enabled = phase !is RedeemPhase.Submitting && !isSuccess,
        isError = phase is RedeemPhase.Error,
        label = { Text(stringResource(R.string.referral_redeem_hint)) },
        modifier = Modifier.fillMaxWidth(),
    )

    when (phase) {
        is RedeemPhase.Error -> {
            Spacer(Modifier.height(8.dp))
            Text(
                text = phase.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        is RedeemPhase.Success -> {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.referral_redeem_success, phase.grantedDays),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        else -> Unit
    }

    Spacer(Modifier.height(12.dp))
    if (!isSuccess) {
        PrimaryButton(
            text = stringResource(R.string.referral_redeem_cta),
            onClick = onRedeem,
            enabled = phase !is RedeemPhase.Submitting && input.isNotBlank(),
        )
    }
}

// ─── Android side-effects ──────────────────────────────────────────────────────

/** Copy the code to the clipboard and toast a confirmation. */
private fun copyToClipboard(context: Context, code: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("kompara_referral", code))
    Toast.makeText(context, context.getString(R.string.referral_copied), Toast.LENGTH_SHORT).show()
}

/**
 * Share the invite via WhatsApp (falls back to the system chooser if WhatsApp isn't installed). The
 * message bundles the code + a Play link placeholder; a dynamic deep link from the share card is
 * pending (techdebt).
 */
private fun shareViaWhatsApp(context: Context, code: String) {
    val message = context.getString(R.string.referral_share_message, code)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, message)
        setPackage(WHATSAPP_PACKAGE)
    }
    val launch = runCatching { context.startActivity(intent) }
    if (launch.isFailure) {
        // WhatsApp not installed — fall back to the generic chooser.
        val chooser = Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
            },
            context.getString(R.string.referral_share_whatsapp),
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(chooser)
    }
}

private const val WHATSAPP_PACKAGE = "com.whatsapp"
