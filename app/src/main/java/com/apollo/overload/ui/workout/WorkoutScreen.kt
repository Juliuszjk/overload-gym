package com.apollo.overload.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apollo.overload.data.PlanExercise
import com.apollo.overload.data.Repository
import com.apollo.overload.data.startWorkout
import com.apollo.overload.domain.Rotation
import com.apollo.overload.ui.common.Eyebrow
import com.apollo.overload.ui.common.Gutter
import com.apollo.overload.ui.common.Hairline
import com.apollo.overload.ui.common.InkButton
import com.apollo.overload.ui.common.Tag
import com.apollo.overload.ui.theme.LocalPalette
import com.apollo.overload.ui.theme.Type
import com.apollo.overload.widget.WidgetSync
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private class Loaded<T>(val value: T)

@Composable
fun WorkoutScreen(repo: Repository, onOpenPlan: () -> Unit) {
    val open by remember { repo.dao.openWorkout().map { Loaded(it) } }.collectAsStateWithLifecycle(null)
    val state = open ?: return
    val w = state.value
    if (w == null) StartDay(repo, onOpenPlan)
    else key(w.id) { SessionScreen(repo, w, onOpenPlan) }
}

/** No session open: the active split's days, the next one in the rotation preselected. */
@Composable
private fun StartDay(repo: Repository, onOpenPlan: () -> Unit) {
    val p = LocalPalette.current
    val dao = repo.dao
    val scope = rememberCoroutineScope()
    val split by remember { dao.activeSplit() }.collectAsStateWithLifecycle(null)
    val splitId = split?.id
    val days by remember(splitId) { if (splitId == null) flowOf(emptyList()) else dao.days(splitId) }
        .collectAsStateWithLifecycle(emptyList())
    val workouts by remember { dao.workouts() }.collectAsStateWithLifecycle(emptyList())
    val exList by remember { dao.exercises() }.collectAsStateWithLifecycle(emptyList())
    val names = remember(exList) { exList.associate { it.id to it.name } }
    val rows by produceState(emptyMap<Long, List<PlanExercise>>(), days) {
        value = days.associate { it.id to dao.dayExercisesNow(it.id) }
    }
    val dayIds = days.map { it.id }
    val suggested = Rotation.nextDay(dayIds, workouts)
    var picked by rememberSaveable { mutableStateOf<Long?>(null) }
    val sel = days.firstOrNull { it.id == (picked?.takeIf { it in dayIds } ?: suggested) }
    val selRows = sel?.let { rows[it.id] }.orEmpty()
    var starting by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Gutter)) {
            WorkoutTop(listOfNotNull(dayLabel(System.currentTimeMillis()), split?.name).joinToString(" · "), onOpenPlan)
            if (sel == null) {
                Text("No plan yet", style = Type.exerciseName, color = p.ink, modifier = Modifier.padding(top = 6.dp))
                Text(
                    "Set up your split and its days in Plan, then start a workout here.",
                    style = Type.body, color = p.mute, modifier = Modifier.padding(top = 10.dp),
                )
                return@Column
            }
            Eyebrow(if (sel.id == suggested) "Next in rotation" else "Picked day", color = if (sel.id == suggested) p.h2 else p.mute)
            Text(sel.name, style = Type.exerciseName, color = p.ink, modifier = Modifier.padding(top = 6.dp))
            Text(
                if (selRows.isEmpty()) "No exercises yet. Add some in Plan."
                else "${selRows.size} exercises · ${selRows.sumOf { it.sets }} sets",
                style = Type.small, color = p.mute, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
            )
            selRows.forEach { r ->
                Hairline()
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        names[r.exerciseId] ?: r.exerciseId, Modifier.weight(1f), style = Type.body, color = p.ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${load(r.kg)} · ${r.sets} × ${r.repMin}–${r.repMax}",
                        style = Type.display(13.sp, 700, 95f), color = p.mute, modifier = Modifier.padding(start = 10.dp),
                    )
                }
            }
            if (selRows.isNotEmpty()) Hairline()
            Spacer(Modifier.height(24.dp))
            Eyebrow("Days · ${split?.name.orEmpty()}")
            Spacer(Modifier.height(8.dp))
            days.forEachIndexed { i, d ->
                val r = rows[d.id].orEmpty()
                val on = d.id == sel.id
                Hairline()
                Row(
                    Modifier.fillMaxWidth().clickable { picked = d.id }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier.size(28.dp).clip(CircleShape)
                            .background(if (on) p.h2 else p.bg).border(2.dp, if (on) p.h2 else p.line2, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text("${i + 1}", style = Type.display(12.sp, 800), color = if (on) p.onInk else p.mute) }
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(d.name, style = Type.body.copy(fontSize = 16.sp), color = p.ink)
                            if (d.id == suggested) Tag("Next")
                        }
                        Text(
                            if (r.isEmpty()) "No exercises yet" else "${r.size} exercises · ${r.sumOf { it.sets }} sets",
                            style = Type.small, color = p.mute,
                        )
                    }
                }
            }
            Hairline()
            Spacer(Modifier.height(16.dp))
        }
        if (sel != null) Dock {
            InkButton(
                onClick = {
                    if (starting) return@InkButton
                    starting = true
                    scope.launch {
                        // On success this screen gives way to the session; otherwise Start works again.
                        var started = false
                        try {
                            started = repo.startWorkout(sel) != null
                            WidgetSync.update(context)
                        } finally {
                            if (!started) starting = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                enabled = selRows.isNotEmpty() && !starting,
            ) { Text("Start ${sel.name}", style = Type.label.copy(fontSize = 16.sp)) }
        }
    }
}
