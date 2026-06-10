package mx.kompara.metrics.fiscal

/**
 * The Mexican "régimen de plataformas tecnológicas" withholding rates Kompara uses to *estimate* what
 * Uber/DiDi/inDrive withhold from a driver's gross fares each month (B-052). **Single source of
 * truth** — every rate the [FiscalCalculator] applies lives here, with the legal citation inline, so a
 * rate correction is a one-line edit in one file (and the techdebt note below stays attached to it).
 *
 * ## The regime, in one paragraph
 * Under LISR Sección III (arts. 113-A–113-D) and LIVA art. 18-J, a technology platform that
 * intermediates a service must **withhold ISR and IVA on the driver's gross fares and remit them to
 * the SAT** on the driver's behalf. For an individual ("persona física") who has given the platform a
 * valid RFC, these are the *only* withholdings and (under the optional "pago definitivo" election) can
 * be the driver's final tax on that income — which is exactly why a per-month withholding summary is a
 * useful credit-application / accountant artifact.
 *
 * ## Rates (terrestrial passenger transport — the launch scope)
 *  - **ISR: 2.1 % of gross.** LISR art. 113-A, fracción I — "servicios de transporte terrestre de
 *    pasajeros y de entrega de bienes": 2.1 %. Unchanged by the 2026 reform (which only moved the
 *    *other-services* bucket from 1 % → 2.5 %; transport stayed at 2.1 %). Verified June 2026 against
 *    SAT minisitio + practitioner guidance (siemprealdia, FacturoPorTi, grupofiscalcastroycia).
 *  - **IVA: 8 % of gross.** LIVA art. 18-J, fracción II, inciso a) read with the RMF: the platform
 *    withholds **50 % of the 16 % IVA trasladado** when the driver has an RFC → 8 %. (Without an RFC
 *    the platform must withhold the full 16 %; we assume the registered-driver case and disclose it.)
 *
 * ## Why these are ESTIMATES, never exact
 *  1. We withhold on **gross fares we observe** (sum of daily/weekly `grossEarningsMxn`), which on the
 *     captured path are offer-fare estimates, not the platform's CFDI base. The platform may compute on
 *     a slightly different base (e.g. excluding certain fees/tolls), so the centavos won't tie out.
 *  2. We assume the **registered-RFC, transport** case for every platform. A driver without an RFC, or
 *     one doing delivery vs. rides, faces different IVA/regime treatment we don't model per-driver yet.
 *  3. Rounding and per-trip vs. per-period withholding differ from a flat monthly application.
 * The UI/PDF therefore label every figure an *estimación* and tell the driver their CFDI/constancia
 * from the platform is the authoritative number.
 *
 * TODO(legal-B038): these rates + the RFC/transport assumptions are research-grade and MUST be
 * confirmed with counsel/an accountant before the fiscal feature is relied on publicly — this is a
 * launch gate. See techdebt.md ("Platform-withholding rate estimates need counsel verification").
 */
object PlatformWithholdingRates {

    /**
     * ISR withheld as a fraction of **gross** fares — 2.1 % for terrestrial passenger transport
     * (LISR art. 113-A fr. I). Data-driven so a correction is one edit.
     */
    const val ISR_RATE_TRANSPORT: Double = 0.021

    /**
     * IVA withheld as a fraction of **gross** fares — 8 % (= 50 % of the 16 % IVA trasladado, the
     * registered-RFC case under LIVA art. 18-J + RMF). Data-driven so a correction is one edit.
     */
    const val IVA_RATE_TRANSPORT: Double = 0.08

    /**
     * The standard IVA rate (16 %) the platform withholding is half of. Kept here only so the
     * relationship `IVA_RATE_TRANSPORT == STANDARD_IVA_RATE * IVA_WITHHOLDING_FRACTION` is
     * self-documenting (and asserted in tests), not applied directly.
     */
    const val STANDARD_IVA_RATE: Double = 0.16

    /** Fraction of the transferred IVA the platform withholds for a registered driver (50 %). */
    const val IVA_WITHHOLDING_FRACTION: Double = 0.50

    /** The fiscal year these rates describe; surfaced in the PDF/UI so a stale rate is visible. */
    const val RATES_YEAR: Int = 2026
}
