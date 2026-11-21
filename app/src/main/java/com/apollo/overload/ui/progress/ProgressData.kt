package com.apollo.overload.ui.progress

import com.apollo.overload.data.Exercise
import com.apollo.overload.data.PlanDay
import com.apollo.overload.data.PlanExercise
import com.apollo.overload.data.SetEntry
import com.apollo.overload.data.Workout
import com.apollo.overload.domain.ExpectedProgress
import com.apollo.overload.domain.LiftSession
import com.apollo.overload.domain.NextJump
import com.apollo.overload.domain.PaceModel
import com.apollo.overload.domain.Progression
import com.apollo.overload.domain.Schedule
import com.apollo.overload.ui.common.kg
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

class Lift(val id: String, val name: String, val sessions: Int)

/** Lifts of one plan day in plan order, or the "Other" group ([key] = [OTHER]) of logged lifts outside the plan. */
class LiftGroup(val key: Long, val name: String, val lifts: List<Lift>)

const val OTHER = -1L

/** One workout of a lift: its working weight (heaviest set, tie -> most reps) and the reps of all its sets. */
class WorkPoint(val workoutId: Long, val time: Long, val kg: Double, val reps: Int, val allReps: List<Int>)

class LiftStats(val points: List<WorkPoint>, val sets: Int)

class Record(val name: String, val kg: Double, val reps: Int, val time: Long)

class WorkoutSummary(val workout: Workout, val sets: Int)

enum class Range(val label: String, val days: Long?, val eyebrow: String) {
    W4("4W", 28, "Last 4 weeks"), W12("12W", 84, "Last 12 weeks"), All("All", null, "All time"),
}

private val heaviest = compareBy<SetEntry> { it.kg }.thenBy { it.reps }

/** The working set of a workout: heaviest kg, ties broken by most reps. */
fun workingSet(sets: List<SetEntry>): SetEntry? = sets.maxWithOrNull(heaviest)

/** Exercises the user has logged, the ones done in the most workouts first. */
fun loggedLifts(sets: List<SetEntry>, exercises: List<Exercise>): List<Lift> {
    val byId = exercises.associateBy { it.id }
    return sets.groupBy { it.exerciseId }
        .map { (id, list) -> Lift(id, byId[id]?.name ?: id, list.map { it.workoutId }.distinct().size) }
        .sortedWith(compareByDescending<Lift> { it.sessions }.thenBy { it.name })
}

/**
 * Lift picker groups: one per day of the active split ([days] in plan order) with its exercises in plan order,
 * then "Other" for logged lifts not in the active plan (only when there are any).
 */
fun liftGroups(days: List<PlanDay>, plan: List<PlanExercise>, sets: List<SetEntry>, exercises: List<Exercise>): List<LiftGroup> {
    val byId = exercises.associateBy { it.id }
    val logged = loggedLifts(sets, exercises).associateBy { it.id }
    val groups = days.sortedBy { it.position }.map { d ->
        val ids = plan.filter { it.dayId == d.id }.sortedBy { it.position }.map { it.exerciseId }.distinct()
        LiftGroup(d.id, d.name, ids.map { id -> logged[id] ?: Lift(id, byId[id]?.name ?: id, 0) })
    }
    val inPlan = groups.flatMap { g -> g.lifts.map { it.id } }.toSet()
    val other = logged.values.filter { it.id !in inPlan }.sortedWith(compareByDescending<Lift> { it.sessions }.thenBy { it.name })
    return if (other.isEmpty()) groups else groups + LiftGroup(OTHER, "Other", other)
}

/** Group of the most recent workout's plan day, else the first group. */
fun defaultGroupKey(groups: List<LiftGroup>, workouts: List<Workout>): Long? {
    val last = workouts.maxByOrNull { it.startedAt }
    return groups.firstOrNull { it.key == last?.dayId }?.key ?: groups.firstOrNull()?.key
}

fun liftStats(liftId: String, sets: List<SetEntry>, workouts: List<Workout>, since: Long?): LiftStats {
    val start = workouts.associate { it.id to it.startedAt }
    val mine = sets.filter { it.exerciseId == liftId && (since == null || it.loggedAt >= since) }
    val points = mine.groupBy { it.workoutId }.mapNotNull { (wid, list) ->
        val done = list.filter { it.reps > 0 }.sortedWith(compareBy<SetEntry> { it.loggedAt }.thenBy { it.setIndex })
        val top = workingSet(done) ?: return@mapNotNull null
        WorkPoint(wid, start[wid] ?: list.minOf { it.loggedAt }, top.kg, top.reps, done.map { it.reps })
    }.sortedBy { it.time }
    return LiftStats(points, mine.size)
}

/** Heaviest set ever of one lift (kg, then reps; the earliest such set). */
fun personalRecord(liftId: String, sets: List<SetEntry>): SetEntry? =
    sets.filter { it.exerciseId == liftId && it.kg > 0 && it.reps > 0 }
        .maxWithOrNull(heaviest.thenByDescending { it.loggedAt })

/** Best estimated one-rep max of one lift (Epley: kg × (1 + reps / 30)), from sets of 1–12 reps. */
fun estimatedMax(liftId: String, sets: List<SetEntry>): Double? =
    sets.filter { it.exerciseId == liftId && it.kg > 0 && it.reps in 1..12 }
        .maxOfOrNull { if (it.reps == 1) it.kg else it.kg * (1 + it.reps / 30.0) }

/** Heaviest set (kg, then reps) of each exercise, newest record first. */
fun records(sets: List<SetEntry>, exercises: List<Exercise>): List<Record> {
    val byId = exercises.associateBy { it.id }
    return sets.filter { it.kg > 0 && it.reps > 0 }.groupBy { it.exerciseId }.map { (id, list) ->
        val best = list.maxWith(heaviest.thenByDescending { it.loggedAt })
        Record(byId[id]?.name ?: id, best.kg, best.reps, best.loggedAt)
    }.sortedByDescending { it.time }
}

fun recentWorkouts(sets: List<SetEntry>, workouts: List<Workout>, limit: Int = 8): List<WorkoutSummary> {
    val bySet = sets.groupBy { it.workoutId }
    return workouts.sortedByDescending { it.startedAt }.mapNotNull { w ->
        val list = bySet[w.id] ?: return@mapNotNull null
        WorkoutSummary(w, list.size)
    }.take(limit)
}

/** Expected progress of one lift (see [ExpectedProgress]); [model] or [next] are null when there are too few sessions. */
class LiftPace(
    val model: PaceModel?, val next: NextJump?, val nextDate: LocalDate?,
    /** Times of the next sessions of this lift, for the projected curve. */
    val future: List<Long>,
    /** Training weekdays the dates come from (empty: weekly from the last session). */
    val weekdays: Set<DayOfWeek>,
    /** First session ever of the lift (estimates included), the start of the typical range. */
    val firstTime: Long?, val firstKg: Double?,
    val repMax: Int,
)

/** Weeks of projected curve on the chart. */
const val PROJECT_WEEKS = 6

fun loggedFromMillis(zone: ZoneId = ZoneId.systemDefault()): Long =
    ExpectedProgress.LOGGED_FROM.atStartOfDay(zone).toInstant().toEpochMilli()

/**
 * Pace of [liftId] as done on plan day [dayId]: its rep range and increment come from [plan]; dates of the next
 * sessions follow the split rotation over the weekdays the user trains, counting every plan day with the lift.
 * Only finished workouts count; sets with implausible reps ([ExpectedProgress.plausibleReps], against the range of the
 * plan row each set was logged for, else this day's) are left out of the est. max but still set the working weight;
 * a session whose working-weight sets are all left out is dropped. A lift outside the active plan gets no model or
 * dates, just its first session.
 */
fun liftPace(
    liftId: String, dayId: Long, sets: List<SetEntry>, workouts: List<Workout>, days: List<PlanDay>, plan: List<PlanExercise>,
    now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault(),
): LiftPace {
    val finished = workouts.filter { it.finishedAt != null }
    val start = finished.associate { it.id to it.startedAt }
    val dayIds = days.sortedBy { it.position }.map { it.id }
    val pe = plan.firstOrNull { it.dayId == dayId && it.exerciseId == liftId }
    val rows = plan.associateBy { it.id }
    val sessions = sets.filter {
        it.exerciseId == liftId && it.kg > 0 && it.reps > 0 && it.workoutId in start
    }.groupBy { it.workoutId }.mapNotNull { (wid, list) ->
        val kg = list.maxOf { it.kg }
        val ok = list.filter { ExpectedProgress.plausibleReps(it.reps, (rows[it.planExerciseId] ?: pe)?.repMax) }
        if (ok.none { it.kg == kg }) null else LiftSession(start.getValue(wid), ok, kg)
    }.sortedBy { it.time }
    if (dayId !in dayIds || pe == null) {
        return LiftPace(null, null, null, emptyList(), emptySet(), sessions.firstOrNull()?.time, sessions.firstOrNull()?.kg, 0)
    }
    val cutoff = loggedFromMillis(zone)
    val logged = sessions.filter { it.time >= cutoff }
    val n0 = sessions.size - logged.size
    val repMin = pe.repMin; val repMax = pe.repMax
    // The next jump from the current working weight (the percentage rounded to the load step).
    val inc = logged.lastOrNull()?.let { Progression.jump(pe, it.kg) } ?: Progression.jump(pe, pe.kg)

    fun date(t: Long) = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
    val liftDays = plan.filter { it.exerciseId == liftId && it.dayId in dayIds }.map { it.dayId }.toSet()
    val weekdays = Schedule.trainingDays(workouts.map { date(it.startedAt) })
    val perWeek = if (weekdays.isNotEmpty()) weekdays.size.toDouble() * liftDays.size / dayIds.size else 1.0
    val model = ExpectedProgress.model(logged, n0, repMin, repMax, inc, perWeek)
    val next = ExpectedProgress.nextJump(logged, repMax, inc)

    val count = maxOf((PROJECT_WEEKS * perWeek).roundToInt(), next?.sessions ?: 0)
    val last = logged.lastOrNull()?.time
    val timeOfDay = last?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() } ?: LocalTime.NOON
    val dates = when {
        last == null -> emptyList()
        weekdays.isNotEmpty() -> {
            // Same rotation anchor as the Workout screen: the latest finished workout of a plan day.
            val anchor = finished.filter { it.dayId in dayIds }.maxByOrNull { it.startedAt }
            val after = listOfNotNull(anchor?.let { date(it.startedAt) }, date(now).minusDays(1)).max()
            Schedule.nextDates(dayIds, anchor?.dayId, weekdays, liftDays, after, count)
        }
        else -> (1..count).map { date(last).plusWeeks(it.toLong()) }
    }
    val future = dates.map { it.atTime(timeOfDay).atZone(zone).toInstant().toEpochMilli() }
    return LiftPace(
        model, next, next?.sessions?.let { dates.getOrNull(it - 1) }, future.take((PROJECT_WEEKS * perWeek).roundToInt()),
        weekdays, sessions.firstOrNull()?.time, sessions.firstOrNull()?.kg, repMax,
    )
}

/** Chart layers for one lift: expected curve with its band, next-jump marker, optional typical range. */
fun chartOverlay(
    pace: LiftPace, typical: Boolean, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault(),
): ChartOverlay {
    val m = pace.model
    fun cp(t: Long, c: Double, hw: Double) = CurvePoint(t, m!!.toKg(c), m.toKg(c + hw), m.toKg(c - hw))
    val curve = if (m == null) emptyList() else
        m.sessions.mapIndexed { i, s -> cp(s.time, m.curve(i), m.half) } +
            pace.future.mapIndexed { j, t -> cp(t, m.curve(m.n + j), m.half * (1 + 0.35 * (j + 1))) }
    val jump = pace.next?.let { n -> pace.nextDate?.let { d ->
        val t = pace.future.firstOrNull { f -> Instant.ofEpochMilli(f).atZone(zone).toLocalDate() == d }
            ?: d.atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()
        JumpMarker(t, n.kg, n.kg + n.increment, "+${kg(n.increment)} kg", "~${fmtDay(t)}")
    } }
    val band = if (typical && pace.firstTime != null && pace.firstKg != null) {
        val t0 = pace.firstTime; val k0 = pace.firstKg
        TypicalBand(t0) { t -> ExpectedProgress.typicalRange(k0, (t - t0) / (30.44 * 86_400_000)) }
    } else null
    return ChartOverlay(curve, if (curve.isEmpty()) null else now, jump, band, loggedFromMillis(zone))
}

private val shortDay = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
fun fmtDay(t: Long): String = shortDay.format(Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()))
