package mx.kompara.ui.screens

import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Ajustes → "Ayuda" (B-071): the in-app help center — expandable FAQ entries covering the reader,
 * the semáforo, OEM battery kills, data/privacy, and the account. Static content (no network) so
 * it works offline, where a driver mid-shift actually reads it.
 */
@Composable
fun HelpScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(mx.kompara.ui.R.string.help_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(mx.kompara.ui.R.string.help_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        HELP_ITEMS.forEach { item -> HelpItemCard(item) }
    }
}

private data class HelpItem(
    @param:StringRes val questionRes: Int,
    @param:StringRes val answerRes: Int,
)

private val HELP_ITEMS = listOf(
    HelpItem(mx.kompara.ui.R.string.help_q_reader, mx.kompara.ui.R.string.help_a_reader),
    HelpItem(mx.kompara.ui.R.string.help_q_verdict, mx.kompara.ui.R.string.help_a_verdict),
    HelpItem(mx.kompara.ui.R.string.help_q_battery, mx.kompara.ui.R.string.help_a_battery),
    HelpItem(mx.kompara.ui.R.string.help_q_privacy, mx.kompara.ui.R.string.help_a_privacy),
    HelpItem(mx.kompara.ui.R.string.help_q_account, mx.kompara.ui.R.string.help_a_account),
    HelpItem(mx.kompara.ui.R.string.help_q_price, mx.kompara.ui.R.string.help_a_price),
    HelpItem(mx.kompara.ui.R.string.help_q_broken, mx.kompara.ui.R.string.help_a_broken),
)

@Composable
private fun HelpItemCard(item: HelpItem) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .animateContentSize(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(item.questionRes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                )
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(item.answerRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
