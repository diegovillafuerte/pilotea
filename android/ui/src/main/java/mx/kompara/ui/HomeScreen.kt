package mx.kompara.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.data.settings.Settings

/**
 * The reader's home screen. Hilt-injects [HomeViewModel] and reflects the persisted settings.
 * Intentionally minimal; full screens arrive in later UI tasks.
 */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle(initialValue = Settings.DEFAULT)
    Column(modifier = modifier.padding(16.dp)) {
        Text(text = "Kompara", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = HomeUiText.activePlatformsLabel(settings),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
