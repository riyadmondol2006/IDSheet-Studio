package io.github.riyadmondol2006.idsheetstudio.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import io.github.riyadmondol2006.idsheetstudio.ui.editor.PhotoEditorScreen
import io.github.riyadmondol2006.idsheetstudio.ui.export.ExportScreen
import io.github.riyadmondol2006.idsheetstudio.ui.home.HomeScreen
import io.github.riyadmondol2006.idsheetstudio.ui.onboarding.OnboardingScreen
import io.github.riyadmondol2006.idsheetstudio.ui.project.ProjectScreen
import io.github.riyadmondol2006.idsheetstudio.ui.settings.SettingsScreen
import io.github.riyadmondol2006.idsheetstudio.ui.theme.ExpressiveMotion
import kotlinx.serialization.Serializable

@Serializable data object OnboardingRoute
@Serializable data object HomeRoute
@Serializable data object SettingsRoute
@Serializable data class ProjectRoute(val projectId: String)
@Serializable data class EditorRoute(val projectId: String, val photoId: String)
@Serializable data class ExportRoute(val projectId: String)

/**
 * Pops only when the current destination is fully shown, so a double tap on a toolbar back button
 * during a transition cannot pop two screens at once.
 */
fun NavController.popSafely(): Boolean {
    val current = currentBackStackEntry ?: return false
    if (!current.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return false
    return popBackStack()
}

@Composable
fun AppNavHost(
    nav: NavHostController,
    startOnboarding: Boolean,
    onCheckUpdates: () -> Unit,
    checkingUpdates: Boolean,
) {
    NavHost(
        navController = nav,
        startDestination = if (startOnboarding) OnboardingRoute else HomeRoute,
        enterTransition = { slideInHorizontally(ExpressiveMotion.offset) { it / 4 } + fadeIn(tween(220)) },
        exitTransition = { slideOutHorizontally(ExpressiveMotion.offset) { -it / 8 } + fadeOut(tween(160)) },
        popEnterTransition = { slideInHorizontally(ExpressiveMotion.offset) { -it / 8 } + fadeIn(tween(220)) },
        popExitTransition = { slideOutHorizontally(ExpressiveMotion.offset) { it / 4 } + fadeOut(tween(160)) },
    ) {
        composable<OnboardingRoute> {
            OnboardingScreen(onFinished = {
                nav.navigate(HomeRoute) { popUpTo(OnboardingRoute) { inclusive = true } }
            })
        }
        composable<HomeRoute> {
            HomeScreen(
                onOpenProject = { nav.navigate(ProjectRoute(it)) { launchSingleTop = true } },
                onExportProject = { nav.navigate(ExportRoute(it)) },
                onOpenSettings = { nav.navigate(SettingsRoute) { launchSingleTop = true } },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(onBack = { nav.popSafely() }, onCheckUpdates = onCheckUpdates, checkingUpdates = checkingUpdates)
        }
        composable<ProjectRoute> { entry ->
            val route = entry.toRoute<ProjectRoute>()
            ProjectScreen(
                projectId = route.projectId,
                onBack = { nav.popSafely() },
                onEditPhoto = { nav.navigate(EditorRoute(route.projectId, it.id)) { launchSingleTop = true } },
                onExport = { nav.navigate(ExportRoute(route.projectId)) { launchSingleTop = true } },
                onOpenSettings = { nav.navigate(SettingsRoute) { launchSingleTop = true } },
            )
        }
        composable<EditorRoute> { entry ->
            val route = entry.toRoute<EditorRoute>()
            PhotoEditorScreen(
                projectId = route.projectId,
                photoId = route.photoId,
                onClose = { nav.popSafely() },
                onOpenSettings = { nav.navigate(SettingsRoute) { launchSingleTop = true } },
            )
        }
        composable<ExportRoute> { entry ->
            val route = entry.toRoute<ExportRoute>()
            ExportScreen(projectId = route.projectId, onBack = { nav.popSafely() })
        }
    }
}
