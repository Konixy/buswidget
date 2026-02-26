package com.buswidget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.buswidget.ui.departures.DeparturesScreen
import com.buswidget.ui.favorites.FavoritesScreen
import com.buswidget.ui.search.SearchScreen
import com.buswidget.ui.theme.BusWidgetTheme
import com.buswidget.util.BatteryOptimizationHelper
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Demande l'exemption d'optimisation batterie si ce n'est pas déjà fait.
        // Cela permet au worker de faire des requêtes réseau même quand l'app est fermée.
        if (!BatteryOptimizationHelper.isIgnoringBatteryOptimizations(this)) {
            startActivity(BatteryOptimizationHelper.buildRequestIntent(this))
        }

        setContent {
            BusWidgetTheme {
                BusWidgetNavHost()
            }
        }
    }
}

@Composable
fun BusWidgetNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in listOf("search", "favorites")

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.Search, contentDescription = "Recherche") },
                        label = { Text("Recherche") },
                        selected = currentRoute == "search",
                        onClick = {
                            navController.navigate("search") {
                                popUpTo("search") { inclusive = true }
                            }
                        },
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.Star, contentDescription = "Favoris") },
                        label = { Text("Favoris") },
                        selected = currentRoute == "favorites",
                        onClick = {
                            navController.navigate("favorites") {
                                popUpTo("search")
                            }
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "search",
            modifier = Modifier.padding(innerPadding),
        ) {
            composable("search") {
                SearchScreen(
                    onNavigateToDepartures = { stopId, stopName ->
                        navController.navigate("departures/${stopId}/${stopName.encodeForRoute()}")
                    },
                )
            }
            composable("favorites") {
                FavoritesScreen(
                    onNavigateToDepartures = { stopId, stopName ->
                        navController.navigate("departures/${stopId}/${stopName.encodeForRoute()}")
                    },
                )
            }
            composable(
                route = "departures/{stopId}/{stopName}",
                arguments = listOf(
                    navArgument("stopId") { type = NavType.StringType },
                    navArgument("stopName") { type = NavType.StringType },
                ),
            ) { backStackEntry ->
                val stopName = backStackEntry.arguments?.getString("stopName")?.decodeFromRoute() ?: ""
                DeparturesScreen(
                    stopName = stopName,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

private fun String.encodeForRoute() = java.net.URLEncoder.encode(this, "UTF-8")
private fun String.decodeFromRoute() = java.net.URLDecoder.decode(this, "UTF-8")
