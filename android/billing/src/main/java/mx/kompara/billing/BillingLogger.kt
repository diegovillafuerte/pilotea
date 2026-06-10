package mx.kompara.billing

/**
 * Tiny logging seam so the billing logic stays a pure-JVM unit (no `android.util.Log` on the
 * unit-test classpath, no Robolectric). [AndroidBillingLogger] forwards to logcat in the app;
 * tests inject a no-op or a recording fake.
 */
interface BillingLogger {
    fun d(message: String)
    fun w(message: String, throwable: Throwable? = null)
    fun e(message: String, throwable: Throwable? = null)

    companion object {
        /** Discards everything — the default for tests/dev that don't care about output. */
        val NOOP: BillingLogger = object : BillingLogger {
            override fun d(message: String) = Unit
            override fun w(message: String, throwable: Throwable?) = Unit
            override fun e(message: String, throwable: Throwable?) = Unit
        }
    }
}
