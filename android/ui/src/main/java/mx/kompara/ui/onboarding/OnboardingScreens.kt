package mx.kompara.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import kotlinx.coroutines.launch
import mx.kompara.ui.R
import mx.kompara.ui.components.KomparaCard
import mx.kompara.ui.components.KomparaStatusChip
import mx.kompara.ui.components.KomparaSwitch
import mx.kompara.ui.components.PrimaryButton
import mx.kompara.ui.components.StatusLevel
import mx.kompara.ui.theme.KomparaTheme

/**
 * The onboarding funnel screens (B-036). Each is a plain stateless composable taking callbacks so
 * the navigation graph ([OnboardingNavGraph]) owns routing and the screens stay previewable.
 */

// ---------------------------------------------------------------------------
// 1. Value pitch (3-page pager)
// ---------------------------------------------------------------------------

private data class PitchPage(
    @param:StringRes val titleRes: Int,
    @param:StringRes val bodyRes: Int,
)

private val pitchPages = listOf(
    PitchPage(R.string.onb_pitch_1_titulo, R.string.onb_pitch_1_cuerpo),
    PitchPage(R.string.onb_pitch_2_titulo, R.string.onb_pitch_2_cuerpo),
    PitchPage(R.string.onb_pitch_3_titulo, R.string.onb_pitch_3_cuerpo),
)

/**
 * The brand "K" logomark hero tile: a rounded-rect filled brand-emerald, holding the white logomark.
 * Replaces the green-tinted Material vector heroes on the pitch + done screens (design `.lm` / `.lm.big`).
 * Default 64 dp tile (the done/celebration hero passes 80 dp).
 */
@Composable
private fun LogomarkTile(modifier: Modifier = Modifier, size: Dp = 64.dp) {
    val cornerRadius = if (size >= 80.dp) 20.dp else 16.dp
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_kompara_logomark),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.53f),
        )
    }
}

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
                LogomarkTile()
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
) {
    // Mock `.sec`: a tonal no-shadow card (radius 12, pad 14, top margin 12). KomparaCard gives the
    // tonal container; the top padding spaces the four sections, so no leading Spacer is needed.
    KomparaCard(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                // Section titles read in text-strong; verdict colours stay reserved for verdicts.
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(bodyRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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
        LogomarkTile()
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
            Spacer(Modifier.height(6.dp))
            StepLine(1, R.string.onb_acc_paso_1)
            StepLine(2, R.string.onb_acc_paso_2)
            StepLine(3, R.string.onb_acc_paso_3)
            ReaderStatusCard(active = false, modifier = Modifier.padding(top = 18.dp))
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(text = stringResource(R.string.onb_acc_boton), onClick = onOpenSettings)
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.onb_acc_esperando),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The mock `.statuscard`: a tonal row reporting the reader (accessibility) state — "Lector de
 * pantalla" with a status chip. Waiting uses a NEUTRAL chip; active uses a brand-emerald chip
 * (NOT VerdictGreen — "Activo" is a brand status, not a semáforo verdict), so it never reuses
 * StatusLevel.OK.
 */
@Composable
private fun ReaderStatusCard(active: Boolean, modifier: Modifier = Modifier) {
    KomparaCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.onb_acc_estado_label),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (active) {
                BrandStatusChip(label = stringResource(R.string.onb_acc_estado_activo))
            } else {
                KomparaStatusChip(
                    label = stringResource(R.string.onb_acc_estado_esperando),
                    level = StatusLevel.NEUTRAL,
                )
            }
        }
    }
}

/**
 * A brand-emerald status pill for non-verdict "Activo" states. Mirrors [KomparaStatusChip]'s shape
 * but tints from colorScheme.primary (brand) instead of a verdict colour, honoring the standing rule
 * that verde/amarillo/rojo are verdicts only.
 */
@Composable
private fun BrandStatusChip(label: String, modifier: Modifier = Modifier) {
    val brand = MaterialTheme.colorScheme.primary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(brand.copy(alpha = 0.12f))
            .border(BorderStroke(1.dp, brand.copy(alpha = 0.35f)), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(brand),
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = label,
            color = brand,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun AccessibilityGrantedContent(onContinue: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        LogomarkTile(size = 80.dp)
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
        Spacer(Modifier.height(20.dp))
        ReaderStatusCard(active = true)
        Spacer(Modifier.weight(1f))
        PrimaryButton(text = stringResource(R.string.onb_continuar), onClick = onContinue)
    }
}

/**
 * A numbered walkthrough step (mock `.stepline` / `.stepn`): a 26 dp round brand-emerald pill holding
 * the white step [number] and the body copy beside it. The number lives in the badge, not the string,
 * so step copy never bakes in its own "N." prefix. The pill is decorative-on-brand (colorScheme.primary)
 * — NOT a verdict colour.
 */
@Composable
private fun StepLine(number: Int, @StringRes textRes: Int) {
    val stepText = stringResource(textRes)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Paso $number. $stepText" },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        Text(
            text = stringResource(textRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
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
            Spacer(Modifier.height(6.dp))
            steps.forEachIndexed { i, res -> StepLine(i + 1, res) }
            // Mock `.isOem` `.statuscard`: a tonal row with a title + subtitle and an inline brand
            // switch for the "reader went down" notifications. Default ON, mirroring the mock.
            var notifyOnDown by remember { mutableStateOf(true) }
            val notifLabel = stringResource(R.string.onb_oem_notif_titulo)
            KomparaCard(modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = notifLabel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.onb_oem_notif_cuerpo),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    KomparaSwitch(
                        checked = notifyOnDown,
                        onCheckedChange = { notifyOnDown = it },
                        modifier = Modifier.semantics { contentDescription = notifLabel },
                    )
                }
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
        LogomarkTile(size = 80.dp)
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
