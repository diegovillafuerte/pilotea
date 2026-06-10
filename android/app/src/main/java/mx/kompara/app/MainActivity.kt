package mx.kompara.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import mx.kompara.app.ui.theme.KomparaTheme
import mx.kompara.ui.HomeScreen

/**
 * Single-activity host. [@AndroidEntryPoint][AndroidEntryPoint] makes this a Hilt injection
 * target so the home screen's [mx.kompara.ui.HomeViewModel] (which pulls the injected
 * SettingsRepository from `:data`) resolves end-to-end — proving the cross-module DI graph.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KomparaTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HomeScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}
