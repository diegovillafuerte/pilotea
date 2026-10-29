package mx.kompara.ui.notifications

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mx.kompara.data.accounting.PeriodWindow
import mx.kompara.data.db.dao.OfferDao
import mx.kompara.data.db.dao.TripDao
import mx.kompara.data.service.DrivingActivity
import mx.kompara.data.settings.Settings
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.metrics.PeriodMoneyRepository
import mx.kompara.ui.R
import mx.kompara.ui.share.WeekCloseAction
import mx.kompara.ui.share.WeekCloseDecision
import mx.kompara.ui.stats.AppClock
import mx.kompara.ui.stats.monday
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Raw lifecycle evidence survives switching to navigation and process restart. An observation
 * gap never finishes a trip, and a static foreground host never times out into a safe moment. */
@Singleton
class SummarySafety @Inject constructor(private val offers: OfferDao, private val trips: TripDao) {
    suspend fun busy(): Boolean = DrivingActivity.mayBeDriving ||
        offers.latestPending() != null || trips.latestOpen() != null || DrivingActivity.mayBeDriving
}

object SummaryCalendar {
    val DAY_TIME: LocalTime = LocalTime.of(21, 0)
    fun due(kind: NoticeKind, now: ZonedDateTime): String = when (kind) {
        NoticeKind.DAY -> (if (now.toLocalTime() >= DAY_TIME) now.toLocalDate() else now.toLocalDate().minusDays(1)).toString()
        NoticeKind.WEEK -> now.toLocalDate().monday().minusWeeks(1).toString()
        NoticeKind.MONTH -> YearMonth.from(now).minusMonths(1).toString()
        NoticeKind.READER -> error("Reader incidents are not summaries")
    }
    fun dayDelay(now: ZonedDateTime): Duration {
        var next = now.with(DAY_TIME)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }
}

/** The existing day/week/month workers share a durable dated outbox. Retrying tomorrow keeps
 * yesterday's destination. Only successful delivery consumes a notice; Android denial is real.
 * A mutex serializes periodic + app-open workers, and stable tags make crash retries updates. */
@Singleton
class SummaryDelivery @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val channels: NoticeChannels,
    private val poster: NoticePoster,
    private val safety: SummarySafety,
    private val money: PeriodMoneyRepository,
    private val clock: AppClock,
    private val zone: ZoneId,
) {
    private val mutex = Mutex()
    private val prefs = context.getSharedPreferences("summary_delivery_v3", Context.MODE_PRIVATE)

    /** True means pending work needs WorkManager retry (busy or denied), without advancing it. */
    suspend fun deliver(kind: NoticeKind): Boolean = mutex.withLock {
        channels.migrate()
        val config = settings.settings.first()
        if (!config.onboardingCompleted) return@withLock false
        val now = Instant.ofEpochMilli(clock.nowMs()).atZone(zone)
        val candidate = SummaryCalendar.due(kind, now)
        val pendingKey = "pending:${kind.name}"
        val handledKey = "handled:${kind.name}"
        val pending = prefs.getStringSet(pendingKey, emptySet()).orEmpty().toMutableSet()
        val handled = prefs.getStringSet(handledKey, emptySet()).orEmpty().toMutableSet()
        // Preserve legacy successful delivery watermarks, even though routes/copy have changed.
        when (kind) {
            NoticeKind.WEEK -> config.shareLastReminderWeek
            NoticeKind.MONTH -> config.fiscalLastNotifiedMonth
            else -> null
        }?.let(handled::add)
        if (candidate !in handled) pending.add(candidate)
        fun save() { check(prefs.edit().putStringSet(pendingKey, pending).putStringSet(handledKey, handled).commit()) }
        save() // Store the calendar key before any suspension or deferral.
        if (!enabled(kind, config)) {
            poster.cancelSummaries(kind)
            handled.addAll(pending); pending.clear(); save()
            return@withLock false
        }
        if (pending.isEmpty()) return@withLock false
        if (safety.busy()) return@withLock true
        for (period in pending.sorted()) {
            val target = NoticeTarget(kind, period)
            val start = if (kind == NoticeKind.MONTH) YearMonth.parse(period).atDay(1) else LocalDate.parse(period)
            val end = when (kind) { NoticeKind.MONTH -> YearMonth.parse(period).atEndOfMonth(); NoticeKind.WEEK -> start.plusDays(6); else -> start }
            val window = PeriodWindow(start, end, now.toInstant(), zone)
            val snapshot = when (kind) {
                NoticeKind.MONTH -> money.monthSnapshot(window).money
                NoticeKind.WEEK -> money.weekSnapshot(window).money
                else -> money.snapshot(window)
            }
            val records = (snapshot.contributions + snapshot.paymentOnly).map { it.record }
            val hasData = records.isNotEmpty()
            val fresh = settings.settings.first()
            if (!hasData && kind == NoticeKind.DAY && start == now.toLocalDate() && enabled(kind, fresh)) return@withLock true
            when (WeekCloseDecision.decide(enabled(kind, fresh), period, handled.firstOrNull { it == period }, hasData,
                busy = safety.busy(), allowed = poster.allowed(NoticeChannels.SUMMARIES))) {
                WeekCloseAction.DEFER -> return@withLock true
                WeekCloseAction.POST -> {
                    val facts = SummaryFacts(records.sumOf { it.trips }.takeIf { records.all { r -> r.tripsKnown } },
                        snapshot.hoursOnline.multiply(java.math.BigDecimal(60)).setScale(0, java.math.RoundingMode.HALF_UP).toLong())
                    val copy = summaryCopy(context, target, facts, now.toLocalDate())
                    if (!poster.post(target, copy.first, copy.second, clock.nowMs())) return@withLock true
                }
                WeekCloseAction.STAMP_ONLY, WeekCloseAction.SKIP -> Unit
            }
            handled.add(period); pending.remove(period); save()
        }
        false
    }
    private fun enabled(kind: NoticeKind, prefs: Settings) = when (kind) {
        NoticeKind.DAY -> prefs.dailySummaryEnabled
        NoticeKind.WEEK -> prefs.shareWeeklyReminderEnabled
        NoticeKind.MONTH -> prefs.fiscalMonthlySummaryEnabled
        NoticeKind.READER -> false
    }
}

data class SummaryFacts(val trips: Int?, val minutes: Long)
fun summaryCopy(context: Context, target: NoticeTarget, facts: SummaryFacts, today: LocalDate): Pair<String, String> {
    val locale = Locale.forLanguageTag("es-MX")
    val dateFormat = DateTimeFormatter.ofPattern("d MMM", locale)
    val count = facts.trips?.let { context.resources.getQuantityString(R.plurals.notice_trips, it, it) }
    return when (target.kind) {
        NoticeKind.DAY -> {
            val day = LocalDate.parse(target.period)
            val title = if (day == today) context.getString(R.string.notice_day_title)
                else context.getString(R.string.notice_past_day_title, day.format(dateFormat))
            val duration = context.getString(R.string.notice_shift_duration, mx.kompara.ui.format.Formatters.formatDuration(facts.minutes))
            title to listOfNotNull(count, duration.takeIf { facts.minutes > 0 }).joinToString(" · ")
        }
        NoticeKind.WEEK -> {
            val start = LocalDate.parse(target.period)
            val end = start.plusDays(6)
            val range = if (start.month == end.month) "${start.dayOfMonth} – ${end.format(dateFormat)}"
                else "${start.format(dateFormat)} – ${end.format(dateFormat)}"
            context.getString(R.string.share_week_close_notif_title) to listOfNotNull(count, range).joinToString(" · ")
        }
        NoticeKind.MONTH -> context.getString(R.string.notice_month_title,
            YearMonth.parse(target.period).format(DateTimeFormatter.ofPattern("MMMM", locale)).replaceFirstChar { it.titlecase(locale) }) to
            context.getString(R.string.notice_month_body)
        NoticeKind.READER -> error("Incident copy uses observed cause")
    }
}
