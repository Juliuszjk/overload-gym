package com.apollo.overload.ui.body

import com.apollo.overload.data.Exercise
import com.apollo.overload.data.SetEntry
import com.apollo.overload.data.Workout
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Sets credited to a muscle by one set of an exercise: 1 for a primary muscle, 0.5 for a helper. */
fun setCredit(activation: Float): Double = when {
    activation >= 1f -> 1.0
    activation > 0f -> 0.5
    else -> 0.0
}

/** One exercise done on a day, with its sets in logging order. */
class DayExercise(val exercise: Exercise, val sets: List<SetEntry>)

class BodyDay(
    val date: LocalDate,
    /** Muscle key -> sets credited that day. */
    val map: Map<String, Double>,
    val workoutNames: List<String>,
    val exercises: List<DayExercise>,
) {
    val total: Double get() = map.values.sum()
}

class BodyData(val days: List<BodyDay>, val week: Map<String, Double>, val hasAnySets: Boolean)

/** Monday of the calendar week (Monday–Sunday) that contains [date]. */
fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** Day selected by default in a week starting [monday]: [today] if it falls in that week, else Monday. */
fun defaultDayIndex(monday: LocalDate, today: LocalDate): Int {
    val i = ChronoUnit.DAYS.between(monday, today)
    return if (i in 0..6) i.toInt() else 0
}

private fun LocalDate.startMillis(zone: ZoneId) = atStartOfDay(zone).toInstant().toEpochMilli()

/**
 * Builds the calendar week Monday–Sunday that starts on [monday] from every logged set (including the open workout).
 */
fun buildBodyData(
    sets: List<SetEntry>,
    workouts: List<Workout>,
    exercises: List<Exercise>,
    monday: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
): BodyData {
    val byId = exercises.associateBy { it.id }
    val workoutById = workouts.associateBy { it.id }
    val first = monday
    val from = first.startMillis(zone)
    val to = first.plusDays(7).startMillis(zone)
    val inRange = sets.filter { it.loggedAt in from until to }
        .groupBy { Instant.ofEpochMilli(it.loggedAt).atZone(zone).toLocalDate() }
    val days = (0..6).map { i ->
        val date = first.plusDays(i.toLong())
        val daySets = inRange[date].orEmpty()
        val map = mutableMapOf<String, Double>()
        for (s in daySets) {
            val ex = byId[s.exerciseId] ?: continue
            for ((k, a) in ex.muscles) {
                val c = setCredit(a)
                if (c > 0) map[k] = (map[k] ?: 0.0) + c
            }
        }
        val names = daySets.map { it.workoutId }.distinct().mapNotNull { workoutById[it]?.dayName }.distinct()
        val dayExercises = daySets.groupBy { it.exerciseId }.mapNotNull { (id, list) ->
            byId[id]?.let { DayExercise(it, list) }
        }
        BodyDay(date, map, names, dayExercises)
    }
    val week = mutableMapOf<String, Double>()
    days.forEach { d -> d.map.forEach { (k, v) -> week[k] = (week[k] ?: 0.0) + v } }
    return BodyData(days, week, sets.isNotEmpty())
}
