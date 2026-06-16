package mx.kompara.ui.screens

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import mx.kompara.data.service.ScreenReaderState
import mx.kompara.data.service.ServiceStatusProvider
import mx.kompara.ui.R
import mx.kompara.ui.components.PrimaryButton
import mx.kompara.ui.onboarding.AccessibilitySettings
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.VerdictGreen
import javax.inject.Inject

/**
 * The Lector tab. OFF → one tap to the system Accessibility settings (we never toggle it ourselves
 * — read-only legal/Play posture, the driver flips the switch). ON → a clean two-action layout: the
 * live screen-reader status plus the two controls the driver actually uses —
 *  - "Iniciar/Reiniciar lector de pantalla" — the screen-capture (OCR) reader (B-075) that Android
 *    kills on every screen lock; the primary action, behind the prominent-disclosure dialog.
 *  - "Configuración de accesibilidad" — manage the permission that powers the reader.
 * Uber and DiDi offers are read the same way (screen capture), so there is no per-app split. Below
 * sit the secondary shortcuts (simulator + "Tu semáforo").
 */
@HiltViewModel
class LectorViewModel @Inject constructor(
    serviceStatus: ServiceStatusProvider,
) : ViewModel() {
    val connected: StateFlow<Boolean> = serviceStatus.connected

    /** Live OCR (screen-capture) reader state — written by `:ocr`'s capture service (B-075). */
    val ocrRunning: StateFlow<Boolean> = ScreenReaderState.running
}

@Composable
fun LectorScreen(
    modifier: Modifier = Modifier,
    onOpenSimulator: () -> Unit = {},
    onOpenThresholds: () -> Unit = {},
    viewModel: LectorViewModel = hiltViewModel(),
) {
    val connected by viewModel.connected.collectAsStateWithLifecycle()
    val ocrRunning by viewModel.ocrRunning.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LectorContent(
        connected = connected,
        ocrRunning = ocrRunning,
        onOpenAccessibilitySettings = { AccessibilitySettings.open(context) },
        onOpenSimulator = onOpenSimulator,
        onOpenThresholds = onOpenThresholds,
        onStartScreenReader = { startScreenReader(context) },
        modifier = modifier,
    )
}

/** Launch the screen-capture consent flow (OcrConsentActivity in `:app`, via the package-internal
 *  action so `:ui` never depends on `:app`). */
private fun startScreenReader(context: Context) {
    context.startActivity(
        Intent(ScreenReaderState.ACTION_START)
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

@Composable
internal fun LectorContent(
    connected: Boolean,
    ocrRunning: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenSimulator: () -> Unit,
    onOpenThresholds: () -> Unit,
    onStartScreenReader: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Play-required prominent disclosure before any screen-capture consent (B-075/B-065).
    var showDisclosure by remember { mutableStateOf(false) }
    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = { showDisclosure = false },
            title = { Text(stringResource(R.string.lector_ocr_disclosure_title)) },
            text = { Text(stringResource(R.string.lector_ocr_disclosure_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDisclosure = false
                    onStartScreenReader()
                }) { Text(stringResource(R.string.lector_ocr_disclosure_accept)) }
            },
            dismissButton = {
                TextButton(onClick = { showDisclosure = false }) {
                    Text(stringResource(R.string.lector_ocr_disclosure_cancel))
                }
            },
        )
    }

    if (connected) {
        ConnectedLector(
            ocrRunning = ocrRunning,
            onOpenAccessibilitySettings = onOpenAccessibilitySettings,
            onOpenSimulator = onOpenSimulator,
            onOpenThresholds = onOpenThresholds,
            onStartScreenReader = { showDisclosure = true },
            modifier = modifier,
        )
    } else {
        DisconnectedLector(
            onOpenAccessibilitySettings = onOpenAccessibilitySettings,
            modifier = modifier,
        )
    }
}

/** Reader off: a single clear call to grant the accessibility service. */
@Composable
private fun DisconnectedLector(
    onOpenAccessibilitySettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.lector_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.lector_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        PrimaryButton(
            text = stringResource(R.string.lector_empty_cta),
            onClick = onOpenAccessibilitySettings,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.lector_empty_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Reader on: the live screen-reader status, the two reader actions, then the secondary shortcuts.
 *  Uber and DiDi offers are read the same way (screen capture), so there is no per-app split. */
@Composable
private fun ConnectedLector(
    ocrRunning: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenSimulator: () -> Unit,
    onOpenThresholds: () -> Unit,
    onStartScreenReader: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.lector_header),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.lector_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // The one live state the driver acts on: the screen-capture reader, which Android kills on
        // every screen lock. The accessibility service is already granted in this (connected) branch.
        ReaderStatusCard(running = ocrRunning)

        // Primary action: start / restart the screen reader (behind the prominent disclosure).
        PrimaryButton(
            text = stringResource(
                if (ocrRunning) R.string.lector_ocr_restart_cta else R.string.lector_ocr_start_cta,
            ),
            onClick = onStartScreenReader,
        )
        // Secondary action: manage the accessibility permission that powers the reader.
        OutlinedButton(
            onClick = onOpenAccessibilitySettings,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.lector_settings_cta))
        }

        Spacer(Modifier.height(4.dp))
        OutlinedButton(
            onClick = onOpenSimulator,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.lector_on_cta))
        }
        OutlinedButton(
            onClick = onOpenThresholds,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.lector_semaforo_cta))
        }
    }
}

/**
 * The screen-capture reader's live status (Activo/Detenido), kept compact (design-principles.md §7):
 * a status dot + label on a single row. It's the one reader state the driver acts on, since Android
 * kills the capture on every screen lock.
 */
@Composable
private fun ReaderStatusCard(running: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = if (running) VerdictGreen else MaterialTheme.colorScheme.outline,
                            shape = CircleShape,
                        ),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.lector_screen_reader_label),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = stringResource(
                    if (running) R.string.lector_status_on else R.string.lector_ocr_status_off,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = if (running) VerdictGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LectorConnectedRunningPreview() {
    KomparaTheme {
        LectorContent(
            connected = true,
            ocrRunning = true,
            onOpenAccessibilitySettings = {},
            onOpenSimulator = {},
            onOpenThresholds = {},
            onStartScreenReader = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LectorConnectedStoppedPreview() {
    KomparaTheme {
        LectorContent(
            connected = true,
            ocrRunning = false,
            onOpenAccessibilitySettings = {},
            onOpenSimulator = {},
            onOpenThresholds = {},
            onStartScreenReader = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LectorOffPreview() {
    KomparaTheme {
        LectorContent(
            connected = false,
            ocrRunning = false,
            onOpenAccessibilitySettings = {},
            onOpenSimulator = {},
            onOpenThresholds = {},
            onStartScreenReader = {},
        )
    }
}
