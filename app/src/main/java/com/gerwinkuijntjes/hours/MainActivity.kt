package com.gerwinkuijntjes.hours

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.gerwinkuijntjes.hours.backup.BackupSettings
import com.gerwinkuijntjes.hours.ui.currentLocale
import com.gerwinkuijntjes.hours.ui.davErrorText
import com.gerwinkuijntjes.hours.ui.formatMoney
import com.gerwinkuijntjes.hours.ui.screens.BackupScreen
import com.gerwinkuijntjes.hours.ui.screens.ClientDetailScreen
import com.gerwinkuijntjes.hours.ui.screens.DayScreen
import com.gerwinkuijntjes.hours.ui.screens.FolderPickerScreen
import com.gerwinkuijntjes.hours.ui.screens.OverviewScreen
import com.gerwinkuijntjes.hours.ui.screens.SettingsScreen
import com.gerwinkuijntjes.hours.ui.theme.HoursTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HoursTheme {
                HoursApp()
            }
        }
    }
}

object Route {
    const val DAY = "day"
    const val OVERVIEW = "overview"
    const val SETTINGS = "settings"
    const val CLIENT = "settings/client/{clientId}"
    const val BACKUP = "settings/backup"
    const val FOLDER_PICKER = "settings/backup/folder"

    fun client(id: String) = "settings/client/$id"
}

private enum class Tab(val route: String, val labelRes: Int, val icon: ImageVector) {
    Day(Route.DAY, R.string.tab_today, Icons.Default.EventAvailable),
    Overview(Route.OVERVIEW, R.string.tab_overview, Icons.Default.BarChart),
    Settings(Route.SETTINGS, R.string.tab_settings, Icons.Default.Settings)
}

@Composable
fun HoursApp() {
    val viewModel: HoursViewModel = viewModel()
    val nav = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val message by viewModel.messages.collectAsState()
    val locale = currentLocale()

    val currentRoute = nav.currentBackStackEntryAsState().value?.destination?.route
    val onTopLevel = Tab.entries.any { it.route == currentRoute }

    val messageText = message?.let { textFor(it, locale) }
    LaunchedEffect(message) {
        messageText?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            // Detail screens take the whole window; the tab bar belongs to the
            // three destinations you can actually switch between.
            if (onTopLevel) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                    Tab.entries.forEach { entry ->
                        NavigationBarItem(
                            selected = currentRoute == entry.route,
                            onClick = { nav.switchTab(entry.route) },
                            icon = { Icon(entry.icon, contentDescription = null) },
                            label = { Text(stringResource(entry.labelRes)) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Route.DAY,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(Route.DAY) { DayScreen(viewModel, padding) }

            composable(Route.OVERVIEW) {
                OverviewScreen(viewModel, padding) { nav.switchTab(Route.DAY) }
            }

            composable(Route.SETTINGS) {
                SettingsScreen(
                    viewModel = viewModel,
                    contentPadding = padding,
                    onOpenClient = { nav.navigate(Route.client(it)) },
                    onOpenBackup = { nav.navigate(Route.BACKUP) }
                )
            }

            composable(
                route = Route.CLIENT,
                enterTransition = { slideIn() },
                exitTransition = { slideOut() }
            ) { entry ->
                ClientDetailScreen(
                    viewModel = viewModel,
                    clientId = entry.arguments?.getString("clientId").orEmpty(),
                    onBack = { nav.popBackStack() }
                )
            }

            composable(
                route = Route.FOLDER_PICKER,
                enterTransition = { slideIn() },
                exitTransition = { slideOut() }
            ) {
                FolderPickerScreen(
                    viewModel = viewModel,
                    onPick = {
                        viewModel.setFolderPath(it)
                        nav.popBackStack()
                    },
                    onBack = { nav.popBackStack() }
                )
            }

            composable(
                route = Route.BACKUP,
                enterTransition = { slideIn() },
                exitTransition = { slideOut() }
            ) {
                BackupScreen(
                    viewModel = viewModel,
                    onOpenFolderPicker = { nav.navigate(Route.FOLDER_PICKER) },
                    onBack = { nav.popBackStack() }
                )
            }
        }
    }
}

/**
 * Tab switches push onto the back stack, so Back returns to wherever you came
 * from, including the overview you tapped a visit in. Only the day screen, the
 * start destination, exits the app.
 *
 * [launchSingleTop] keeps repeated taps on the same tab from stacking up.
 */
private fun NavHostController.switchTab(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) { launchSingleTop = true }
}

private fun AnimatedContentTransitionScope<*>.slideIn() =
    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(280))

private fun AnimatedContentTransitionScope<*>.slideOut() =
    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(280))

@Composable
private fun textFor(message: UiMessage, locale: java.util.Locale): String = when (message) {
    is UiMessage.Saved -> stringResource(
        R.string.saved_toast,
        message.clientName,
        formatMoney(message.amount, locale)
    )
    is UiMessage.Updated -> stringResource(R.string.updated_toast)
    is UiMessage.Deleted -> stringResource(R.string.deleted_toast)
    is UiMessage.Erased -> stringResource(R.string.erased)
    is UiMessage.BackupSucceeded -> stringResource(R.string.backup_succeeded)
    is UiMessage.BackupFailed -> stringResource(R.string.last_error, davErrorText(message.error))
    is UiMessage.BackupNotConfigured -> stringResource(
        when (message.problem) {
            BackupSettings.Problem.NO_FOLDER -> R.string.backup_needs_folder
            BackupSettings.Problem.NOT_SECURE -> R.string.backup_needs_https
            BackupSettings.Problem.NO_CREDENTIALS -> R.string.backup_needs_login
            BackupSettings.Problem.NONE -> R.string.backup_not_configured
        }
    )
    is UiMessage.BackupNothingToDo -> stringResource(R.string.backup_nothing_to_do)
    is UiMessage.ExportDone -> stringResource(R.string.export_done)
    is UiMessage.RestoreDone -> stringResource(
        R.string.restore_done,
        message.clients,
        message.visits
    )
    is UiMessage.RestoreFailed -> stringResource(R.string.restore_failed)
    is UiMessage.EnterHoursFirst -> stringResource(R.string.enter_hours_first)
    is UiMessage.AmountNotValid -> stringResource(R.string.amount_not_valid)
}
