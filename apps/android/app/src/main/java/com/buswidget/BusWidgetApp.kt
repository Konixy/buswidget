package com.buswidget

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.buswidget.widget.WidgetUpdateWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class BusWidgetApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Lance la boucle d'actualisation du widget seulement s'il y a des widgets actifs
        GlobalScope.launch {
            val manager = androidx.glance.appwidget.GlanceAppWidgetManager(this@BusWidgetApp)
            if (manager.getGlanceIds(com.buswidget.widget.BusGlanceWidget::class.java).isNotEmpty()) {
                WidgetUpdateWorker.runNow(this@BusWidgetApp)
            }
        }
    }
}
