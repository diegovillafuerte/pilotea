package mx.kompara.data.settings

import mx.kompara.data.model.Platform

/**
 * City-seeded default acceptance floors, hand-ported from the web MVP's population seed
 * (`seed/population-stats.ts`). The seed models per-city, per-platform earnings distributions; we
 * take the **p50** of `earnings_per_km` and `earnings_per_hour` as the starting "you're at the
 * median" floor (anything below the city median is, by definition, a below-average offer). Drivers
 * tune these later via [SettingsRepository.setThreshold].
 *
 * Values were computed from the seed's deterministic generator, not copied by eye:
 *   earnings_per_km   p50 = round2(7   × cityMult × platformMult)
 *   earnings_per_hour p50 = round2(140 × cityMult × platformMult)
 * with cityMult from `CITY_MODIFIERS` and platformMult from `PLATFORM_MODIFIERS` (uber 1.0,
 * didi 0.92). InDrive and other platforms fall back to [Platform.UBER]'s row for the same city,
 * and unknown cities fall back to the "national" row. Keep this table in sync if the seed changes.
 */
object DefaultThresholds {

    /** Canonical city keys matching the web seed; lowercased, no accents. */
    private val byCityAndPlatform: Map<String, Map<Platform, PlatformThreshold>> = mapOf(
        "cdmx" to mapOf(
            Platform.UBER to PlatformThreshold(8.05, 161.0),
            Platform.DIDI to PlatformThreshold(7.41, 148.12),
        ),
        "monterrey" to mapOf(
            Platform.UBER to PlatformThreshold(7.7, 154.0),
            Platform.DIDI to PlatformThreshold(7.08, 141.68),
        ),
        "guadalajara" to mapOf(
            Platform.UBER to PlatformThreshold(7.35, 147.0),
            Platform.DIDI to PlatformThreshold(6.76, 135.24),
        ),
        "puebla" to mapOf(
            Platform.UBER to PlatformThreshold(6.3, 126.0),
            Platform.DIDI to PlatformThreshold(5.8, 115.92),
        ),
        "toluca" to mapOf(
            Platform.UBER to PlatformThreshold(6.16, 123.2),
            Platform.DIDI to PlatformThreshold(5.67, 113.34),
        ),
        "tijuana" to mapOf(
            Platform.UBER to PlatformThreshold(7.84, 156.8),
            Platform.DIDI to PlatformThreshold(7.21, 144.26),
        ),
        "leon" to mapOf(
            Platform.UBER to PlatformThreshold(5.95, 119.0),
            Platform.DIDI to PlatformThreshold(5.47, 109.48),
        ),
        "queretaro" to mapOf(
            Platform.UBER to PlatformThreshold(6.65, 133.0),
            Platform.DIDI to PlatformThreshold(6.12, 122.36),
        ),
        "merida" to mapOf(
            Platform.UBER to PlatformThreshold(6.44, 128.8),
            Platform.DIDI to PlatformThreshold(5.92, 118.5),
        ),
        "cancun" to mapOf(
            Platform.UBER to PlatformThreshold(8.4, 168.0),
            Platform.DIDI to PlatformThreshold(7.73, 154.56),
        ),
        "national" to mapOf(
            Platform.UBER to PlatformThreshold(7.0, 140.0),
            Platform.DIDI to PlatformThreshold(6.44, 128.8),
        ),
    )

    /** The national-fallback city key. */
    const val NATIONAL: String = "national"

    /**
     * Default floors for a [city] (case-insensitive) and [platform]. Falls back to the same city's
     * Uber row for non-Uber/DiDi platforms, then to the national row for unknown cities, and
     * finally to [PlatformThreshold.DEFAULT] if even national is somehow absent.
     */
    fun forCity(city: String, platform: Platform): PlatformThreshold {
        val key = city.trim().lowercase()
        val cityRow = byCityAndPlatform[key] ?: byCityAndPlatform[NATIONAL]
        val resolved = cityRow?.get(platform)
            ?: cityRow?.get(Platform.UBER)
            ?: byCityAndPlatform[NATIONAL]?.get(Platform.UBER)
        return resolved ?: PlatformThreshold.DEFAULT
    }
}
