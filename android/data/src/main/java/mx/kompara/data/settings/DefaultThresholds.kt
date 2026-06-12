package mx.kompara.data.settings

/**
 * City-seeded default acceptance floors, hand-ported from the web MVP's population seed
 * (`seed/population-stats.ts`). The seed models per-city earnings distributions; we take the **p50**
 * of `earnings_per_km` and `earnings_per_hour` as the starting "you're at the median" floor
 * (anything below the city median is, by definition, a below-average offer). Drivers tune these
 * later via [SettingsRepository.setThreshold].
 *
 * One row per city (B-076: a single semáforo shared by every platform) — the base-multiplier row of
 * the seed (platformMult 1.0, the Uber column), computed from the seed's deterministic generator,
 * not copied by eye:
 *   earnings_per_km   p50 = round2(7   × cityMult)
 *   earnings_per_hour p50 = round2(140 × cityMult)
 * with cityMult from `CITY_MODIFIERS`. Unknown cities fall back to the "national" row. Keep this
 * table in sync if the seed changes.
 */
object DefaultThresholds {

    /** Canonical city keys matching the web seed; lowercased, no accents. */
    private val byCity: Map<String, PlatformThreshold> = mapOf(
        "cdmx" to PlatformThreshold(8.05, 161.0),
        "monterrey" to PlatformThreshold(7.7, 154.0),
        "guadalajara" to PlatformThreshold(7.35, 147.0),
        "puebla" to PlatformThreshold(6.3, 126.0),
        "toluca" to PlatformThreshold(6.16, 123.2),
        "tijuana" to PlatformThreshold(7.84, 156.8),
        "leon" to PlatformThreshold(5.95, 119.0),
        "queretaro" to PlatformThreshold(6.65, 133.0),
        "merida" to PlatformThreshold(6.44, 128.8),
        "cancun" to PlatformThreshold(8.4, 168.0),
        "national" to PlatformThreshold(7.0, 140.0),
    )

    /** The national-fallback city key. */
    const val NATIONAL: String = "national"

    /**
     * Default floors for a [city] (case-insensitive). Falls back to the national row for unknown
     * cities, and finally to [PlatformThreshold.DEFAULT] if even national is somehow absent.
     */
    fun forCity(city: String): PlatformThreshold {
        val key = city.trim().lowercase()
        return byCity[key] ?: byCity[NATIONAL] ?: PlatformThreshold.DEFAULT
    }
}
