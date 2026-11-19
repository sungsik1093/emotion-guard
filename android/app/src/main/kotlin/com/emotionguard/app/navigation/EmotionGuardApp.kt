/**
 * 앱 내비게이션: 온보딩 → (홈 · 기록 · 캐릭터 · 설정) 하단 탭.
 * 각 기능 모듈은 서로를 모르고, 화면 사이 이동은 여기서만 연결한다.
 */
package com.emotionguard.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.emotionguard.app.report.WeeklyReportWorker
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.emotionguard.core.datastore.SettingsRepository
import com.emotionguard.feature.character.CharacterDestination
import com.emotionguard.feature.character.CharacterRoute
import com.emotionguard.feature.history.HistoryDestination
import com.emotionguard.feature.history.HistoryRoute
import com.emotionguard.feature.home.HomeDestination
import com.emotionguard.feature.home.HomeRoute
import com.emotionguard.feature.home.TestDestination
import com.emotionguard.feature.home.TestRoute
import com.emotionguard.feature.onboarding.OnboardingDestination
import com.emotionguard.feature.onboarding.OnboardingRoute
import com.emotionguard.feature.settings.DiagnosticsDestination
import com.emotionguard.feature.settings.DiagnosticsRoute
import com.emotionguard.feature.settings.SettingsDestination
import com.emotionguard.feature.settings.SettingsRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class AppViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {
    /** null이면 아직 설정을 읽는 중 */
    val onboardingDone: StateFlow<Boolean?> = settings.settings
        .map { it.onboardingDone }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

private data class TopLevel(val route: Any, val routeClass: KClass<*>, val label: String, val icon: ImageVector)

private val topLevels = listOf(
    TopLevel(HomeDestination, HomeDestination::class, "홈", Icons.Filled.Home),
    TopLevel(HistoryDestination, HistoryDestination::class, "기록", Icons.Filled.DateRange),
    TopLevel(CharacterDestination, CharacterDestination::class, "캐릭터", Icons.Filled.Face),
    TopLevel(SettingsDestination, SettingsDestination::class, "설정", Icons.Filled.Settings),
)

@Composable
fun EmotionGuardApp(appViewModel: AppViewModel = hiltViewModel()) {
    val onboardingDone by appViewModel.onboardingDone.collectAsStateWithLifecycle()
    val done = onboardingDone ?: return // 설정을 읽는 짧은 순간은 빈 화면

    val nav = rememberNavController()
    val context = LocalContext.current
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showBottomBar = topLevels.any { top -> destination?.hierarchy?.any { it.hasRoute(top.routeClass) } == true }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topLevels.forEach { top ->
                        val selected = destination?.hierarchy?.any { it.hasRoute(top.routeClass) } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(top.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(top.icon, contentDescription = null) },
                            label = { Text(top.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            NavHost(
                navController = nav,
                startDestination = if (done) HomeDestination else OnboardingDestination,
            ) {
                composable<OnboardingDestination> {
                    OnboardingRoute(onFinished = {
                        nav.navigate(HomeDestination) { popUpTo<OnboardingDestination> { inclusive = true } }
                    })
                }
                composable<HomeDestination> { HomeRoute(onOpenTest = { nav.navigate(TestDestination) }) }
                composable<TestDestination> { TestRoute(onBack = nav::popBackStack) }
                composable<HistoryDestination> { HistoryRoute() }
                composable<CharacterDestination> { CharacterRoute() }
                composable<SettingsDestination> {
                    SettingsRoute(
                        onOpenDiagnostics = { nav.navigate(DiagnosticsDestination) },
                        onSendReportNow = { WeeklyReportWorker.runNow(context) },
                    )
                }
                composable<DiagnosticsDestination> { DiagnosticsRoute(onBack = nav::popBackStack) }
            }
        }
    }
}
