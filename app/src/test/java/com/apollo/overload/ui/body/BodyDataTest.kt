package com.apollo.overload.ui.body

import com.apollo.overload.data.Exercise
import com.apollo.overload.data.SetEntry
import com.apollo.overload.data.Workout
import com.apollo.overload.ui.common.fmt1
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class BodyDataTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 17)
    private fun at(d: LocalDate, h: Int) = d.atStartOfDay(zone).toInstant().toEpochMilli() + h * 3_600_000L
    private val bench = Exercise("bench", "Bench", "push", "barbell", mapOf("chest" to 1f, "triceps" to 0.4f, "delts" to 0.4f))

    @Test fun weekStartsOnMonday() {
        assertEquals(LocalDate.of(2026, 11, 16), weekStart(today)) // Tue 17 Nov -> Mon 16 Nov
        assertEquals(LocalDate.of(2026, 11, 16), weekStart(LocalDate.of(2026, 11, 16)))
        assertEquals(LocalDate.of(2026, 11, 16), weekStart(LocalDate.of(2026, 11, 22))) // Sunday stays in the week
        assertEquals(LocalDate.of(2026, 11, 23), weekStart(LocalDate.of(2026, 11, 23)))
        assertEquals(LocalDate.of(2026, 11, 30), weekStart(LocalDate.of(2026, 12, 3)))
        assertEquals(1, defaultDayIndex(weekStart(today), today))
        assertEquals(0, defaultDayIndex(weekStart(today).minusWeeks(1), today))
    }

    @Test fun countsPrimaryAndSecondaryPerDayAndWeek() {
        val monday = weekStart(today)
        val w = Workout(id = 1, dayId = 1, dayName = "Upper A", startedAt = at(today, 10))
        val sets = listOf(
            SetEntry(workoutId = 1, exerciseId = "bench", planExerciseId = null, setIndex = 0, kg = 60.0, reps = 8, loggedAt = at(today, 10)),
            SetEntry(workoutId = 1, exerciseId = "bench", planExerciseId = null, setIndex = 1, kg = 60.0, reps = 8, loggedAt = at(today, 10)),
            // Sunday 15 Nov belongs to the previous week, even though it is within the last 7 days.
            SetEntry(workoutId = 1, exerciseId = "bench", planExerciseId = null, setIndex = 0, kg = 60.0, reps = 8, loggedAt = at(today.minusDays(2), 10)),
        )
        val d = buildBodyData(sets, listOf(w), listOf(bench), monday, zone)
        assertEquals(7, d.days.size)
        assertEquals(monday, d.days.first().date)
        assertEquals(LocalDate.of(2026, 11, 22), d.days.last().date)
        val tue = d.days[1]
        assertEquals(today, tue.date)
        assertEquals(2.0, tue.map["chest"]!!, 0.0)
        assertEquals(1.0, tue.map["triceps"]!!, 0.0)
        assertEquals(2.0, d.week["chest"]!!, 0.0)
        assertEquals(listOf("Upper A"), tue.workoutNames)
        assertEquals(1, tue.exercises.size)
        val prev = buildBodyData(sets, listOf(w), listOf(bench), monday.minusWeeks(1), zone)
        assertEquals(1.0, prev.week["chest"]!!, 0.0)
        assertEquals(1.0, prev.days.last().map["chest"]!!, 0.0)
        assertEquals("1.5", fmt1(1.5)); assertEquals("6", fmt1(6.0))
    }
}
