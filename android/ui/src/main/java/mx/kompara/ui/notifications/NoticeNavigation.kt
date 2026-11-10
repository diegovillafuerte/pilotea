package mx.kompara.ui.notifications

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import mx.kompara.ui.nav.KomparaDestination
import mx.kompara.ui.stats.AppClock
import mx.kompara.ui.stats.SelectedPeriod
import java.time.*
import javax.inject.Inject

@HiltViewModel
class NoticeNavigation @Inject constructor(private val selection: SelectedPeriod,
    private val clock: AppClock, private val zone: ZoneId) : ViewModel() {
    suspend fun route(target: NoticeTarget): String {
        if (target.kind == NoticeKind.WEEK) selection.select(LocalDate.parse(target.period))
        if (target.kind == NoticeKind.MONTH) selection.selectMonth(YearMonth.parse(target.period),
            Instant.ofEpochMilli(clock.nowMs()).atZone(zone).toLocalDate())
        return destination(target)
    }
    companion object {
        fun destination(target: NoticeTarget): String = when (target.kind) {
            NoticeKind.READER -> KomparaDestination.GANANCIAS.route
            NoticeKind.DAY -> KomparaDestination.dayRoute(target.period)
            NoticeKind.WEEK -> "${KomparaDestination.WEEK_SUMMARY_ROUTE}/${target.period}"
            NoticeKind.MONTH -> KomparaDestination.IMSS.route
            NoticeKind.IMPORT -> KomparaDestination.IMPORT_ROUTE
        }
    }
}
