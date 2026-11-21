package com.apollo.overload.domain

import com.apollo.overload.data.PlanExercise
import com.apollo.overload.data.SetEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionTest {
    private val p = PlanExercise(id = 10, dayId = 1, exerciseId = "lat-pulldown", position = 0, kg = 55.0, sets = 3, repMin = 7, repMax = 9, increment = 2.5, incrementPct = 5.0)
    private fun sets(kg: Double, vararg reps: Int) = reps.mapIndexed { i, r ->
        SetEntry(id = i.toLong() + 1, workoutId = 1, exerciseId = "lat-pulldown", planExerciseId = 10, setIndex = i, kg = kg, reps = r, loggedAt = i.toLong())
    }

    @Test fun noHistoryUsesPlanKgAndBottomOfRange() {
        assertEquals(Prescription(55.0, listOf(7, 7, 7), false), Progression.prescribe(p, emptyList()))
    }

    @Test fun lastTimePlusOneCappedAtTop() {
        assertEquals(Prescription(55.0, listOf(9, 8, 8), false), Progression.prescribe(p, sets(55.0, 8, 7, 7)))
        assertEquals(listOf(9, 9, 9), Progression.prescribe(p, sets(55.0, 9, 9, 8)).reps)
    }

    @Test fun planKgAboveLastTimeResetsReps() {
        // Finishing an earned session raised the plan to 57.5.
        assertEquals(Prescription(57.5, listOf(7, 7, 7), true), Progression.prescribe(p.copy(kg = 57.5), sets(55.0, 9, 9, 9)))
        // The plan was not raised: same load, reps stay at the top.
        assertEquals(Prescription(55.0, listOf(9, 9, 9), false), Progression.prescribe(p, sets(55.0, 9, 9, 9)))
    }

    @Test fun planKgIsTheLoadEvenWhenLowered() {
        assertEquals(Prescription(50.0, listOf(9, 8, 8), false), Progression.prescribe(p.copy(kg = 50.0), sets(55.0, 8, 7, 7)))
    }

    @Test fun fewerSetsLastTimeIsNotProgress() {
        val rx = Progression.prescribe(p, sets(55.0, 9, 9))
        assertFalse(rx.raised)
        assertEquals(listOf(9, 9, 7), rx.reps)
    }

    @Test fun setTargetPrefersDraftThenTodayThenPrescription() {
        val rx = Progression.prescribe(p, sets(55.0, 8, 7, 7))
        assertEquals(55.0 to 9, Progression.setTarget(p, rx, emptyList(), 0))
        assertEquals(50.0 to 8, Progression.setTarget(p, rx, sets(50.0, 9), 1))
        assertEquals(60.0 to 6, Progression.setTarget(p, rx, sets(50.0, 9), 1, draftKg = 60.0, draftReps = 6))
        assertEquals(55.0 to 8, Progression.setTarget(p, rx, emptyList(), 5))
    }

    @Test fun earnedNeedsAllPlannedSetsAtTop() {
        assertTrue(Progression.earned(p, sets(55.0, 9, 9, 9)))
        assertTrue(Progression.earned(p, sets(55.0, 9, 10, 9, 9)))
        assertFalse(Progression.earned(p, sets(55.0, 9, 9)))
        assertFalse(Progression.earned(p, sets(55.0, 9, 8, 9)))
        assertEquals(2, Progression.topCount(p, sets(55.0, 9, 8, 9)))
        assertEquals(57.5, Progression.nextKg(p, sets(55.0, 9, 9, 9)), 0.0)
    }

    @Test fun planUpdatesWriteBackTheLoadUsed() {
        val other = p.copy(id = 11, exerciseId = "row", kg = 30.0, increment = 1.25)
        val third = p.copy(id = 12, exerciseId = "press", kg = 40.0)
        val untouched = p.copy(id = 13, exerciseId = "curl", kg = 20.0)
        val today = mapOf(
            10L to sets(55.0, 9, 9, 9), // earned: load used + increment
            11L to sets(30.0, 9, 9, 8), // same load, not earned: no change
            12L to sets(42.5, 8, 8, 8), // heavier than planned: the plan follows
        )
        assertEquals(
            listOf(p.copy(kg = 57.5), third.copy(kg = 42.5)),
            Progression.planUpdates(listOf(p, other, third, untouched), today),
        )
        // Idempotent: running it again on the updated plan gives the same loads.
        assertEquals(emptyList<PlanExercise>(), Progression.planUpdates(listOf(p.copy(kg = 57.5), third.copy(kg = 42.5)), today))
    }

    @Test fun incrementRoundsCleanly() {
        val q = p.copy(kg = 17.5, increment = 1.1)
        assertEquals(18.6, Progression.nextKg(q, sets(17.5, 9, 9, 9)), 0.0)
    }

    @Test fun jumpIsThePercentageRoundedToTheLoadStep() {
        val legPress = p.copy(exerciseId = "leg-press", incrementPct = 10.0, increment = 2.5)
        assertEquals(10.0, Progression.jump(legPress, 110.0), 0.0) // 11 kg -> 4 steps
        assertEquals(120.0, Progression.nextKg(legPress, sets(110.0, 9, 9, 9)), 0.0)
        val curl = p.copy(incrementPct = 5.0, increment = 2.5)
        assertEquals(2.5, Progression.jump(curl, 17.5), 0.0) // 0.875 kg rounds to 0 steps: at least one step
        assertEquals(20.0, Progression.nextKg(curl, sets(17.5, 9, 9, 9)), 0.0)
        assertEquals(5.0, Progression.jump(curl, 90.0), 0.0) // 4.5 kg = 1.8 steps -> 2
        assertEquals(1.0, Progression.jump(curl.copy(increment = 1.0), 25.0), 0.0) // small load, small step: 1.25 kg -> 1
        assertEquals(2.5, Progression.jump(curl, 0.0), 0.0) // bodyweight: one step
    }

    @Test fun jumpWithoutStepRoundsTheNewLoadToHalfKg() {
        val q = p.copy(increment = 0.0, incrementPct = 5.0)
        assertEquals(3.0, Progression.jump(q, 55.0), 0.0) // 57.75 rounds to 58
        assertEquals(58.0, Progression.nextKg(q, sets(55.0, 9, 9, 9)), 0.0)
        assertEquals(0.5, Progression.jump(q, 10.0), 0.0) // 10.5
        assertEquals(0.0, Progression.jump(q, 4.0), 0.0) // 4.2 rounds back to 4
    }

    @Test fun planUpdatesShareTheLoadAcrossDaysOfTheSplit() {
        val lowerA = p.copy(id = 20, dayId = 2, exerciseId = "leg-press", kg = 100.0, incrementPct = 10.0)
        val lowerB = p.copy(id = 30, dayId = 4, exerciseId = "leg-press", kg = 100.0, incrementPct = 10.0)
        val curlB = p.copy(id = 31, dayId = 4, exerciseId = "leg-curl", kg = 30.0)
        // Lower A: 110 kg x 7, not earned; Lower B's leg press follows, its other exercise does not.
        val today = mapOf(20L to sets(110.0, 7, 7, 7))
        assertEquals(
            listOf(lowerA.copy(kg = 110.0), lowerB.copy(kg = 110.0)),
            Progression.planUpdates(listOf(lowerA, lowerB, curlB), today),
        )
        // Earned on Lower A at 110: both rows take 120.
        assertEquals(
            listOf(lowerA.copy(kg = 120.0), lowerB.copy(kg = 120.0)),
            Progression.planUpdates(listOf(lowerA.copy(kg = 110.0), lowerB.copy(kg = 110.0), curlB), mapOf(20L to sets(110.0, 9, 9, 9))),
        )
        // A row already at the shared load is not rewritten.
        assertEquals(
            listOf(lowerB.copy(kg = 110.0)),
            Progression.planUpdates(listOf(lowerA.copy(kg = 110.0), lowerB, curlB), today),
        )
    }

    @Test fun rotationPicksTheDayAfterTheLast() {
        val days = listOf(1L, 2, 3, 4)
        assertEquals(1L, Rotation.nextDay(days, null))
        assertEquals(3L, Rotation.nextDay(days, 2))
        assertEquals(1L, Rotation.nextDay(days, 4))
        assertEquals(1L, Rotation.nextDay(days, 99))
        assertEquals(null, Rotation.nextDay(emptyList(), 1))
    }
}
