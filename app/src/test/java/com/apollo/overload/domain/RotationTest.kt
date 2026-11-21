package com.apollo.overload.domain

import com.apollo.overload.data.Workout
import org.junit.Assert.assertEquals
import org.junit.Test

class RotationTest {
    private fun w(day: Long, started: Long, finished: Boolean = true) =
        Workout(dayId = day, dayName = "", startedAt = started, finishedAt = if (finished) started + 1 else null)

    @Test fun nextDayFollowsLatestFinishedWorkoutOfTheSplit() {
        val days = listOf(1L, 2L, 3L)
        assertEquals(1L, Rotation.nextDay(days, emptyList<Workout>()))
        assertEquals(3L, Rotation.nextDay(days, listOf(w(1, 10), w(2, 20), w(3, 30, finished = false), w(9, 40))))
        assertEquals(1L, Rotation.nextDay(days, listOf(w(3, 50), w(1, 10))))
    }
}
