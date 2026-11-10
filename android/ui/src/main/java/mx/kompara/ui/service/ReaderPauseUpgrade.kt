package mx.kompara.ui.service

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import mx.kompara.data.service.ReaderReadinessRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 0.2.x could pause the semáforo until tomorrow; the launch set has no pause control
 * (docs/v4-simple-launch-plan.md §3). A pause stored before the update is cleared once, at the first
 * start after it, so no tester comes back to a reader they cannot resume.
 */
@Singleton
class ReaderPauseUpgrade(private val prefs: SharedPreferences, private val reader: ReaderReadinessRepository) {
    @Inject constructor(@ApplicationContext context: Context, reader: ReaderReadinessRepository) :
        this(context.getSharedPreferences("v4_upgrade", Context.MODE_PRIVATE), reader)

    fun run() {
        if (prefs.getBoolean(DONE, false)) return
        if (reader.state.value.paused) reader.resume()
        prefs.edit { putBoolean(DONE, true) }
    }

    private companion object { const val DONE = "reader_pause_cleared" }
}
