package com.apollo.overload.ui.body

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apollo.overload.data.Muscles
import com.apollo.overload.data.Repository
import com.apollo.overload.ui.common.BodyFigure
import com.apollo.overload.ui.common.Eyebrow
import com.apollo.overload.ui.common.Gutter
import com.apollo.overload.ui.common.Hairline
import com.apollo.overload.ui.common.Segmented
import com.apollo.overload.ui.common.Side
import com.apollo.overload.ui.common.fmt1
import com.apollo.overload.ui.common.kg
import com.apollo.overload.ui.theme.LocalPalette
import com.apollo.overload.ui.theme.Type
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
private val dowFmt = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
private val startFmt = DateTimeFormatter.ofPattern("EEE d", Locale.ENGLISH)
private val startMonthFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

/** "Mon 16 – Sun 22 Nov", or "Mon 30 Nov – Sun 6 Dec" across a month boundary. */
private fun weekLabel(monday: LocalDate): String {
    val sunday = monday.plusDays(6)
    val start = if (monday.month == sunday.month) startFmt else startMonthFmt
    return "${start.format(monday)} – ${dayFmt.format(sunday)}"
}

private const val WEEK_MAX = 16.0
private const val DAY_MAX = 8.0

@Composable
fun BodyScreen(repo: Repository) {
    val sets by repo.dao.allSets().collectAsStateWithLifecycle(null)
    val workouts by repo.dao.workouts().collectAsStateWithLifecycle(null)
    val exercises by repo.dao.exercises().collectAsStateWithLifecycle(null)
    val today = LocalDate.now()
    val thisMonday = weekStart(today)
    // 0 = the current week, -1 = the week before, and so on.
    var weekOffset by rememberSaveable { mutableIntStateOf(0) }
    val monday = thisMonday.plusWeeks(weekOffset.toLong())
    val current = weekOffset == 0
    val data = remember(sets, workouts, exercises, monday) {
        val s = sets; val w = workouts; val e = exercises
        if (s == null || w == null || e == null) null else buildBodyData(s, w, e, monday)
    } ?: return

    var week by rememberSaveable { mutableStateOf(true) }
    // null = the default day of the shown week (today if in it, else Monday).
    var pickedDay by rememberSaveable { mutableStateOf<Int?>(null) }
    var sel by rememberSaveable { mutableStateOf<String?>(null) }
    val p = LocalPalette.current

    val dayIndex = pickedDay ?: defaultDayIndex(monday, today)
    val day = data.days[dayIndex]
    val map = if (week) data.week else day.map
    val max = if (week) WEEK_MAX else DAY_MAX
    val toggle: (String) -> Unit = { k -> sel = if (sel == k) null else k }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = Gutter).padding(top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Eyebrow(weekLabel(monday))
                Text("Muscle map", style = Type.title, color = p.ink, modifier = Modifier.padding(top = 4.dp))
            }
            WeekArrow(Icons.Outlined.ChevronLeft, "Previous week", enabled = true) { weekOffset--; pickedDay = null }
            WeekArrow(Icons.Outlined.ChevronRight, "Next week", enabled = !current) { weekOffset++; pickedDay = null }
        }

        if (!data.hasAnySets) {
            Text(
                "No sets logged yet. Finish a few sets on the Workout tab and the muscles you trained light up here.",
                style = Type.body, color = p.mute,
            )
            Footnote()
            return@Column
        }

        Segmented(listOf("Week", "Day"), if (week) 0 else 1, { week = it == 0 })
        DayStrip(data, selected = if (week) null else dayIndex, today = today) { i -> pickedDay = i; week = false }

        Row(Modifier.fillMaxWidth()) {
            for (side in listOf(Side.Front, Side.Back)) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    BodyFigure(
                        side, fill = { k -> map[k]?.takeIf { it > 0 }?.let { p.heat((it / max).toFloat()) } },
                        modifier = Modifier.heightIn(max = 360.dp), selected = sel, onTap = toggle,
                    )
                    Text(
                        side.name.uppercase(), Modifier.padding(top = 4.dp),
                        style = Type.display(10.sp, 600).copy(letterSpacing = 0.14.em), color = p.mute,
                    )
                }
            }
        }

        Ramp(max)
        Detail(sel, map, week, current, data, day.date)

        if (week) {
            val keys = Muscles.all.sortedByDescending { data.week[it] ?: 0.0 }
            val skipped = keys.filter { (data.week[it] ?: 0.0) < 4 }.reversed()
            if (skipped.isNotEmpty()) Section(if (current) "Skipped this week" else "Skipped that week", "under 4 sets") { Skips(skipped, data.week, sel, toggle) }
            Section("Sets per muscle", "band = 10–20 target") {
                Column {
                    keys.forEach { k -> VolRow(k, data.week[k] ?: 0.0, scale = 24.0, max, band = true, on = sel == k) { toggle(k) } }
                }
            }
        } else {
            val keys = day.map.keys.sortedByDescending { day.map[it] }
            val title = day.workoutNames.joinToString(" · ").ifEmpty { "Rest day" }
            Section(title, if (keys.isEmpty()) "no training" else "${keys.size} muscles") {
                if (keys.isEmpty()) Text("Rest day. Nothing logged.", style = Type.small, color = p.mute)
                else Column { keys.forEach { k -> VolRow(k, day.map[k]!!, scale = 10.0, max, band = false, on = sel == k) { toggle(k) } } }
            }
        }

        if (week) {
            val trained = data.days.filter { it.exercises.isNotEmpty() }
            if (trained.isEmpty()) Section(if (current) "This week's exercises" else "Exercises that week", "") {
                Text(if (current) "No workout logged this week." else "No workout logged that week.", style = Type.small, color = p.mute)
            }
            trained.forEach { d ->
                val name = d.workoutNames.joinToString(" · ")
                val date = if (d.date == today) "Today" else dayFmt.format(d.date)
                Section(date + if (name.isEmpty()) "" else " · $name", exerciseCount(d.exercises.size)) {
                    Column { d.exercises.forEachIndexed { i, e -> if (i > 0) Hairline(); ExerciseRow(e) } }
                }
            }
        } else {
            val exDay = day
            val exTitle = if (exDay.date == today) "Today's exercises" else "Exercises · ${dayFmt.format(exDay.date)}"
            Section(exTitle, if (exDay.exercises.isEmpty()) "" else exerciseCount(exDay.exercises.size)) {
                if (exDay.exercises.isEmpty()) {
                    Text(
                        if (exDay.date == today) "No workout today yet." else "No workout on this day.",
                        style = Type.small, color = p.mute,
                    )
                } else Column { exDay.exercises.forEachIndexed { i, e -> if (i > 0) Hairline(); ExerciseRow(e) } }
            }
        }

        Footnote()
    }
}

private fun exerciseCount(n: Int) = if (n == 1) "1 exercise" else "$n exercises"

@Composable
private fun Footnote() {
    Text(
        "Body figure: react-native-body-highlighter by Hicham El Abbassi, MIT licence. " +
            "Sets count 1 for the main muscle and 0.5 for helpers.",
        style = Type.small.copy(fontSize = 11.sp, lineHeight = 16.sp), color = LocalPalette.current.mute,
    )
}

@Composable
private fun Section(title: String, sub: String, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, Modifier.weight(1f), style = Type.display(17.sp, 800, tracking = -0.01f), color = p.ink)
            if (sub.isNotEmpty()) Text(sub, style = Type.small, color = p.mute)
        }
        content()
    }
}

@Composable
private fun DayStrip(data: BodyData, selected: Int?, today: LocalDate, onTap: (Int) -> Unit) {
    val p = LocalPalette.current
    val mx = data.days.maxOf { it.total }.coerceAtLeast(1.0)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        data.days.forEachIndexed { i, d ->
            val on = selected == i
            val isToday = d.date == today
            val future = d.date.isAfter(today)
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (on) p.ink else Color.Transparent)
                    .clickable(enabled = !future) { onTap(i) }.padding(top = 8.dp, bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    dowFmt.format(d.date).take(1), style = Type.small.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                    color = if (on) p.onInk else if (isToday) p.h2 else if (future) p.line2 else p.mute,
                )
                Text(
                    d.date.dayOfMonth.toString(), style = Type.display(17.sp, 800, lineHeight = 17.sp),
                    color = if (on) p.onInk else if (isToday) p.h2 else if (future) p.line2 else p.ink,
                )
                Box(Modifier.width(18.dp).height(22.dp), contentAlignment = Alignment.BottomCenter) {
                    val frac = if (d.total > 0) maxOf(0.12, d.total / mx).toFloat() else 0f
                    Box(
                        Modifier.fillMaxWidth().then(if (frac > 0) Modifier.fillMaxHeight(frac) else Modifier.height(2.dp))
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp, bottomStart = 1.dp, bottomEnd = 1.dp))
                            .background(if (d.total > 0) p.heat((d.total / 24).toFloat()) else p.line2)
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekArrow(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = if (enabled) p.ink else p.line2, modifier = Modifier.size(24.dp)) }
}

@Composable
private fun Ramp(max: Double) {
    val p = LocalPalette.current
    val style = Type.display(11.sp, 600)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("0", style = style, color = p.mute)
        Box(
            Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(
                Brush.horizontalGradient(
                    0f to p.body, 0.08f to p.body, 0.18f to p.h1, 0.45f to p.h2, 0.72f to p.h3, 1f to p.h4,
                )
            )
        )
        Text("${fmt1(max)}+ sets", style = style, color = p.mute)
    }
}

@Composable
private fun Detail(sel: String?, map: Map<String, Double>, week: Boolean, current: Boolean, data: BodyData, date: LocalDate) {
    val p = LocalPalette.current
    Column {
        Hairline()
        Row(
            Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            if (sel != null) {
                val v = map[sel] ?: 0.0
                Text(Muscles.label[sel] ?: sel, Modifier.weight(1f), style = Type.display(20.sp, 800, tracking = -0.01f, lineHeight = 22.sp), color = p.ink)
                val text = if (week) {
                    val trainedOn = data.days.filter { (it.map[sel] ?: 0.0) > 0 }.map { dowFmt.format(it.date) }
                    buildString {
                        append("${fmt1(v)} sets ${if (current) "this" else "that"} week · ${if (v < 10) "below" else "in"} the 10–20 target")
                        if (trainedOn.isNotEmpty()) append("\nTrained ${trainedOn.joinToString(", ")}")
                    }
                } else "${fmt1(v)} sets"
                Text(text, style = Type.small, color = p.mute, textAlign = TextAlign.End)
            } else {
                Text("Tap a muscle on the figure or in the list.", Modifier.weight(1f), style = Type.small, color = p.mute)
                Text(if (week) "Mon–Sun" else dayFmt.format(date), style = Type.small, color = p.mute)
            }
        }
        Hairline()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Skips(keys: List<String>, map: Map<String, Double>, sel: String?, onTap: (String) -> Unit) {
    val p = LocalPalette.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        keys.forEach { k ->
            val shape = RoundedCornerShape(10.dp)
            Row(
                Modifier.clip(shape)
                    .then(if (sel == k) Modifier.border(1.dp, p.ink, shape) else Modifier.border(1.dp, p.line2, shape))
                    .clickable { onTap(k) }.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom,
            ) {
                Text(Muscles.label[k] ?: k, style = Type.small.copy(fontWeight = FontWeight.SemiBold), color = p.ink2)
                Text(fmt1(map[k] ?: 0.0), style = Type.display(12.sp, 700), color = p.mute)
            }
        }
    }
}

/** Muscle row: name, bar (width = sets / [scale]), sets. [band] draws the 10–20 target band (of 24). */
@Composable
private fun VolRow(key: String, v: Double, scale: Double, max: Double, band: Boolean, on: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            Muscles.label[key] ?: key, Modifier.width(118.dp), maxLines = 1,
            style = Type.small.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Normal), color = if (on) p.ink else p.ink2,
        )
        Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(3.dp)).background(p.sunk)) {
            if (band) {
                Row(Modifier.fillMaxSize()) {
                    Spacer(Modifier.weight(0.416f))
                    Box(
                        Modifier.weight(0.416f).fillMaxHeight().background(p.ink.copy(alpha = 0.07f))
                            .border(width = 1.dp, color = p.ink.copy(alpha = 0.12f))
                    )
                    Spacer(Modifier.weight(0.168f))
                }
            }
            val frac = (v / scale).coerceIn(0.0, 1.0).toFloat()
            if (frac > 0) Box(
                Modifier.fillMaxWidth(frac).fillMaxHeight().padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(2.dp)).background(p.heat((v / max).toFloat()))
            )
        }
        Text(
            fmt1(v), Modifier.widthIn(min = 34.dp), style = Type.display(13.sp, 700),
            color = if (on) p.ink else p.ink2, textAlign = TextAlign.End,
        )
    }
}

/** An exercise done on a day: mini front + back figure coloured by its own activation, and its sets. */
@Composable
private fun ExerciseRow(e: DayExercise) {
    val p = LocalPalette.current
    val muscles = e.exercise.muscles
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (side in listOf(Side.Front, Side.Back)) {
                BodyFigure(
                    side, fill = { k -> muscles[k]?.takeIf { it > 0f }?.let { p.heat(if (it >= 1f) 1f else 0.4f) } },
                    modifier = Modifier.size(width = 38.dp, height = 72.dp),
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(e.exercise.name, style = Type.body.copy(fontWeight = FontWeight.Bold), color = p.ink)
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = p.ink2)) { append("${e.sets.size} ${if (e.sets.size == 1) "set" else "sets"}") }
                    append("  ")
                    append(e.sets.joinToString("  ") { "${kg(it.kg)}×${it.reps}" })
                },
                style = Type.small, color = p.mute,
            )
            val primary = muscles.filter { it.value >= 1f }.keys.mapNotNull { Muscles.label[it] }
            if (primary.isNotEmpty()) Text(primary.joinToString(", "), style = Type.small.copy(fontSize = 12.sp), color = p.mute)
        }
    }
}
