package com.apollo.overload.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.apollo.overload.OverloadApp
import kotlinx.coroutines.launch

/** Redraws every Overload widget; called after the app writes workout data. */
object WidgetSync {
    fun update(context: Context) {
        val app = context.applicationContext as OverloadApp
        app.appScope.launch { runCatching { OverloadWidget().updateAll(app) } }
    }
}
