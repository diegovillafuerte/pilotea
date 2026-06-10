package mx.kompara.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point that bootstraps the Hilt dependency graph for the whole app.
 */
@HiltAndroidApp
class KomparaApplication : Application()
