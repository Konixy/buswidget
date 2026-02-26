package com.buswidget.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.buswidget.data.local.FavoriteStop
import com.buswidget.data.local.FavoritesStore
import com.buswidget.ui.theme.BusWidgetTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class WidgetConfigurationActivity : ComponentActivity() {

    @Inject
    lateinit var favoritesStore: FavoritesStore

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Récupère l'ID du widget en cours de configuration
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // Si on n'a pas d'ID valide, on ferme
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        setContent {
            BusWidgetTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ConfigurationScreen()
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ConfigurationScreen() {
        val scope = rememberCoroutineScope()
        val favorites by favoritesStore.favoritesFlow.collectAsState(initial = emptyList())

        Scaffold(
            topBar = { TopAppBar(title = { Text("Choisir un arrêt") }) }
        ) { padding ->
            if (favorites.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text("Ajoutez d'abord des favoris dans l'application.")
                }
            } else {
                LazyColumn(modifier = Modifier.padding(padding)) {
                    items(favorites) { favorite ->
                        ListItem(
                            headlineContent = { Text(favorite.stop.name) },
                            supportingContent = { Text(favorite.selectedLines.joinToString(", ")) },
                            modifier = Modifier.clickable {
                                scope.launch {
                                    handleSelect(favorite)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private suspend fun handleSelect(favorite: FavoriteStop) {
        val context = this.applicationContext
        val glanceAppWidgetManager = GlanceAppWidgetManager(context)
        
        // Récupération sécurisée du GlanceId
        val glanceId = try {
            glanceAppWidgetManager.getGlanceIdBy(appWidgetId)
        } catch (e: Exception) {
            android.util.Log.e("WidgetConfig", "Could not get GlanceId for widget $appWidgetId", e)
            null
        }

        if (glanceId != null) {
            // 1. Sauvegarde l'arrêt choisi
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[BusGlanceWidget.STOP_ID_KEY] = favorite.stop.id
                    // On pré-remplit le nom de l'arrêt pour un affichage immédiat
                    val initialData = WidgetData(
                        stopName = favorite.stop.name,
                        departures = emptyList(),
                        errorMessage = "Mise à jour...",
                        updatedAtMs = System.currentTimeMillis()
                    )
                    this[BusGlanceWidget.WIDGET_DATA_KEY] = initialData.toJson(favoritesStore.moshi)
                }
            }
            
            // 2. Force le rafraîchissement visuel immédiat
            BusGlanceWidget().update(context, glanceId)

            // 3. Lance le Worker pour les vraies données
            WidgetUpdateWorker.runNow(context)
        }

        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(Activity.RESULT_OK, resultValue)
        finish()
    }
}
