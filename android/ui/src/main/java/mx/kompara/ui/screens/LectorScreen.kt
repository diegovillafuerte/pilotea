package mx.kompara.ui.screens

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
 */
@HiltViewModel
class LectorViewModel @Inject constructor(
    serviceStatus: ServiceStatusProvider,
) : ViewModel() {
    val connected: StateFlow<Boolean> = serviceStatus.connected
}

@Composable
fun LectorScreen(
    modifier: Modifier = Modifier,
    onOpenSimulator: () -> Unit = {},
    viewModel: LectorViewModel = hiltViewModel(),
) {
    val connected by viewModel.connected.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LectorContent(
        connected = connected,
        onOpenAccessibilitySettings = { AccessibilitySettings.open(context) },
        onOpenSimulator = onOpenSimulator,
        modifier = modifier,
    )
}

@Composable
internal fun LectorContent(
    connected: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenSimulator: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
        } else {
            PrimaryButton(
                text = stringResource(R.string.lector_empty_cta),
                onClick = onOpenAccessibilitySettings,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LectorContentOnPreview() {
    KomparaTheme {
        LectorContent(connected = true, onOpenAccessibilitySettings = {}, onOpenSimulator = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun LectorContentOffPreview() {
    KomparaTheme {
        LectorContent(connected = false, onOpenAccessibilitySettings = {}, onOpenSimulator = {})
    }
}
