package mx.kompara.ui.stats

/**
 * A trivial injectable clock so viewmodels read "now" through a seam tests can pin. Mirrors the
 * pattern the rollup layer uses (time is always passed in, never read statically).
 */
fun interface AppClock {
    fun nowMs(): Long
}
