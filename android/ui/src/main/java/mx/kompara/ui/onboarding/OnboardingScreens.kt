package mx.kompara.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import kotlinx.coroutines.launch
import mx.kompara.ui.R
import mx.kompara.ui.components.PrimaryButton
import mx.kompara.ui.theme.KomparaTheme

/**
 * The onboarding funnel screens (B-036). Each is a plain stateless composable taking callbacks so
 * the navigation graph ([OnboardingNavGraph]) owns routing and the screens stay previewable.
 */

// ---------------------------------------------------------------------------
// 1. Value pitch (3-page pager)
// ---------------------------------------------------------------------------

private data class PitchPage(
    val icon: ImageVector,
    @param:StringRes val titleRes: Int,
    @param:StringRes val bodyRes: Int,
)

private val pitchPages = listOf(
    PitchPage(Icons.Filled.PlayArrow, R.string.onb_pitch_1_titulo, R.string.onb_pitch_1_cuerpo),
    PitchPage(Icons.Filled.CheckCircle, R.string.onb_pitch_2_titulo, R.string.onb_pitch_2_cuerpo),
    PitchPage(Icons.Filled.Lock, R.string.onb_pitch_3_titulo, R.string.onb_pitch_3_cuerpo),
)

@Composable
fun PitchScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { pitchPages.size })
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == pitchPages.lastIndex

    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { page ->
            val p = pitchPages[page]
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = p.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(88.dp),
                )
                Spacer(Modifier.height(28.dp))
                Text(
                    text = stringResource(p.titleRes),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(p.bodyRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        PagerDots(count = pitchPages.size, selected = pagerState.currentPage)
        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            text = if (isLast) {
                stringResource(R.string.onb_pitch_cta)
            } else {
                stringResource(R.string.onb_continuar)
            },
            onClick = {
                if (isLast) {
                    onFinished()
                } else {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }
            },
        )
    }
}

@Composable
private fun PagerDots(count: Int, selected: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(count) { i ->
            val active = i == selected
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (active) 10.dp else 8.dp)
                    .background(
                        color = if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        shape = CircleShape,
                    ),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 2. Prominent disclosure
// ---------------------------------------------------------------------------

@Composable
fun DisclosureScreen(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.onb_disc_titulo),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.onb_disc_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DisclosureSection(R.string.onb_disc_que_titulo, R.string.onb_disc_que_cuerpo)
            DisclosureSection(R.string.onb_disc_porque_titulo, R.string.onb_disc_porque_cuerpo)
            DisclosureSection(R.string.onb_disc_donde_titulo, R.string.onb_disc_donde_cuerpo)
            DisclosureSection(
                R.string.onb_disc_riesgo_titulo,
                R.string.onb_disc_riesgo_cuerpo,
                emphasize = true,
            )
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(text = stringResource(R.string.onb_disc_aceptar), onClick = onAccept)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onDecline, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onb_disc_rechazar))
        }
    }
}

@Composable
private fun DisclosureSection(
    @StringRes titleRes: Int,
    @StringRes bodyRes: Int,
    emphasize: Boolean = false,
) {
    Spacer(Modifier.height(20.dp))
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = if (emphasize) {
            MaterialTheme.colorScheme.secondary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(bodyRes),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ---------------------------------------------------------------------------
// 2b. Limited info screen (after decline)
// ---------------------------------------------------------------------------

@Composable
fun LimitedInfoScreen(
    onReview: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.onb_limit_titulo),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onb_limit_cuerpo),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        PrimaryButton(text = stringResource(R.string.onb_limit_revisar), onClick = onReview)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onb_limit_continuar))
        }
    }
}

// ---------------------------------------------------------------------------
// 3. Accessibility grant walkthrough
// ---------------------------------------------------------------------------

@Composable
fun AccessibilityGrantScreen(
    connected: Boolean,
    onOpenSettings: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (connected) {
        AccessibilityGrantedContent(onContinue = onContinue, modifier = modifier)
        return
    }
    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(
                text = stringResource(R.string.onb_acc_titulo),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.onb_acc_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            StepLine(R.string.onb_acc_paso_1)
            StepLine(R.string.onb_acc_paso_2)
            StepLine(R.string.onb_acc_paso_3)
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    text = stringResource(R.string.onb_acc_esperando),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(text = stringResource(R.string.onb_acc_boton), onClick = onOpenSettings)
    }
}

@Composable
private fun AccessibilityGrantedContent(onContinue: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(96.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.onb_acc_listo_titulo),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onb_acc_listo_cuerpo),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        PrimaryButton(text = stringResource(R.string.onb_continuar), onClick = onContinue)
    }
}

@Composable
private fun StepLine(@StringRes textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

// ---------------------------------------------------------------------------
// 4. OEM survival kit
// ---------------------------------------------------------------------------

@Composable
fun OemSurvivalScreen(
    profile: OemProfile,
    onOpenBattery: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val steps = OemSteps.stepsFor(profile)
    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(
                text = stringResource(R.string.onb_oem_titulo),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.onb_oem_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            steps.forEach { StepLine(it) }
            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .padding(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.onb_oem_recientes_tip),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onOpenBattery, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onb_oem_bateria_boton))
        }
        Spacer(Modifier.height(8.dp))
        PrimaryButton(text = stringResource(R.string.onb_oem_boton_continuar), onClick = onContinue)
    }
}

// ---------------------------------------------------------------------------
// 5. Done / ready
// ---------------------------------------------------------------------------

@Composable
fun OnboardingDoneScreen(
    showNotificationPrompt: Boolean,
    onRequestNotifications: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(96.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.onb_done_titulo),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onb_done_cuerpo),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (showNotificationPrompt) {
            Spacer(Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .padding(16.dp),
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.onb_done_notif_titulo),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.onb_done_notif_cuerpo),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onRequestNotifications,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.onb_done_notif_boton))
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton(text = stringResource(R.string.onb_done_cta), onClick = onFinish)
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(showBackground = true, name = "Onboarding — pitch")
@Composable
private fun PitchScreenPreview() {
    KomparaTheme { PitchScreen(onFinished = {}) }
}

@Preview(showBackground = true, name = "Onboarding — disclosure")
@Composable
private fun DisclosureScreenPreview() {
    KomparaTheme { DisclosureScreen(onAccept = {}, onDecline = {}) }
}

@Preview(showBackground = true, name = "Onboarding — accessibility")
@Composable
private fun AccessibilityScreenPreview() {
    KomparaTheme {
        AccessibilityGrantScreen(connected = false, onOpenSettings = {}, onContinue = {})
    }
}

@Preview(showBackground = true, name = "Onboarding — OEM Xiaomi")
@Composable
private fun OemScreenPreview() {
    KomparaTheme {
        OemSurvivalScreen(profile = OemProfile.XIAOMI, onOpenBattery = {}, onContinue = {})
    }
}

@Preview(showBackground = true, name = "Onboarding — done")
@Composable
private fun DoneScreenPreview() {
    KomparaTheme {
        OnboardingDoneScreen(
            showNotificationPrompt = true,
            onRequestNotifications = {},
            onFinish = {},
        )
    }
}
