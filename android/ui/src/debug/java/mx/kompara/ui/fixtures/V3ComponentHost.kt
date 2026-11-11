package mx.kompara.ui.fixtures

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import mx.kompara.ui.theme.KomparaTheme

/** Explicit debug activity, no production navigation or seeded repository. */
class V3ComponentActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KomparaTheme(darkTheme = intent.getBooleanExtra("dark", false)) {
            if (intent.hasExtra("shareFrame")) ShareFixtureHost(intent.getStringExtra("shareFrame") ?: "H3", !intent.getBooleanExtra("showAmounts", false))
            else if (intent.hasExtra("planFrame")) PlanFixtureHost(intent.getStringExtra("planFrame") ?: "H1")
            else if (intent.hasExtra("importFrame")) ImportFixtureHost(intent.getStringExtra("importFrame") ?: "H4")
            else if (intent.hasExtra("onboardingFrame")) OnboardingFixtureHost(intent.getStringExtra("onboardingFrame") ?: "A6")
            else if (intent.hasExtra("privacyFrame")) PrivacyFixtureHost(intent.getStringExtra("privacyFrame") ?: "G9")
            else if (intent.hasExtra("costsFrame")) CostsFixtureHost(intent.getStringExtra("costsFrame") ?: "A5")
            else if (intent.hasExtra("settingsFrame")) SettingsFixtureHost(intent.getStringExtra("settingsFrame") ?: "J1")
            else if (intent.hasExtra("compareFrame")) CompareFixtureHost(intent.getStringExtra("compareFrame") ?: "K1")
            else if (intent.hasExtra("imssFrame")) ImssFixtureHost(intent.getStringExtra("imssFrame") ?: "M1")
            // The v3 component gallery was retired with the frames it drew; Ganancias is the launch screen.
            else GananciasFixtureHost(intent.getStringExtra("gananciasFrame") ?: "L1", Modifier.fillMaxSize())
        } }
    }
}
