package mx.kompara.ui.notifications

import android.net.Uri
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** Only a destination and its calendar key travel through Android. Never money or a frozen
 * recovery state: an old outage tap must show today's actual readiness. */
data class NoticeTarget(val kind: NoticeKind, val period: String = "") {
    val uri: Uri get() = Uri.parse("kompara://notice/${kind.name.lowercase()}/$period")
    companion object {
        fun parse(uri: Uri?): NoticeTarget? = runCatching {
            require(uri?.scheme == "kompara" && uri.host == "notice" && uri.query == null && uri.fragment == null)
            val parts = uri!!.pathSegments
            val kind = NoticeKind.valueOf(parts.first().uppercase())
            val period = if (kind == NoticeKind.READER) { require(parts.size == 1); "" }
                else { require(parts.size == 2); parts[1] }
            when (kind) {
                NoticeKind.READER -> Unit
                NoticeKind.MONTH -> require(YearMonth.parse(period).toString() == period)
                NoticeKind.DAY, NoticeKind.WEEK, NoticeKind.IMPORT -> {
                    val date = LocalDate.parse(period)
                    require(date.toString() == period)
                    if (kind == NoticeKind.WEEK || kind == NoticeKind.IMPORT) require(date.dayOfWeek == DayOfWeek.MONDAY)
                }
            }
            NoticeTarget(kind, period)
        }.getOrNull()
    }
}
enum class NoticeKind { READER, DAY, WEEK, MONTH, IMPORT }
