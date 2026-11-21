package com.apollo.overload.domain

import com.apollo.overload.data.SetEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VsLastTest {
    private fun set(i: Int, kg: Double, reps: Int) =
        SetEntry(workoutId = 1, exerciseId = "leg-press", planExerciseId = 1, setIndex = i, kg = kg, reps = reps, loggedAt = i.toLong())

    private val last = listOf(set(0, 140.0, 12), set(1, 140.0, 10), set(2, 135.0, 9))

    @Test fun kgAgainstLastTop() {
        assertEquals(5.0, VsLast.kg(145.0, last)!!, 0.0)
        assertEquals(0.0, VsLast.kg(140.0, last)!!, 0.0)
        assertEquals(-2.5, VsLast.kg(137.5, last)!!, 0.0)
        assertNull(VsLast.kg(100.0, emptyList()))
    }

    @Test fun repsAgainstSameSetLastTime() {
        assertEquals(1, VsLast.reps(11, last, 1))
        assertEquals(-1, VsLast.reps(8, last, 2))
        assertEquals(0, VsLast.reps(12, last, 0))
        assertNull(VsLast.reps(8, last, 3))
    }
}
