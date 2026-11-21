package com.apollo.overload.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apollo.overload.OverloadApp
import com.apollo.overload.ui.body.BodyScreen
import com.apollo.overload.ui.plan.PlanScreen
import com.apollo.overload.ui.progress.ProgressScreen
import com.apollo.overload.ui.ranking.RankingScreen
import com.apollo.overload.ui.theme.LocalPalette
import com.apollo.overload.ui.theme.Type
import com.apollo.overload.ui.workout.WorkoutScreen

enum class Tab(val label: String, val icon: ImageVector) {
    Workout("Workout", Icons.Outlined.FitnessCenter),
    Body("Body", Icons.Outlined.Accessibility),
    Progress("Progress", Icons.AutoMirrored.Outlined.TrendingUp),
    Ranking("Ranking", Icons.Outlined.BarChart),
}

@Composable
fun AppShell(app: OverloadApp) {
    val p = LocalPalette.current
    var ready by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { app.ready.await(); ready = true }
    var tab by rememberSaveable { mutableStateOf(Tab.Workout) }
    // The plan editor opens from the Workout screen's "Plan" button; tapping Workout in the nav returns to the session.
    var planOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = planOpen) { planOpen = false }
    BackHandler(enabled = !planOpen && tab != Tab.Workout) { tab = Tab.Workout }

    Column(Modifier.fillMaxSize().background(p.bg)) {
        Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) {
            if (!ready) return@Box
            when {
                tab == Tab.Workout && planOpen -> PlanScreen(app.repo, onBack = { planOpen = false })
                tab == Tab.Workout -> WorkoutScreen(app.repo, onOpenPlan = { planOpen = true })
                tab == Tab.Body -> BodyScreen(app.repo)
                tab == Tab.Progress -> ProgressScreen(app.repo)
                tab == Tab.Ranking -> RankingScreen(app.repo)
            }
        }
        HorizontalDivider(color = p.line)
        NavigationBar(containerColor = p.raise, tonalElevation = 0.dp) {
            Tab.entries.forEach { t ->
                NavigationBarItem(
                    selected = tab == t,
                    onClick = { if (t == Tab.Workout && tab == Tab.Workout) planOpen = false; tab = t },
                    icon = { Icon(t.icon, contentDescription = null) },
                    label = { Text(t.label, style = Type.small.copy(fontSize = 12.sp)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = p.ink, selectedTextColor = p.ink, indicatorColor = p.line2,
                        unselectedIconColor = p.mute, unselectedTextColor = p.mute,
                    ),
                )
            }
        }
    }
}
