package com.apollo.overload

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.apollo.overload.ui.AppShell
import com.apollo.overload.ui.theme.OverloadTheme
import com.apollo.overload.widget.WidgetSync

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as OverloadApp
        setContent {
            OverloadTheme {
                AppShell(app)
            }
        }
    }

    /** Leaving the app: the widget picks up whatever changed in it (plan edits included). */
    override fun onStop() {
        super.onStop()
        WidgetSync.update(this)
    }
}
