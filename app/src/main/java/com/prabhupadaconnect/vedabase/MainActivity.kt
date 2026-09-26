package com.prabhupadaconnect.vedabase

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prabhupadaconnect.vedabase.data.settings.SettingsDataStore
import com.prabhupadaconnect.vedabase.ui.navigation.VedaBaseNavHost
import com.prabhupadaconnect.vedabase.ui.theme.VedaBaseTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsDataStore: SettingsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settings by settingsDataStore.settings.collectAsStateWithLifecycle(
                initialValue = com.prabhupadaconnect.vedabase.core.model.AppSettings()
            )

            VedaBaseTheme(theme = settings.theme) {
                VedaBaseNavHost()
            }
        }
    }
}
