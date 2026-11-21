package com.apollo.overload.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import android.content.Context
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apollo.overload.data.PlanDay
import com.apollo.overload.data.PlanExercise
import com.apollo.overload.data.Repository
import com.apollo.overload.domain.PaceStatus
import com.apollo.overload.ui.common.Eyebrow
import com.apollo.overload.ui.common.Gutter
import com.apollo.overload.ui.common.Hairline
import com.apollo.overload.ui.common.Segmented
import com.apollo.overload.ui.common.kg
import com.apollo.overload.ui.theme.LocalPalette
import com.apollo.overload.ui.theme.Type
import com.apollo.overload.ui.theme.archivo
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
private fun fmtDate(t: Long, f: DateTimeFormatter = dateFmt) = f.format(Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()))

@Composable
fun ProgressScreen(repo: Repository) {
    val sets by repo.dao.allSets().collectAsStateWithLifecycle(null)
    val workouts by repo.dao.workouts().collectAsStateWithLifecycle(null)
    val exercises by repo.dao.exercises().collectAsStateWithLifecycle(null)
    val split by repo.dao.activeSplit().collectAsStateWithLifecycle(null)
    // Active split's days and plan exercises; reloaded when the split or the logged workouts change.
    val plan by produceState<Pair<List<PlanDay>, List<PlanExercise>>?>(null, split?.id, workouts) {
        val id = split?.id
        val days = if (id == null) emptyList() else repo.dao.allDays().filter { it.splitId == id }
        val dayIds = days.map { it.id }.toSet()
        value = days to repo.dao.allPlanExercises().filter { it.dayId in dayIds }
    }
    val s = sets ?: return; val w = workouts ?: return; val e = exercises ?: return
    val (days, planEx) = plan ?: return
    val p = LocalPalette.current

    var range by rememberSaveable { mutableStateOf(Range.W12) }
    var groupKey by rememberSaveable { mutableStateOf<Long?>(null) }
    var liftId by rememberSaveable { mutableStateOf<String?>(null) }
    var selWorkout by rememberSaveable { mutableStateOf<Long?>(null) }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    val prefs = LocalContext.current.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    var typical by remember { mutableStateOf(prefs.getBoolean(PREF_TYPICAL, false)) }
    val groups = remember(days, planEx, s, e) { liftGroups(days, planEx, s, e).filter { it.lifts.isNotEmpty() } }
    val group = groups.firstOrNull { it.key == groupKey } ?: groups.firstOrNull { it.key == defaultGroupKey(groups, w) }
    val lift = group?.lifts?.firstOrNull { it.id == liftId } ?: group?.lifts?.firstOrNull()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = Gutter).padding(top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Eyebrow(range.eyebrow)
                Text("Strength", style = Type.title, color = p.ink, modifier = Modifier.padding(top = 4.dp))
            }
            if (lift != null) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Range.entries.forEach { r -> Chip(r.label, range == r) { range = r } }
            }
        }

        if (group == null || lift == null) {
            Text(
                "No sets logged yet. Your working weights, records and past workouts show up here after your first session.",
                style = Type.body, color = p.mute,
            )
            return@Column
        }

        // Lift picker: the active split's days (plus "Other"), then that day's exercises in plan order.
        val groupIndex = groups.indexOf(group)
        val pickGroup: (Int) -> Unit = { i -> groupKey = groups[i].key; liftId = null; selWorkout = null }
        if (groups.size <= 5) {
            Segmented(groups.map { it.name }, groupIndex, pickGroup)
        } else {
            ChipRow { groups.forEachIndexed { i, g -> Chip(g.name, i == groupIndex) { pickGroup(i) } } }
        }
        // Expected progress of every lift of the day (plan days only; "Other" has no schedule).
        val today = LocalDate.now()
        val paces = remember(s, w, group.key, days, planEx, today) {
            group.lifts.associate { it.id to liftPace(it.id, group.key, s, w, days, planEx) }
        }
        val status = { id: String -> paces[id]?.model?.status }
        if (group.key != OTHER && group.lifts.any { status(it.id) != null }) {
            DaySummary(group.name, group.lifts.map { it to status(it.id) }, lift.id, { liftId = it; selWorkout = null }) { sheet = SHEET_DAY }
        }
        ChipRow {
            group.lifts.forEach { l -> Chip(l.name, l.id == lift.id, status(l.id)) { liftId = l.id; selWorkout = null } }
        }

        val since = range.days?.let { System.currentTimeMillis() - it * 86_400_000L }
        val stats = remember(s, w, lift.id, range) { liftStats(lift.id, s, w, since) }
        val selected = stats.points.firstOrNull { it.workoutId == selWorkout } ?: stats.points.lastOrNull()
        val pace = paces.getValue(lift.id)
        val overlay = remember(pace, typical) { chartOverlay(pace, typical) }
        Hero(lift.name, stats, selected, pace, overlay, onPace = { sheet = SHEET_LIFT }) { selWorkout = it }
        if (stats.points.isNotEmpty()) TypicalToggle(typical) {
            typical = !typical
            prefs.edit().putBoolean(PREF_TYPICAL, typical).apply()
        }

        val pr = remember(s, lift.id) { personalRecord(lift.id, s) }
        val max = remember(s, lift.id) { estimatedMax(lift.id, s) }
        StatsRow(
            listOf(
                StatCell(pr?.let { "${kg(it.kg)} kg × ${it.reps}" } ?: "–", "PR", pr?.let { fmtDate(it.loggedAt) }, 1.8f),
                StatCell(max?.let { "${Math.round(it)} kg" } ?: "–", "Est. max", "1 rep", 1.2f),
                StatCell(stats.points.size.toString(), "Sessions"),
            )
        )

        val recs = remember(s, e) { records(s, e) }
        Section("Records", "heaviest set") {
            recs.forEachIndexed { i, r ->
                if (i > 0) Hairline()
                ListRow(r.name, "${kg(r.kg)} kg × ${r.reps}", fmtDate(r.time))
            }
        }

        val recent = remember(s, w) { recentWorkouts(s, w) }
        Section("Recent workouts", null) {
            recent.forEachIndexed { i, ws ->
                if (i > 0) Hairline()
                val wo = ws.workout
                val duration = wo.finishedAt?.let { "${((it - wo.startedAt) / 60_000).coerceAtLeast(1)} min" } ?: "in progress"
                ListRow(wo.dayName, "${ws.sets} sets", "${fmtDate(wo.startedAt, dayFmt)} · $duration")
            }
        }

        when (sheet) {
            SHEET_LIFT -> LiftPaceSheet(lift.name, group.name, pace) { sheet = null }
            SHEET_DAY -> DayPaceSheet(group.name, group.lifts.mapNotNull { l -> paces[l.id]?.let { l to it } }) { sheet = null }
        }
    }
}

private const val PREFS = "settings"
private const val PREF_TYPICAL = "progress_typical_range"
private const val SHEET_LIFT = "lift"
private const val SHEET_DAY = "day"

@Composable
private fun Hero(
    name: String, stats: LiftStats, selected: WorkPoint?, pace: LiftPace, overlay: ChartOverlay, onPace: () -> Unit,
    onSelect: (Long) -> Unit,
) {
    val p = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                EyebrowLine("Working weight · $name")
                val last = stats.points.lastOrNull()
                val small = SpanStyle(fontSize = 22.sp, fontFamily = archivo(850, 100f), letterSpacing = 0.sp, color = p.mute)
                Text(
                    buildAnnotatedString {
                        append(last?.let { kg(it.kg) } ?: "–")
                        withStyle(small) { append(" kg") }
                        if (last != null) withStyle(small) { append(" × ${last.reps}") }
                    },
                    Modifier.padding(top = 8.dp), style = Type.display(64.sp, 850, 85f, -0.03f, 58.sp), color = p.ink,
                )
            }
            if (stats.points.size > 1) {
                val first = stats.points.first().kg; val last = stats.points.last().kg
                val d = last - first
                val sign = if (d > 0) "+" else if (d < 0) "−" else "±"
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "$sign${kg(kotlin.math.abs(d))} kg", style = Type.display(18.sp, 800),
                        color = if (d > 0) p.good else p.mute,
                    )
                    Text(
                        "since ${fmtDate(stats.points.first().time)}",
                        style = Type.small, color = p.mute, textAlign = TextAlign.End,
                    )
                }
            }
        }
        PaceLine(pace, onPace)
        if (stats.points.isEmpty() || selected == null) {
            Text("No sessions of this lift in this range.", style = Type.small, color = p.mute)
        } else {
            Text(
                buildAnnotatedString {
                    append("${fmtDate(selected.time, dayFmt)} · ")
                    withStyle(SpanStyle(color = p.ink, fontWeight = FontWeight.Bold)) { append("${kg(selected.kg)} kg") }
                    append(if (selected.time < overlay.loggedFrom) " · estimated" else " · ${selected.allReps.joinToString(", ")}")
                },
                style = Type.small, color = p.ink2, maxLines = 1,
            )
            WeightChart(stats.points, selected.workoutId, onSelect, overlay = overlay)
        }
    }
}

@Composable
private fun EyebrowLine(text: String) {
    Text(text.uppercase(), style = Type.eyebrow, color = LocalPalette.current.mute, maxLines = 1)
}

private class StatCell(val value: String, val label: String, val sub: String? = null, val weight: Float = 1f)

@Composable
private fun StatsRow(cells: List<StatCell>) {
    val p = LocalPalette.current
    Column {
        Hairline()
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            cells.forEachIndexed { i, c ->
                if (i > 0) Spacer(Modifier.width(1.dp).fillMaxHeight().background(p.line))
                Column(
                    Modifier.weight(c.weight).padding(start = if (i == 0) 0.dp else 12.dp, top = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(c.value, style = Type.display(22.sp, 800, lineHeight = 24.sp), color = p.ink, maxLines = 1)
                    EyebrowLine(if (c.sub != null) "${c.label} · ${c.sub}" else c.label)
                }
            }
        }
        Hairline()
    }
}

@Composable
private fun Section(title: String, sub: String?, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, Modifier.weight(1f), style = Type.display(17.sp, 800, tracking = -0.01f), color = p.ink)
            if (sub != null) Text(sub, style = Type.small, color = p.mute)
        }
        content()
    }
}

/** Mockup .recs row: bold name + right-aligned value, small mute line below. */
@Composable
private fun ListRow(name: String, value: String, sub: String) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(name, Modifier.weight(1f), style = Type.body.copy(fontWeight = FontWeight.Bold), color = p.ink)
            Text(value, style = Type.body, color = p.ink2)
        }
        Text(sub, style = Type.small.copy(fontSize = 12.sp), color = p.mute)
    }
}

/** Horizontally scrolling chip row, edge to edge like the mockup's .chips. */
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().layout { m, c ->
            val extra = Gutter.roundToPx() * 2
            val pl = m.measure(c.copy(minWidth = c.maxWidth + extra, maxWidth = c.maxWidth + extra))
            layout(c.maxWidth, pl.height) { pl.place(-extra / 2, 0) }
        }.horizontalScroll(rememberScrollState()).padding(horizontal = Gutter),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

@Composable
private fun Chip(text: String, on: Boolean, status: PaceStatus? = null, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(999.dp)
    Row(
        Modifier.clip(shape).background(if (on) p.ink else Color.Transparent).border(1.dp, if (on) p.ink else p.line2, shape)
            .clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (status != null) StatusDot(status, onInk = on)
        Text(text, style = Type.small.copy(fontWeight = FontWeight.SemiBold), color = if (on) p.onInk else p.ink2, maxLines = 1)
    }
}
