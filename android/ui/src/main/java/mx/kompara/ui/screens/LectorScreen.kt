package mx.kompara.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
 * The Lector tab: live state of the accessibility reader. ON → drive-ready guidance + simulator
 * shortcut; OFF → one tap to the system Accessibility settings (we never toggle it ourselves —
 * read-only legal/Play posture, the driver flips the switch).
 *
 * Once the accessibility reader is on, the tab also controls the screen-capture (OCR) reader that
 * DiDi needs (B-075): live activo/detenido state plus an iniciar/reiniciar button, behind a
 * prominent-disclosure dialog (Play policy) that hands off to the system consent.
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(
                    color = if (connected) VerdictGreen else MaterialTheme.colorScheme.surfaceVariant,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (connected) Icons.Filled.CheckCircle else Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = if (connected) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(if (connected) R.string.lector_on_title else R.string.lector_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(if (connected) R.string.lector_on_body else R.string.lector_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(28.dp))
        if (connected) {
            PrimaryButton(
                text = stringResource(R.string.lector_on_cta),
                onClick = onOpenSimulator,
            )
            TextButton(onClick = onOpenAccessibilitySettings) {
                Text(text = stringResource(R.string.lector_settings_cta))
            }

            Spacer(modifier = Modifier.height(24.dp))
            ScreenReaderSection(
                running = ocrRunning,
                onStart = { showDisclosure = true },
            )
        } else {
            PrimaryButton(
                text = stringResource(R.string.lector_empty_cta),
                onClick = onOpenAccessibilitySettings,
            )
        }
    }
}

/** Screen-capture (OCR) reader controls for DiDi (B-075): status dot + start/restart. */
@Composable
private fun ScreenReaderSection(
    running: Boolean,
    onStart: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.lector_ocr_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        color = if (running) VerdictGreen else MaterialTheme.colorScheme.outline,
                        shape = CircleShape,
                    ),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = stringResource(
                    if (running) R.string.lector_ocr_running else R.string.lector_ocr_stopped,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onStart) {
            Text(
                text = stringResource(
                    if (running) R.string.lector_ocr_restart_cta else R.string.lector_ocr_start_cta,
                ),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LectorContentOnPreview() {
    KomparaTheme {
        LectorContent(
            connected = true,
            ocrRunning = false,
            onOpenAccessibilitySettings = {},
            onOpenSimulator = {},
            onStartScreenReader = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LectorContentOffPreview() {
    KomparaTheme {
        LectorContent(
            connected = false,
            ocrRunning = false,
            onOpenAccessibilitySettings = {},
            onOpenSimulator = {},
            onStartScreenReader = {},
        )
    }
}
