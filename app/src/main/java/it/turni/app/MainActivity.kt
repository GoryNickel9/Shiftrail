package it.turni.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import it.turni.app.ui.AppViewModel
import it.turni.app.ui.TurniApp

class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        appViewModel.refreshCalendar()
        appViewModel.checkUpdates(automatic = true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { TurniApp(appViewModel) }
    }
}
