package mx.kompara.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.kompara.ui.theme.VerdictRed

/**
 * The reader-down banner (B-078): a tappable pill the accessibility service overlays on DiDi/
 * inDrive while the screen reader is dead — the moment the driver is silently missing verdicts.
 * Tapping relaunches the capture consent flow; the controller hides it the instant capture is back
 * (or the driver leaves the host app).
 */
@Composable
fun ReaderDownBannerUi(onTap: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xF2212121),
        shadowElevation = 6.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(onClick = onTap)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(color = VerdictRed, shape = CircleShape),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.overlay_reader_down),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
