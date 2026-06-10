package mx.kompara.billing

import android.util.Log
import javax.inject.Inject

/** logcat-backed [BillingLogger] used in the app. Tag-stable so billing logs are easy to filter. */
class AndroidBillingLogger @Inject constructor() : BillingLogger {
    override fun d(message: String) {
        Log.d(TAG, message)
    }

    override fun w(message: String, throwable: Throwable?) {
        Log.w(TAG, message, throwable)
    }

    override fun e(message: String, throwable: Throwable?) {
        Log.e(TAG, message, throwable)
    }

    private companion object {
        const val TAG = "KomparaBilling"
    }
}
