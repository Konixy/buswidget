package com.buswidget.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import com.buswidget.di.WidgetEntryPoint
import dagger.hilt.android.EntryPointAccessors

/**
 * Callback de rafraîchissement déclenché par le bouton.
 * Effectue la mise à jour DIRECTEMENT pour une réactivité maximale.
 */
class RefreshWidgetCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        try {
            // Récupération des dépendances via EntryPoint
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                WidgetEntryPoint::class.java,
            )
            
            // On fait la mise à jour de la donnée immédiatement
            BusGlanceWidget.updateData(
                context = context,
                glanceId = glanceId,
                api = entryPoint.api(),
                favoritesStore = entryPoint.favoritesStore(),
                moshi = entryPoint.moshi()
            )

            // On redessine le widget
            BusGlanceWidget().update(context, glanceId)
            
            // Et on relance la boucle de fond pour pas qu'elle s'arrête
            WidgetUpdateWorker.runNow(context)
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
