package com.cyprienbrisset.myportal.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val TILE_EDIT = "tile_edit"
    const val ALARMS = "alarms"
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            com.cyprienbrisset.myportal.ui.home.HomeScreen(
                onOpenSettings = { nav.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.SETTINGS) {
            com.cyprienbrisset.myportal.ui.settings.SettingsScreen(
                onBack = { nav.popBackStack() },
                onTiles = { nav.navigate(Routes.TILE_EDIT) },
                onAlarms = { nav.navigate(Routes.ALARMS) },
                onWeather = { nav.navigate(Routes.SETTINGS + "/weather") },
            )
        }
        composable(Routes.TILE_EDIT) {
            com.cyprienbrisset.myportal.ui.settings.TileEditScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS + "/weather") {
            com.cyprienbrisset.myportal.ui.settings.WeatherSettingsScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.ALARMS) { Text("Alarms") }
    }
}
