package com.apollo.overload.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.apollo.overload.MainActivity
import com.apollo.overload.OverloadApp
import com.apollo.overload.R
import com.apollo.overload.data.Exercise
import com.apollo.overload.data.PlanDay
import com.apollo.overload.data.PlanExercise
import com.apollo.overload.data.Repository
import com.apollo.overload.data.SetEntry
import com.apollo.overload.data.Workout
import com.apollo.overload.data.nextDay
import com.apollo.overload.ui.common.kg
import com.apollo.overload.ui.workout.lastTimeText
import com.apollo.overload.ui.workout.load
import com.apollo.overload.ui.workout.shortName

class OverloadWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OverloadWidget()
}

/**
 * Home-screen widget (mockup/widget-v2.html): the next workout, the set to log with − / + and Log set, "Next up"
 * between exercises, and "done". Values come from the same domain logic as the session screen ([WidgetModel]).
 */
class OverloadWidget : GlanceAppWidget() {

    // Exact: the layout is built for the size the launcher really gives (cells differ a lot between launchers),
    // and it fills that size instead of snapping to the nearest smaller breakpoint.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as OverloadApp
        app.ready.await()
        val repo = app.repo
        val first = Snapshot.load(repo)
        // Redraws on any change to the workout data while the widget session is alive.
        val changes = repo.db.invalidationTracker.createFlow(*TABLES)
        provideContent {
            val prefs = currentState<Preferences>()
            val snap by produceState(first) { changes.collect { value = Snapshot.load(repo) } }
            val draft = WidgetPrefs.draft(prefs)
            val model = remember(snap, draft) { snap.model(draft) }
            Content(model, flash = prefs[WidgetPrefs.flash] == true)
        }
    }

    companion object {
        private val TABLES = arrayOf("workout", "set_entry", "plan_exercise", "plan_day", "split", "exercise")
    }
}

/** The database rows the widget needs, read in one go. */
private class Snapshot(
    val workout: Workout?, val dayPlan: List<PlanExercise>, val sets: List<SetEntry>,
    val exercises: Map<String, Exercise>, val last: Map<String, List<SetEntry>>,
    val nextDay: PlanDay?, val nextDayRows: List<PlanExercise>,
) {
    fun model(draft: Draft?) = WidgetModel.derive(workout, dayPlan, sets, exercises, last, draft, nextDay, nextDayRows, ::shortName)

    companion object {
        suspend fun load(repo: Repository): Snapshot {
            val dao = repo.dao
            val exercises = dao.allExercises().associateBy { it.id }
            val w = dao.openWorkoutNow()
            if (w == null) {
                val day = repo.nextDay()
                return Snapshot(null, emptyList(), emptyList(), exercises, emptyMap(), day, day?.let { dao.dayExercisesNow(it.id) }.orEmpty())
            }
            val plan = w.todayPlan(dao.dayExercisesNow(w.dayId))
            val last = plan.filter { it.id in w.order }.map { it.exerciseId }.distinct().associateWith { dao.lastSessionSets(it, w.id) }
            return Snapshot(w, plan, dao.setsNow(w.id), exercises, last, null, emptyList())
        }
    }
}

/* ---------- look ---------- */

private object C {
    val ink = ColorProvider(R.color.wdg_ink)
    val ink2 = ColorProvider(R.color.wdg_ink2)
    val mute = ColorProvider(R.color.wdg_mute)
    val line = ColorProvider(R.color.wdg_line)
    val onInk = ColorProvider(R.color.wdg_on_ink)
    val h2 = ColorProvider(R.color.wdg_h2)
    val good = ColorProvider(R.color.wdg_good)
}

/** Archivo can't be loaded into RemoteViews; the system condensed face stands in for the display type. */
private val Condensed = FontFamily("sans-serif-condensed")

private fun text(size: Float, color: ColorProvider, bold: Boolean = false, display: Boolean = false) = TextStyle(
    color = color, fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    fontFamily = if (display) Condensed else null,
)

private fun num(v: Double) = if (v == 0.0) "BW" else kg(v)

/** Opens the app like its launcher icon does, so an open task comes to the front instead of a second copy. */
private fun openApp(context: Context): Action = actionStartActivity(
    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(ComponentName(context, MainActivity::class.java))
)

/**
 * The real size the launcher gives the widget (SizeMode.Exact), in dp, and the content box inside the padding.
 * Layouts are picked by what fits, not by cell counts, since cells differ per launcher
 * (One UI: about 73 × 127 dp; Pixel: about 70 × 100 dp).
 */
private class Fit(width: Float, height: Float, val fs: Float) {
    /** Content width / height inside the widget padding. */
    val cw = width - 28f
    val ch = height - 25f
    /** One row high (One UI 109 dp, Pixel about 100 dp or less). */
    val compact = height < 150f
    /** Three rows or more (One UI 364 dp, Pixel about 290 dp). */
    val tall = height >= 280f
    /** Room for the reps-per-set chips next to the "Next:" line. */
    val chips = cw >= 380f
    val narrow = cw < 300f

    /** Height of one text line set in [sp], in dp (font padding included). */
    fun line(sp: Float) = sp * fs * 1.3f

    /** Rough width of [chars] characters set in [sp], in dp. Condensed digits run about half an em. */
    fun textW(chars: Int, sp: Float, em: Float = 0.52f) = chars * sp * fs * em

    /** Height of the "Name · SET 1 OF 3" header. */
    fun header(sp: Float = 20f) = line(sp)
    val foot get() = line(12f).coerceAtLeast(20f)
    val rung get() = 12f + line(9.5f) + line(14f) + line(10f)

    /** Largest scale in [lo]..[hi] (step 0.05) for which [fits] holds, else [lo]. */
    fun scale(lo: Float = 0.7f, hi: Float = 1.6f, fits: (Float) -> Boolean): Float {
        var k = hi
        while (k > lo && !fits(k)) k -= 0.05f
        return k.coerceAtLeast(lo)
    }
}

@Composable
private fun Content(m: WidgetModel, flash: Boolean) {
    val size = LocalSize.current
    val context = LocalContext.current
    val fit = Fit(size.width.value, size.height.value, context.resources.configuration.fontScale)
    Column(
        GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(26.dp)
            .background(ImageProvider(R.drawable.wdg_bg)).clickable(openApp(context))
            .padding(start = 14.dp, end = 14.dp, top = 13.dp, bottom = 12.dp)
    ) {
        when (m) {
            is WidgetModel.Empty -> EmptyState(m)
            is WidgetModel.Idle -> IdleState(m, fit)
            is WidgetModel.Running -> RunningState(m, fit, flash)
            is WidgetModel.NextUp -> NextUpState(m, fit)
            is WidgetModel.Done -> DoneState(m, fit)
        }
    }
}

@Composable
private fun Gap(h: Int = 8) = Spacer(GlanceModifier.height(h.dp))

@Composable
private fun ColumnScope.Push() = Spacer(GlanceModifier.defaultWeight())

@Composable
private fun Eyebrow(t: String, color: ColorProvider = C.mute) = Text(t.uppercase(), style = text(11f, color, bold = true), maxLines = 1)

@Composable
private fun Header(name: String, tag: String, tagColor: ColorProvider = C.h2, size: Float = 20f) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(name, GlanceModifier.defaultWeight(), style = text(size, C.ink, bold = true, display = true), maxLines = 1)
        Text(tag.uppercase(), GlanceModifier.padding(start = 8.dp), style = text(11f, tagColor, bold = true), maxLines = 1)
    }
}

/** "Label <b>value</b>" foot text. */
@Composable
private fun Foot(label: String, value: String? = null, modifier: GlanceModifier = GlanceModifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = text(12f, C.mute), maxLines = 1)
        if (value != null) Text(value, style = text(12f, C.ink2, bold = true), maxLines = 1)
    }
}

@Composable
private fun Rule() = Box(GlanceModifier.fillMaxWidth().height(1.dp).background(C.line)) {}

/** Every button (Start, Log set) has the same size, so they look alike across states. */
private const val BTN_H = 46f
private const val BTN_W = 132f

/** Ink button; [height] in dp. [iconOnly] drops the label (narrow one-row widget). */
@Composable
private fun Pill(
    label: String, action: Action?, full: Boolean = false, ok: Boolean = false, icon: Int? = null,
    height: Float = BTN_H, iconOnly: Boolean = false,
) {
    var mod = GlanceModifier.height(height.dp).background(ImageProvider(if (ok) R.drawable.wdg_btn_ok else R.drawable.wdg_btn))
    mod = if (iconOnly) mod.width(height.dp) else if (full) mod.fillMaxWidth() else mod.width(BTN_W.dp)
    if (action != null) mod = mod.clickable(action)
    Box(if (iconOnly) mod else mod.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Image(ImageProvider(icon), if (iconOnly) label else null, GlanceModifier.size(17.dp))
                if (!iconOnly) Spacer(GlanceModifier.width(7.dp))
            }
            if (!iconOnly) Text(label, style = text(14.5f, C.onInk, bold = true), maxLines = 1)
        }
    }
}

/** How many list rows of [rowH] dp fit in [avail] dp, at most [max]. */
private fun rowsThatFit(avail: Float, rowH: Float, max: Int) = (avail / rowH).toInt().coerceIn(0, minOf(max, 10))

/* ---------- states ---------- */

@Composable
private fun ColumnScope.EmptyState(m: WidgetModel.Empty) {
    Eyebrow("Overload")
    Gap(6)
    Text("No plan yet", style = text(20f, C.ink, bold = true, display = true), maxLines = 1)
    Gap(6)
    Text(
        if (m.dayName != null) "${m.dayName} has no exercises yet. Add some in Plan." else "Open the app to set up your split.",
        style = text(12.5f, C.mute), maxLines = 2,
    )
}

@Composable
private fun ColumnScope.IdleState(m: WidgetModel.Idle, fit: Fit) {
    val start = actionRunCallback<StartWorkoutAction>()
    if (fit.compact) {
        Push()
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                Eyebrow("Next workout · today")
                Text(m.dayName, style = text(22f, C.ink, bold = true, display = true), maxLines = 1)
            }
            Pill("Start", start)
        }
        Push()
        return
    }
    val nameSp = if (fit.tall) 30f else 26f
    // Exercise rows fill whatever is left between the day name and the Start row.
    val rowH = fit.line(12.5f) + 7f
    val avail = fit.ch - fit.line(11f) - 6f - fit.line(nameSp) - 8f - 46f - 8f
    val rows = rowsThatFit(avail, rowH, m.exercises.size)
    Eyebrow("Next workout · today")
    Gap(6)
    Text(m.dayName, style = text(nameSp, C.ink, bold = true, display = true), maxLines = 1)
    Gap()
    if (rows < 2) {
        m.exercises.first().let { Foot("First: ", "${it.name} · ${load(it.kg)}") }
    } else {
        RuledList(m.exercises.take(rows)) { x ->
            ListRow(x.name, if (!fit.narrow) "${load(x.kg)} · ${x.sets}×${x.repMin}–${x.repMax}" else load(x.kg))
        }
    }
    Push()
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Foot("${m.exercises.size} exercises · ${m.totalSets} sets", modifier = GlanceModifier.defaultWeight())
        Pill("Start", start)
    }
}

@Composable
private fun ListRow(name: String, right: String, rightColor: ColorProvider = C.ink2, check: Boolean = false) {
    Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        if (check) {
            Image(ImageProvider(R.drawable.ic_wdg_check_good), null, GlanceModifier.size(12.dp))
            Spacer(GlanceModifier.width(4.dp))
        }
        Text(name, GlanceModifier.defaultWeight(), style = text(12.5f, C.ink2), maxLines = 1)
        Text(right, GlanceModifier.padding(start = 10.dp), style = text(12.5f, rightColor, bold = true, display = true), maxLines = 1)
    }
}

/** Size of the big kg × reps block ([Values]) at scale [k], in dp. */
private fun Fit.valuesH(k: Float) = line(40f * k) + 4f + 28f * k
private fun Fit.valuesW(m: WidgetModel.Running, k: Float): Float {
    val steps = 2 * 34f * k + 6f * k
    val kgW = textW(num(m.kg).length, 40f * k, 0.5f) + (if (m.kg == 0.0) 0f else textW(2, 15f) + 2f)
    val repsW = textW("${m.reps}".length, 40f * k, 0.5f)
    return maxOf(steps, kgW) + maxOf(steps, repsW) + 6f + textW(1, 18f * k)
}

@Composable
private fun ColumnScope.RunningState(m: WidgetModel.Running, fit: Fit, flash: Boolean) {
    val setTag = if (fit.cw < 220f) "Set ${m.setIndex + 1}/${m.setCount}" else "Set ${m.setIndex + 1} of ${m.setCount}"
    val log = actionRunCallback<LogAction>(
        actionParametersOf(
            WidgetParams.workout to m.workoutId, WidgetParams.planEx to m.planExId, WidgetParams.setIndex to m.setIndex,
            WidgetParams.kg to m.kg, WidgetParams.reps to m.reps,
        )
    )
    @Composable fun LogBtn(full: Boolean, height: Float = BTN_H, iconOnly: Boolean = false) =
        if (flash) Pill("Logged", null, full, ok = true, icon = R.drawable.ic_wdg_check_on_ink, height = height, iconOnly = iconOnly)
        else Pill("Log set", log, full, icon = R.drawable.ic_wdg_check_on_ink, height = height, iconOnly = iconOnly)
    @Composable fun NextFoot() = if (m.nextName != null) Foot("Next: ", m.nextName) else Foot("Last exercise")
    @Composable fun LastLine() = m.last?.let { (k, r) -> Foot("Last ", "${num(k)} × $r") } ?: Foot("First time")

    if (fit.compact) {
        // One row: name and set, then kg × reps and Log set; − / + live in the app.
        val iconOnly = fit.narrow
        val btnH = (fit.ch - fit.header(17f) - 4f).coerceIn(34f, 44f)
        val numSp = ((fit.ch - fit.header(17f) - 4f) / (fit.fs * 1.3f)).coerceIn(20f, 32f)
        Header(m.name, setTag, size = 17f)
        Push()
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val color = when { m.kgVs > 0 -> C.h2; m.kgVs < 0 -> C.mute; else -> C.ink }
            val repsColor = when { m.repsVs > 0 -> C.h2; m.repsVs < 0 -> C.mute; else -> C.ink }
            Text(num(m.kg), style = text(numSp, color, bold = true, display = true), maxLines = 1)
            if (m.kg != 0.0) Text("kg", GlanceModifier.padding(start = 2.dp), style = text(13f, C.mute), maxLines = 1)
            Text(" × ", style = text(numSp * 0.5f, C.mute, bold = true, display = true), maxLines = 1)
            Text("${m.reps}", style = text(numSp, repsColor, bold = true, display = true), maxLines = 1)
            Spacer(GlanceModifier.defaultWeight())
            LogBtn(false, btnH, iconOnly)
        }
        return
    }

    // Log set sits at the bottom: in the right corner next to the foot lines when there is room for both, else
    // full width under them. The numbers get the band in between and scale into it.
    val side = fit.cw >= 2 * BTN_W + 20f
    val minGaps = 8f * 3
    val ladder = fit.tall
    val footLines = if (ladder) 1 else 2
    val bottom = if (side) maxOf(BTN_H, footLines * fit.foot + 4f) else fit.foot + 8f + BTN_H
    val fixed = fit.header() + minGaps + bottom +
        (if (ladder) fit.rung + 8f + (if (side) 4f + 8f else 0f) else 0f)
    val k = fit.scale { k -> fit.valuesH(k) <= fit.ch - fixed && fit.valuesW(m, k) <= fit.cw }

    Header(m.name, setTag)
    Gap()
    if (ladder) {
        if (side) { Track(m.track); Gap() }
        Ladder(m)
    }
    Push()
    Row(GlanceModifier.fillMaxWidth(), horizontalAlignment = if (side) Alignment.Start else Alignment.CenterHorizontally) {
        Values(m, k)
    }
    Push()
    if (side) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                if (!ladder) {
                    if (fit.chips) Chips(m) else LastLine()
                    Gap(4)
                }
                NextFoot()
            }
            Spacer(GlanceModifier.width(10.dp))
            LogBtn(false)
        }
    } else {
        NextFoot()
        Gap()
        LogBtn(true)
    }
}

/** Big kg × reps, each with − / +, at scale [k]. Higher than last time = orange, lower = mute. */
@Composable
private fun Values(m: WidgetModel.Running, k: Float) {
    fun step(dKg: Double, dReps: Int) = actionRunCallback<StepAction>(
        actionParametersOf(
            WidgetParams.workout to m.workoutId, WidgetParams.planEx to m.planExId, WidgetParams.setIndex to m.setIndex,
            WidgetParams.kg to m.kg, WidgetParams.reps to m.reps,
            WidgetParams.stepKg to dKg, WidgetParams.stepReps to dReps,
        )
    )
    Row(verticalAlignment = Alignment.Top) {
        Value(num(m.kg), if (m.kg == 0.0) null else "kg", m.kgVs, step(-m.increment, 0), step(m.increment, 0), "Less weight", "More weight", k)
        Text("×", GlanceModifier.padding(start = (3 * k).dp, end = (3 * k).dp, top = (12 * k).dp), style = text(18f * k, C.mute, bold = true, display = true))
        Value("${m.reps}", null, m.repsVs, step(0.0, -1), step(0.0, 1), "Fewer reps", "More reps", k)
    }
}

@Composable
private fun Value(value: String, unit: String?, vs: Vs, minus: Action, plus: Action, less: String, more: String, k: Float) {
    val color = when { vs > 0 -> C.h2; vs < 0 -> C.mute; else -> C.ink }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = text(40f * k, color, bold = true, display = true), maxLines = 1)
            if (unit != null) Text(unit, GlanceModifier.padding(start = 2.dp, bottom = (6 * k).dp), style = text(15f, C.mute), maxLines = 1)
        }
        Gap(4)
        Row {
            StepButton(R.drawable.ic_wdg_minus, less, minus, k)
            Spacer(GlanceModifier.width((6 * k).dp))
            StepButton(R.drawable.ic_wdg_plus, more, plus, k)
        }
    }
}

@Composable
private fun StepButton(icon: Int, desc: String, action: Action, k: Float) {
    Box(
        GlanceModifier.size((34 * k).dp, (28 * k).dp).background(ImageProvider(R.drawable.wdg_step)).clickable(action),
        contentAlignment = Alignment.Center,
    ) { Image(ImageProvider(icon), desc, GlanceModifier.size((15 * k.coerceAtMost(1.3f)).dp)) }
}


/** Up to [n] items around [current], so containers stay within Glance's child limit. */
private fun <T> window(items: List<T>, current: Int, n: Int): List<T> {
    if (items.size <= n) return items
    val from = (current - 1).coerceIn(0, items.size - n)
    return items.subList(from, from + n)
}

/** Reps per set: done ✓reps, now (orange), to do. */
@Composable
private fun Chips(m: WidgetModel.Running) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        window(m.rungs, m.setIndex, 6).forEach { r ->
            Box(GlanceModifier.padding(end = 4.dp)) {
                Row(
                    GlanceModifier.background(
                        ImageProvider(
                            when (r.state) {
                                RungState.Done -> R.drawable.wdg_chip_done
                                RungState.Now -> R.drawable.wdg_chip_now
                                RungState.Upcoming -> R.drawable.wdg_chip
                            }
                        )
                    ).padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (r.state == RungState.Done) Image(ImageProvider(R.drawable.ic_wdg_check_ink2), null, GlanceModifier.size(10.dp))
                    Text(
                        "${r.reps}", style = text(
                            11.5f, when (r.state) { RungState.Done -> C.ink2; RungState.Now -> C.h2; RungState.Upcoming -> C.mute },
                            bold = true, display = true,
                        ), maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Set ladder: one rung per set (at most four around the current one). */
@Composable
private fun Ladder(m: WidgetModel.Running) {
    val rungs = window(m.rungs, m.setIndex, 4)
    Row(GlanceModifier.fillMaxWidth()) {
        rungs.forEachIndexed { k, r ->
            Box(GlanceModifier.defaultWeight().padding(end = if (k < rungs.size - 1) 5.dp else 0.dp)) { Rung(r) }
        }
    }
}

@Composable
private fun Rung(r: Rung) {
    val bg = when (r.state) {
        RungState.Done -> R.drawable.wdg_rung_done
        RungState.Now -> R.drawable.wdg_rung_now
        RungState.Upcoming -> R.drawable.wdg_rung
    }
    val (sub, subColor) = when (r.state) {
        RungState.Done -> if (r.top) "Top of range" to C.good else (r.lastReps?.let { "Last $it" } ?: "Done") to C.mute
        RungState.Now -> (r.lastReps?.let { "Now · last $it" } ?: "Now") to C.h2
        RungState.Upcoming -> (r.lastReps?.let { "Last $it" } ?: "Target ${r.reps}") to C.mute
    }
    Column(GlanceModifier.fillMaxWidth().background(ImageProvider(bg)).padding(horizontal = 7.dp, vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SET ${r.index + 1}", style = text(9.5f, if (r.state == RungState.Done) C.ink2 else C.mute, bold = true), maxLines = 1)
            if (r.state == RungState.Done) {
                Spacer(GlanceModifier.width(3.dp))
                Image(ImageProvider(R.drawable.ic_wdg_check_ink2), null, GlanceModifier.size(9.dp))
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(num(r.kg), style = text(14f, C.ink, bold = true, display = true), maxLines = 1)
            Text(" × ", style = text(11f, C.mute), maxLines = 1)
            Text("${r.reps}", style = text(14f, C.ink, bold = true, display = true), maxLines = 1)
        }
        Text(sub, style = text(10f, subColor, bold = r.state == RungState.Now), maxLines = 1)
    }
}

/** Exercise track: one segment per exercise, filled by sets done; the current one orange. */
@Composable
private fun Track(track: List<TrackSeg>) {
    val segs = track.take(10)
    Row(GlanceModifier.fillMaxWidth().height(4.dp)) {
        segs.forEachIndexed { k, seg ->
            Box(GlanceModifier.defaultWeight().fillMaxHeight().padding(end = if (k < segs.size - 1) 3.dp else 0.dp)) {
                Row(GlanceModifier.fillMaxSize().background(ImageProvider(if (seg.current) R.drawable.wdg_seg_now else R.drawable.wdg_seg))) {
                    val cells = seg.target.coerceIn(1, 10)
                    repeat(cells) { c ->
                        val on = c < seg.done
                        Box(GlanceModifier.defaultWeight().fillMaxHeight().let { if (on) it.background(if (seg.current) C.h2 else C.ink) else it }) {}
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.NextUpState(m: WidgetModel.NextUp, fit: Fit) {
    val start = actionRunCallback<StartExerciseAction>(actionParametersOf(WidgetParams.workout to m.workoutId, WidgetParams.planEx to m.planExId))
    Header(m.name, if (fit.narrow || fit.compact) "Next up" else "Next up · ${m.rank} of ${m.total}", size = if (fit.compact) 17f else 20f)
    @Composable fun DoneLine(modifier: GlanceModifier = GlanceModifier) = Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Image(ImageProvider(R.drawable.ic_wdg_check_good), null, GlanceModifier.size(13.dp))
        Spacer(GlanceModifier.width(5.dp))
        Text(m.prevName, style = text(12f, C.ink2, bold = true), maxLines = 1)
        Text(
            if (fit.narrow && !fit.tall) " done" else " done · " + m.prevSets.joinToString(" ") { (k, r) -> "${num(k)}×$r" },
            style = text(12f, C.mute), maxLines = 1,
        )
    }
    @Composable fun NextLine() = Row(verticalAlignment = Alignment.CenterVertically) {
        Image(ImageProvider(R.drawable.ic_wdg_up_good), null, GlanceModifier.size(13.dp))
        Spacer(GlanceModifier.width(5.dp))
        Text("${m.prevShort} next time ${load(m.prevNextKg!!)}", style = text(12f, C.good, bold = true), maxLines = 1)
    }
    // Foot lines below the load, as many as the height allows: last time, the finished exercise, its next load.
    val footH = fit.foot + 6f
    val startBeside = !fit.tall || !fit.narrow
    val fixed = fit.header() + 8f + (if (fit.compact) 0f else 8f) + (if (startBeside) 0f else BTN_H + 8f) +
        (if (fit.tall) 4f + 8f else 0f)
    if (fit.compact) {
        val k = fit.scale(lo = 0.6f, hi = 1f) { k ->
            maxOf(fit.line(34f * k), 40f) <= fit.ch - fixed && fit.bigLoadW(m, k) + 10f + BTN_W <= fit.cw
        }
        Push()
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BigLoad(m, k); Spacer(GlanceModifier.defaultWeight()); Pill("Start", start, height = 40f)
        }
        return
    }
    // Start sits in the bottom right corner next to the foot lines (or full width under them when narrow).
    val k = fit.scale(lo = 0.6f, hi = 1.5f) { k ->
        fit.line(34f * k) + (if (startBeside) maxOf(BTN_H, footH) else footH) <= fit.ch - fixed && fit.bigLoadW(m, k) <= fit.cw
    }
    val left = fit.ch - fixed - fit.line(34f * k)
    val lines = (left / footH).toInt().coerceIn(1, if (m.prevNextKg != null) 3 else 2)
    Gap()
    if (fit.tall) { Track(m.track); Gap() }
    Push()
    BigLoad(m, k)
    Push()
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(GlanceModifier.defaultWeight()) {
            if (lines >= 2) { Foot("Last ", lastTimeText(m.lastSets)); Gap(6) }
            DoneLine()
            if (lines >= 3) { Gap(6); NextLine() }
        }
        if (startBeside) { Spacer(GlanceModifier.width(10.dp)); Pill("Start", start) }
    }
    if (!startBeside) { Gap(); Pill("Start", start, full = true) }
}

private fun Fit.bigLoadW(m: WidgetModel.NextUp, k: Float) =
    textW(num(m.kg).length + "${m.sets}".length + "${m.repMin}–${m.repMax}".length, 34f * k, 0.5f) + textW(3, 14f) + 10f

/** "60kg 3×8–10" in the big condensed type, at scale [k]. */
@Composable
private fun BigLoad(m: WidgetModel.NextUp, k: Float) {
    val big = text(34f * k, C.ink, bold = true, display = true)
    val small = text(14f, C.mute)
    Row(verticalAlignment = Alignment.Bottom) {
        Text(num(m.kg), style = big, maxLines = 1)
        if (m.kg != 0.0) Text("kg", GlanceModifier.padding(start = 2.dp, end = 3.dp, bottom = (5 * k).dp), style = small, maxLines = 1)
        else Spacer(GlanceModifier.width(6.dp))
        Text("${m.sets}", style = big, maxLines = 1)
        Text("×", GlanceModifier.padding(horizontal = 2.dp, vertical = (5 * k).dp), style = small, maxLines = 1)
        Text("${m.repMin}–${m.repMax}", style = big, maxLines = 1)
    }
}

@Composable
private fun ColumnScope.DoneState(m: WidgetModel.Done, fit: Fit) {
    val ups = m.lines.filter { it.nextKg != null }
    if (fit.compact) {
        Header("${m.dayName} done", "Done", C.good, size = 22f)
        Push()
        if (ups.isNotEmpty()) {
            Text(ups.joinToString(" · ") { "${it.short} next ${load(it.nextKg!!)}" }, style = text(12f, C.good, bold = true), maxLines = 1)
            Gap(4)
        }
        Foot("${m.sets} sets · ", "Open app to finish")
        return
    }
    Header("${m.dayName} done", "Done", C.good, size = 26f)
    Gap()
    val rowH = fit.line(12.5f) + 7f
    val rows = rowsThatFit(fit.ch - fit.header(26f) - 8f - fit.foot - 8f, rowH, m.lines.size)
    if (rows < 2) {
        Foot("${m.sets} sets · ${m.lines.size} exercises")
        if (ups.isNotEmpty()) {
            Gap(4)
            Text(ups.joinToString(" · ") { "${it.short} next ${load(it.nextKg!!)}" }, style = text(12f, C.good, bold = true), maxLines = 1)
        }
        Push()
        Foot("Open app to finish")
    } else {
        // Progressions first, so the rows that fit show what changes next time.
        val shown = (ups + m.lines.filter { it.nextKg == null }).take(rows)
        RuledList(m.lines.filter { it in shown }) { x ->
            if (x.nextKg != null) ListRow(x.name, "Next ${load(x.nextKg)}", C.good, check = true)
            else ListRow(x.name, "${x.sets} sets", check = true)
        }
        Push()
        Foot("${m.sets} sets · ", "Open app to finish")
    }
}


/** Rows with a hairline between them; Glance containers take at most 10 children. */
@Composable
private fun <T> RuledList(items: List<T>, row: @Composable (T) -> Unit) {
    Column(GlanceModifier.fillMaxWidth()) {
        items.take(10).forEachIndexed { k, x ->
            Column(GlanceModifier.fillMaxWidth()) {
                if (k > 0) Rule()
                row(x)
            }
        }
    }
}
