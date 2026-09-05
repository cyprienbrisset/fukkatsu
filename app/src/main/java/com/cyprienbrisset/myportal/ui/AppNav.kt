package com.cyprienbrisset.myportal.ui

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val TILE_EDIT = "tile_edit"
    const val ALARMS = "alarms"
    const val ALARM_EDIT = "alarm_edit"
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            com.cyprienbrisset.myportal.ui.home.HomeScreen(
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onAddTile = { nav.navigate(Routes.TILE_EDIT) },
            )
        }
        composable(Routes.SETTINGS) {
            val context = LocalContext.current
            com.cyprienbrisset.myportal.ui.settings.SettingsScreen(
                onBack = { nav.popBackStack() },
                onTiles = { nav.navigate(Routes.TILE_EDIT) },
                onAlarms = { nav.navigate(Routes.ALARMS) },
                onWeather = { nav.navigate(Routes.SETTINGS + "/weather") },
                // TEMPORARY spike entry point.
                onFukkaLogin = {
                    context.startActivity(
                        Intent(context, com.cyprienbrisset.myportal.store.FukkaLoginActivity::class.java)
                    )
                },
            )
        }
        composable(Routes.TILE_EDIT) {
            com.cyprienbrisset.myportal.ui.settings.TileEditScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS + "/weather") {
            com.cyprienbrisset.myportal.ui.settings.WeatherSettingsScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.ALARMS) {
            com.cyprienbrisset.myportal.ui.alarms.AlarmsScreen(
                onBack = { nav.popBackStack() },
                onAdd = { nav.navigate(Routes.ALARM_EDIT) },
            )
        }
        composable(Routes.ALARM_EDIT) {
            com.cyprienbrisset.myportal.ui.alarms.AlarmEditScreen(onDone = { nav.popBackStack() })
        }
    }
}
