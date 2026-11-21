package com.apollo.overload.ui.plan

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.withTransaction
import com.apollo.overload.data.Exercise
import com.apollo.overload.data.GymDao
import com.apollo.overload.data.Muscles
import com.apollo.overload.data.PlanDay
import com.apollo.overload.data.Repository
import com.apollo.overload.data.Split
import com.apollo.overload.ui.common.Eyebrow
import com.apollo.overload.ui.common.Gutter
import com.apollo.overload.ui.common.Hairline
import com.apollo.overload.ui.common.ScreenHeader
import com.apollo.overload.ui.settings.BackupSection
import com.apollo.overload.ui.theme.LocalPalette
import com.apollo.overload.ui.theme.Type
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** Plan editor: split picker + day list, and the day editor for one day. */
@Composable
fun PlanScreen(repo: Repository, onBack: () -> Unit) {
    val exercises by remember { repo.dao.exercises() }.collectAsStateWithLifecycle(emptyList())
    val exMap = remember(exercises) { exercises.associateBy { it.id } }
    var dayId by rememberSaveable { mutableStateOf<Long?>(null) }
    val open = dayId
    if (open != null) {
        BackHandler { dayId = null }
        DayEditor(repo, open, exercises, exMap, onBack = { dayId = null })
    } else {
        SplitScreen(repo, exMap, onBack, onOpenDay = { dayId = it })
    }
}

private class Template(val name: String, val sub: String, val days: List<Pair<String, String>>)

private val Templates = listOf(
    Template("ULUL", "4 days", listOf("Upper A" to "upper", "Lower A" to "lower", "Upper B" to "upper", "Lower B" to "lower")),
    Template("PPL", "3 days", listOf("Push" to "push", "Pull" to "pull", "Legs" to "legs")),
    Template("Full body", "3 days", listOf("Full A" to "full", "Full B" to "full", "Full C" to "full")),
    Template("Custom", "your days", listOf("Day 1" to "custom")),
)

/** Creates a new split from a template (never touching existing ones) and makes it active. */
private suspend fun createFromTemplate(repo: Repository, t: Template) = repo.db.withTransaction {
    val names = repo.dao.allSplits().map { it.name }.toSet()
    val name = if (t.name !in names) t.name else (2..99).map { "${t.name} $it" }.first { it !in names }
    val id = repo.dao.insertSplit(Split(name = name))
    t.days.forEachIndexed { i, (n, k) -> repo.dao.insertDay(PlanDay(splitId = id, name = n, kind = k, position = i)) }
    repo.dao.activateSplit(id)
}

/** False (nothing deleted) when the workout in progress belongs to this split. */
private suspend fun deleteSplit(repo: Repository, s: Split): Boolean = repo.db.withTransaction {
    val dao = repo.dao
    val days = dao.allDays().filter { it.splitId == s.id }
    if (days.any { it.id == dao.openWorkoutNow()?.dayId }) return@withTransaction false
    days.forEach { d -> deleteDayDeep(dao, d) }
    dao.deleteSplit(s)
    if (s.active) dao.allSplits().firstOrNull()?.let { dao.activateSplit(it.id) }
    true
}

internal const val WorkoutInProgressMsg = "Finish or discard the workout in progress first"

suspend fun deleteDayDeep(dao: GymDao, d: PlanDay) {
    dao.dayExercisesNow(d.id).forEach { dao.deletePlanExercise(it) }
    dao.deleteDay(d)
}

@Composable
private fun SplitScreen(repo: Repository, exMap: Map<String, Exercise>, onBack: () -> Unit, onOpenDay: (Long) -> Unit) {
    val dao = repo.dao
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val splits by remember { dao.splits() }.collectAsStateWithLifecycle(emptyList())
    val active = splits.firstOrNull { it.active }
    val days by remember(active?.id) { active?.let { dao.days(it.id) } ?: flowOf(emptyList()) }.collectAsStateWithLifecycle(emptyList())
    val dayCounts by produceState(emptyMap<Long, Int>(), splits, days.size) {
        value = dao.allDays().groupingBy { it.splitId }.eachCount()
    }
    var renaming by remember { mutableStateOf<Split?>(null) }
    var deleting by remember { mutableStateOf<Split?>(null) }
    var creating by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(p.bg).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        AppBar("Workout", onBack)
        ScreenHeader("Your plan", "Plan")
        Eyebrow("Select your split", Modifier.padding(horizontal = Gutter).padding(top = 10.dp, bottom = 8.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Gutter),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            splits.forEach { s ->
                SplitCard(s.name, "${dayCounts[s.id] ?: 0} days", on = s.active, template = false) {
                    if (!s.active) scope.launch { dao.activateSplit(s.id) }
                }
            }
            Templates.forEach { t ->
                SplitCard(t.name, "New · ${t.sub}", on = false, template = true) {
                    if (!creating) {
                        creating = true
                        scope.launch { try { createFromTemplate(repo, t) } finally { creating = false } }
                    }
                }
            }
        }
        if (active != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Gutter).padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Sub("Active: ${active.name}", Modifier.weight(1f))
                SmallGhost("Rename", { renaming = active }, icon = Icons.Outlined.Edit)
                if (splits.size > 1) SmallGhost("Delete", { deleting = active }, icon = Icons.Outlined.Delete)
            }
        }

        if (active != null) {
            SectionHead("Days", "${days.size} in rotation", Modifier.padding(horizontal = Gutter).padding(top = 26.dp, bottom = 12.dp))
            days.forEachIndexed { i, d -> DayRow(dao, d, i, exMap) { onOpenDay(d.id) } }
            Hairline()
            AddRow("Add day", Icons.Outlined.Add, {
                scope.launch {
                    val id = dao.insertDay(PlanDay(splitId = active.id, name = "Day ${days.size + 1}", kind = "custom", position = days.size))
                    onOpenDay(id)
                }
            }, Modifier.padding(horizontal = Gutter).padding(top = 14.dp))
        }

        BackupSection(repo, Modifier.padding(horizontal = Gutter).padding(top = 32.dp))
        Foot(
            "Changes apply right away, also to a workout in progress.",
            Modifier.padding(horizontal = Gutter).padding(top = 20.dp),
        )
    }

    renaming?.let { s ->
        TextDialog("Rename split", s.name, onSave = { n -> scope.launch { dao.updateSplit(s.copy(name = n)) } }, onDismiss = { renaming = null })
    }
    deleting?.let { s ->
        ConfirmDialog(
            "Delete ${s.name}?", "Its days and exercises are removed. Past workouts stay in your history.", "Delete",
            onConfirm = {
                scope.launch { if (!deleteSplit(repo, s)) Toast.makeText(context, WorkoutInProgressMsg, Toast.LENGTH_SHORT).show() }
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun SplitCard(name: String, sub: String, on: Boolean, template: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(14.dp)
    val base = Modifier.widthIn(min = 84.dp).clip(shape)
    val styled = when {
        on -> base.background(p.ink)
        template -> base.drawBehind {
            drawRoundRect(
                p.line2, cornerRadius = CornerRadius(14.dp.toPx()),
                style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
            )
        }
        else -> base.border(1.5.dp, p.line2, shape)
    }
    Column(
        styled.clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(if (template) "+ $name" else name, style = Type.display(14.sp, 800), color = if (on) p.onInk else p.ink, maxLines = 1)
        Text(sub, Modifier.padding(top = 4.dp), style = Type.small.copy(fontSize = 11.sp), color = if (on) p.onInk.copy(alpha = 0.7f) else p.mute, maxLines = 1)
    }
}

@Composable
private fun DayRow(dao: GymDao, day: PlanDay, index: Int, exMap: Map<String, Exercise>, onClick: () -> Unit) {
    val p = LocalPalette.current
    val rows by remember(day.id) { dao.dayExercises(day.id) }.collectAsStateWithLifecycle(emptyList())
    val sets = rows.sumOf { it.sets }
    val muscles = rows.mapNotNull { exMap[it.exerciseId]?.primaryMuscle() }.distinct().take(4).joinToString(", ") { Muscles.label[it] ?: it }
    val kind = KindLabel[day.kind] ?: day.kind
    val summary = if (rows.isEmpty()) "$kind · No exercises yet" else "$kind · ${rows.size} exercises · $sets sets · $muscles"
    Hairline()
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Gutter, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${index + 1}", Modifier.widthIn(min = 34.dp).padding(end = 12.dp), style = Type.display(22.sp, 850, 85f), color = p.mute)
        Column(Modifier.weight(1f)) {
            Text(day.name, style = Type.body.copy(fontSize = 15.5.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = p.ink, maxLines = 1)
            Text(summary, Modifier.padding(top = 3.dp), style = Type.small.copy(fontSize = 12.5.sp), color = p.mute, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = p.mute, modifier = Modifier.size(22.dp))
    }
}
