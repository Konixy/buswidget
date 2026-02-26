package com.buswidget.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.work.*
import com.buswidget.di.WidgetEntryPoint
import dagger.hilt.android.EntryPointAccessors
import java.util.concurrent.TimeUnit

class WidgetUpdateWorker(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "WidgetUpdateWorker"
        const val WORK_NAME = "bus_widget_update"

        // Lance la boucle d'actualisation
        fun runNow(context: Context) {
            Log.d(TAG, "Enqueuing immediate update...")
            val request = OneTimeWorkRequestBuilder<WidgetUpdateWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE, // Remplace pour éviter les doublons
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Worker execution started")
        
        // Petit diagnostic réseau
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val networkInfo = connectivityManager.activeNetwork
        if (networkInfo == null) {
            Log.e(TAG, "System reports NO active network")
        }

        return try {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                WidgetEntryPoint::class.java,
            )
            val api = entryPoint.api()
            val favoritesStore = entryPoint.favoritesStore()
            val moshi = entryPoint.moshi()

            val manager = GlanceAppWidgetManager(context)
            val glanceIds = manager.getGlanceIds(BusGlanceWidget::class.java)

            // S'il n'y a plus de widgets, on arrête la boucle
            if (glanceIds.isEmpty()) {
                Log.d(TAG, "No widgets found, stopping loop.")
                return Result.success()
            }

            glanceIds.forEach { glanceId ->
                BusGlanceWidget.updateData(
                    context = context,
                    glanceId = glanceId,
                    api = api,
                    favoritesStore = favoritesStore,
                    moshi = moshi,
                )
            }

            BusGlanceWidget().updateAll(context)
            Log.d(TAG, "Update success. Scheduling next update in 30s...")

            // LA RUSE : On reprogramme le même worker dans 30 secondes
            val nextRequest = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                .setInitialDelay(30, TimeUnit.SECONDS)
                .build()
            
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                nextRequest
            )

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in worker, will retry in 30s anyway", e)
            
            // Même en cas d'erreur (ex: DNS), on relance la boucle dans 30s
            val retryRequest = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                .setInitialDelay(10, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                retryRequest
            )
            
            Result.failure()
        }
    }
}
