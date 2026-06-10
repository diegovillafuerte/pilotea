package mx.kompara.ui.stats

import mx.kompara.data.db.entity.WeeklyAggregateEntity
import mx.kompara.data.model.Platform

/**
 * Which platform the dashboard is showing, and the chip row above the metrics.
 *
 * Chips appear only when **more than one** platform captured data in the period — a single-platform
 * driver never sees a redundant "Uber | Todas" toggle. When chips do appear, "Todas" leads (summed
 * view), followed by each platform that has data, in [Platform] declaration order.
 */
object PlatformSelection {

    /** Platforms (declaration order) that have at least one captured row in [rows]. */
    fun platformsWithData(rows: List<WeeklyAggregateEntity>): List<Platform> {
        val present = rows.mapNotNull { row ->
            runCatching { Platform.valueOf(row.platform) }.getOrNull()
        }.toSet()
        return Platform.entries.filter { it in present }
    }

    /**
     * The chip options to render, or an empty list when no chips are warranted (0 or 1 platform).
     * A null entry represents "Todas"; non-null entries are individual platforms.
     */
    fun chips(rows: List<WeeklyAggregateEntity>): List<Platform?> {
        val platforms = platformsWithData(rows)
        if (platforms.size <= 1) return emptyList()
        return buildList {
            add(null) // "Todas"
            addAll(platforms)
        }
    }

    /**
     * Resolve the platform to actually display given the user's [selected] choice and what data
     * exists. Keeps the view sane after data changes: if the selected platform no longer has data we
     * fall back to "Todas"; with a single platform we pin to it (no chips, so "Todas" == that one).
     */
    fun resolve(rows: List<WeeklyAggregateEntity>, selected: Platform?): Platform? {
        val platforms = platformsWithData(rows)
        return when {
            platforms.isEmpty() -> null
            platforms.size == 1 -> platforms.first()
            selected != null && selected in platforms -> selected
            else -> null // "Todas"
        }
    }
}
