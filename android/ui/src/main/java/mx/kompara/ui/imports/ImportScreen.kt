package mx.kompara.ui.imports

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.sync.api.ImportFile
import mx.kompara.sync.api.ImportMetrics
import mx.kompara.ui.R
import mx.kompara.ui.components.ButtonVariant
import mx.kompara.ui.components.KomparaButton
import mx.kompara.ui.components.KomparaCard
import mx.kompara.ui.components.KomparaProgressBar
import mx.kompara.ui.components.PrimaryButton
import mx.kompara.ui.format.Formatters
import mx.kompara.ui.theme.KomparaType

/** MIME filter for the document picker — matches the backend's accepted set. */
private val IMPORT_MIME_FILTER = arrayOf("application/pdf", "image/png", "image/jpeg", "image/webp")

/**
 * The B-045 import flow screen. Renders the [ImportViewModel] state machine: signed-out gate →
 * platform picker → file pick → upload progress → dry-run review → saved / error. On a confirmed
 * import the parsed week is backfilled into local history (handled in the repository), so it appears
 * immediately with the Importado badge when the driver returns.
 *
 * File bytes are read off the content resolver here (the picker yields a [Uri]); the ViewModel and
 * repository only ever see in-memory [ImportFile]s, keeping them free of Android IO for tests.
 *
 * @param onClose pop back to History (success "Listo", signed-out "Volver", and error "Volver").
 */
@Composable
fun ImportScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ImportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        when (val s = state) {
            ImportUiState.Loading -> LoadingState()
            ImportUiState.SignedOut -> SignedOutState(onBack = onClose)
            is ImportUiState.Picking -> PickingState(
                state = s,
                onSelectPlatform = viewModel::selectPlatform,
                onChangePlatform = viewModel::restart,
                onFilesPicked = { platform, files -> viewModel.submitForReview(platform, files) },
                context = context,
            )
            is ImportUiState.SharedReady -> SharedReadyState(
                state = s,
                onConfirm = viewModel::confirmSharedImport,
                onChoosePlatform = viewModel::restart,
            )
            is ImportUiState.Uploading -> UploadingState(step = s.step)
            is ImportUiState.Review -> ReviewState(
                state = s,
                onSave = viewModel::confirm,
                onDiscard = viewModel::discard,
            )
            is ImportUiState.Saved -> SavedState(weekStart = s.weekStart, onDone = onClose)
            is ImportUiState.Error -> ErrorState(
                message = s.message,
                retryable = s.retryable,
                onRetry = viewModel::restart,
                onBack = onClose,
            )
        }
    }
}

// ─── States ─────────────────────────────────────────────────────────────────

@Composable
private fun LoadingState() {
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
            text = stringResource(R.string.import_signed_out_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.import_signed_out_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        PrimaryButton(text = stringResource(R.string.import_signed_out_back), onClick = onBack)
    }
}

/**
 * PR-D3: a file shared into Kompara from another app lands here pre-classified. We show what we
 * detected and require an explicit "Continuar" tap before any upload — the exported share entry must
 * never auto-spend the driver's authenticated parse quota.
 */
@Composable
private fun SharedReadyState(
    state: ImportUiState.SharedReady,
    onConfirm: () -> Unit,
    onChoosePlatform: () -> Unit,
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text(
            text = stringResource(R.string.import_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        KomparaCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.import_shared_ready_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        R.string.import_shared_ready_detected,
                        stringResource(platform = state.platform, title = true),
                        state.files.size,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        PrimaryButton(text = stringResource(R.string.import_shared_ready_confirm), onClick = onConfirm)
        Spacer(Modifier.height(12.dp))
        KomparaButton(
            text = stringResource(R.string.import_pick_another_platform),
            onClick = onChoosePlatform,
            variant = ButtonVariant.SECONDARY,
            fullWidth = true,
        )
    }
}

@Composable
private fun PickingState(
    state: ImportUiState.Picking,
    onSelectPlatform: (ImportPlatform) -> Unit,
    onChangePlatform: () -> Unit,
    onFilesPicked: (ImportPlatform, List<ImportFile>) -> Unit,
    context: Context,
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text(
            text = stringResource(R.string.import_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.import_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        val platform = state.platform
        if (platform == null) {
            Text(
                text = stringResource(R.string.import_pick_platform),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            for (option in ImportPlatform.entries) {
                PlatformCard(option = option, onClick = { onSelectPlatform(option) })
                Spacer(Modifier.height(10.dp))
            }
        } else {
            FilePickStep(
                platform = platform,
                onFilesReady = { files -> onFilesPicked(platform, files) },
                onChangePlatform = onChangePlatform, // back to the platform list
                context = context,
            )
        }
    }
}

@Composable
private fun PlatformCard(option: ImportPlatform, onClick: () -> Unit) {
    KomparaCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(platform = option, title = true),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(platform = option, title = false),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The file-pick step for the chosen [platform]. Single-file platforms launch one document picker;
 * DiDi collects two (ganancias + tablero) and only submits once both are picked. Bytes are read off
 * the resolver immediately so the ViewModel/repository never touch a [Uri].
 */
@Composable
private fun FilePickStep(
    platform: ImportPlatform,
    onFilesReady: (List<ImportFile>) -> Unit,
    onChangePlatform: () -> Unit,
    context: Context,
) {
    var first by remember(platform) { mutableStateOf<ImportFile?>(null) }
    var second by remember(platform) { mutableStateOf<ImportFile?>(null) }

    val launcher1 = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val file = uri?.let { readImportFile(context, it) }
        if (file != null) {
            first = file
            if (platform.fileCount == 1) onFilesReady(listOf(file))
            else if (second != null) onFilesReady(listOf(file, second!!))
        }
    }
    val launcher2 = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val file = uri?.let { readImportFile(context, it) }
        if (file != null) {
            second = file
            if (first != null) onFilesReady(listOf(first!!, file))
        }
    }

    Text(
        text = stringResource(platform = platform, title = true),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(platform = platform, title = false),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(20.dp))

    if (platform.fileCount == 2) {
        FilePickRow(
            label = stringResource(R.string.import_pick_file_1),
            picked = first != null,
            onClick = { launcher1.launch(IMPORT_MIME_FILTER) },
        )
        Spacer(Modifier.height(12.dp))
        FilePickRow(
            label = stringResource(R.string.import_pick_file_2),
            picked = second != null,
            onClick = { launcher2.launch(IMPORT_MIME_FILTER) },
        )
    } else {
        PrimaryButton(
            text = stringResource(R.string.import_pick_file),
            onClick = { launcher1.launch(IMPORT_MIME_FILTER) },
        )
    }

    Spacer(Modifier.height(20.dp))
    KomparaButton(
        text = stringResource(R.string.import_pick_another_platform),
        onClick = onChangePlatform,
        variant = ButtonVariant.SECONDARY,
        fullWidth = true,
    )
}

@Composable
private fun FilePickRow(label: String, picked: Boolean, onClick: () -> Unit) {
    Column {
        Text(text = label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        if (picked) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.import_file_selected),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        KomparaButton(
            text = stringResource(R.string.import_pick_file),
            onClick = onClick,
            variant = ButtonVariant.SECONDARY,
            fullWidth = true,
        )
    }
}

@Composable
private fun UploadingState(step: ImportProgressStep) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            text = stringResource(R.string.import_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.import_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        KomparaCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.import_uploading_label).uppercase(),
                    style = KomparaType.metricLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                for (s in ImportProgressStep.entries) {
                    StepRow(
                        label = stepLabel(s),
                        done = s.ordinal < step.ordinal,
                        active = s == step,
                    )
                }
                Spacer(Modifier.height(10.dp))
                KomparaProgressBar(
                    // +1 so the bar shows motion on the first active step (not an empty/stalled
                    // bar) and reaches full as the last step completes into Review.
                    progress = (step.ordinal + 1).toFloat() / ImportProgressStep.entries.size,
                    height = 8.dp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.import_progress_wait),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** One step row in the upload checklist: a check circle + label, dimmed until reached. */
@Composable
private fun StepRow(label: String, done: Boolean, active: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp)
            .alpha(if (active || done) 1f else 0.35f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CheckCircle(done = done)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 18dp circle: filled BrandGreen with a check when done, else an empty outlined ring. */
@Composable
private fun CheckCircle(done: Boolean) {
    val base = Modifier.size(18.dp).clip(CircleShape)
    Box(
        modifier = if (done) {
            base.background(MaterialTheme.colorScheme.primary)
        } else {
            base.border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
        },
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Composable
private fun ReviewState(
    state: ImportUiState.Review,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
) {
    val metrics = state.response.metrics
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text(
            text = stringResource(R.string.import_review_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.import_review_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        Text(
            text = Formatters.formatWeekLabel(metrics.weekStart),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(16.dp))

        MetricsSummary(metrics)

        Spacer(Modifier.height(20.dp))
        CompletenessBar(fraction = state.response.dataCompleteness)

        Spacer(Modifier.height(28.dp))
        PrimaryButton(text = stringResource(R.string.import_review_save), onClick = onSave)
        Spacer(Modifier.height(10.dp))
        KomparaButton(
            text = stringResource(R.string.import_review_discard),
            onClick = onDiscard,
            variant = ButtonVariant.SECONDARY,
            fullWidth = true,
        )
    }
}

@Composable
private fun MetricsSummary(metrics: ImportMetrics) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        metrics.netEarnings?.let {
            MetricLine(stringResource(R.string.summary_net), Formatters.formatMxn(it))
        }
        metrics.grossEarnings?.let {
            MetricLine(stringResource(R.string.summary_gross), Formatters.formatMxn(it))
        }
        metrics.totalTrips?.let {
            MetricLine(stringResource(R.string.summary_trips), it.toString())
        }
        metrics.totalKm?.let {
            MetricLine(stringResource(R.string.summary_km), Formatters.formatKm(it))
        }
        metrics.hoursOnline?.let {
            MetricLine(stringResource(R.string.summary_hours), Formatters.formatHours(it))
        }
    }
}

@Composable
private fun MetricLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CompletenessBar(fraction: Double) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(R.string.import_review_completeness),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                Formatters.formatPercent(fraction),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(8.dp))
        KomparaProgressBar(progress = fraction.toFloat())
    }
}

@Composable
private fun SavedState(weekStart: String, onDone: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.import_saved_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = Formatters.formatWeekLabel(weekStart),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.import_saved_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        PrimaryButton(text = stringResource(R.string.import_saved_done), onClick = onDone)
    }
}

@Composable
private fun ErrorState(
    message: String,
    retryable: Boolean,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.import_error_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        if (retryable) {
            PrimaryButton(text = stringResource(R.string.import_error_retry), onClick = onRetry)
            Spacer(Modifier.height(10.dp))
        }
        KomparaButton(
            text = stringResource(R.string.import_error_back),
            onClick = onBack,
            variant = ButtonVariant.SECONDARY,
            fullWidth = true,
        )
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

@Composable
private fun stepLabel(step: ImportProgressStep): String = when (step) {
    ImportProgressStep.READING_FILES -> stringResource(R.string.import_step_reading)
    ImportProgressStep.EXTRACTING -> stringResource(R.string.import_step_extracting)
    ImportProgressStep.CALCULATING -> stringResource(R.string.import_step_calculating)
    ImportProgressStep.COMPARING -> stringResource(R.string.import_step_comparing)
}

/** Title / hint string for a platform option in the picker. */
@Composable
private fun stringResource(platform: ImportPlatform, title: Boolean): String = stringResource(
    when (platform) {
        ImportPlatform.UBER_PDF ->
            if (title) R.string.import_platform_uber_pdf else R.string.import_platform_uber_pdf_hint
        ImportPlatform.UBER_SCREENSHOT ->
            if (title) R.string.import_platform_uber_screenshot else R.string.import_platform_uber_screenshot_hint
        ImportPlatform.DIDI ->
            if (title) R.string.import_platform_didi else R.string.import_platform_didi_hint
        ImportPlatform.INDRIVE ->
            if (title) R.string.import_platform_indrive else R.string.import_platform_indrive_hint
    },
)

/**
 * Read the picked [uri] into an in-memory [ImportFile] (filename + MIME + bytes). Returns null if the
 * resolver can't open the stream or the MIME isn't one we accept. Caps the read at the backend's
 * 10 MB limit so a runaway file can't OOM the app — an oversize file simply returns null (the picker
 * MIME filter already restricts the type).
 */
private fun readImportFile(context: Context, uri: Uri): ImportFile? {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri) ?: return null
    if (mime !in IMPORT_MIME_FILTER) return null
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    val name = queryDisplayName(context, uri) ?: defaultName(mime)
    return ImportFile(fileName = name, mimeType = mime, bytes = bytes)
}

private fun queryDisplayName(context: Context, uri: Uri): String? =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else {
                null
            }
        }

private fun defaultName(mime: String): String = when (mime) {
    "application/pdf" -> "import.pdf"
    "image/png" -> "import.png"
    "image/webp" -> "import.webp"
    else -> "import.jpg"
}
