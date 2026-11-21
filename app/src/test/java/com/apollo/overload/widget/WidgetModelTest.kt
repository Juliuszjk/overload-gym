package com.apollo.overload.widget

import com.apollo.overload.data.Exercise
import com.apollo.overload.data.PlanDay
import com.apollo.overload.data.PlanExercise
import com.apollo.overload.data.SetEntry
import com.apollo.overload.data.Workout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetModelTest {
    private val ex = mapOf(
        "rdl" to Exercise("rdl", "Romanian deadlift", "legs", "dumbbell", emptyMap()),
        "lp" to Exercise("lp", "Leg press", "legs", "machine", emptyMap()),
    )
    private val plan = listOf(
        PlanExercise(id = 1, dayId = 1, exerciseId = "rdl", position = 0, kg = 60.0, sets = 2, repMin = 8, repMax = 10, incrementPct = 10.0),
        PlanExercise(id = 2, dayId = 1, exerciseId = "lp", position = 1, kg = 140.0, sets = 3, repMin = 10, repMax = 12, increment = 5.0, incrementPct = 10.0),
    )
    private val day = PlanDay(id = 1, splitId = 1, name = "Lower B", kind = "lower", position = 0)
    private fun workout(nextUpFrom: Long? = null, setCounts: Map<Long, Int> = emptyMap()) =
        Workout(id = 7, dayId = 1, dayName = "Lower B", startedAt = 0, order = listOf(1, 2), nextUpFrom = nextUpFrom, setCounts = setCounts)
    private fun set(pe: Long, i: Int, kg: Double, reps: Int, w: Long = 7) =
        SetEntry(id = w * 100 + pe * 10 + i, workoutId = w, exerciseId = if (pe == 1L) "rdl" else "lp", planExerciseId = pe,
            setIndex = i, kg = kg, reps = reps, loggedAt = w * 100 + pe * 10 + i)
    private val lastLp = listOf(set(2, 0, 140.0, 12, w = 3), set(2, 1, 140.0, 10, w = 3), set(2, 2, 140.0, 9, w = 3))

    private fun derive(w: Workout?, sets: List<SetEntry>, draft: Draft? = null, last: Map<String, List<SetEntry>> = mapOf("lp" to lastLp)) =
        WidgetModel.derive(w, plan, sets, ex, last, draft, day, plan)

    @Test fun idleShowsNextDay() {
        val m = derive(null, emptyList()) as WidgetModel.Idle
        assertEquals("Lower B", m.dayName)
        assertEquals(listOf("Romanian deadlift", "Leg press"), m.exercises.map { it.name })
        assertEquals(5, m.totalSets)
    }

    @Test fun emptyWithoutPlanRows() {
        assertTrue(WidgetModel.derive(null, plan, emptyList(), ex, emptyMap(), null, day, emptyList()) is WidgetModel.Empty)
        assertTrue(WidgetModel.derive(null, plan, emptyList(), ex, emptyMap(), null, null, emptyList()) is WidgetModel.Empty)
    }

    @Test fun runningUsesPrescriptionAndLastTime() {
        val sets = listOf(set(1, 0, 60.0, 9), set(1, 1, 60.0, 9), set(2, 0, 140.0, 12))
        val m = derive(workout(), sets) as WidgetModel.Running
        assertEquals(2L, m.planExId)
        assertEquals("Leg press", m.name)
        assertEquals(1, m.setIndex)
        assertEquals(3, m.setCount)
        // Load from today's last set, reps = last time's set 2 (10) + 1.
        assertEquals(140.0, m.kg, 0.0)
        assertEquals(11, m.reps)
        assertEquals(0, m.kgVs)
        assertEquals(1, m.repsVs)
        assertEquals(140.0 to 10, m.last)
        assertNull(m.nextName)
        assertEquals(listOf(RungState.Done, RungState.Now, RungState.Upcoming), m.rungs.map { it.state })
        assertTrue(m.rungs[0].top)
        assertEquals(listOf(TrackSeg(2, 2, false), TrackSeg(1, 3, true)), m.track)
    }

    @Test fun draftAppliesOnlyToItsSet() {
        val sets = listOf(set(1, 0, 60.0, 9), set(1, 1, 60.0, 9), set(2, 0, 140.0, 12))
        val d = derive(workout(), sets, Draft(7, 2, 1, kg = 135.0, reps = 9)) as WidgetModel.Running
        assertEquals(135.0, d.kg, 0.0)
        assertEquals(9, d.reps)
        assertEquals(-1, d.kgVs)
        assertEquals(-1, d.repsVs)
        // Upcoming sets follow the adjusted kg and the plan's target reps.
        assertEquals(135.0, d.rungs[2].kg, 0.0)
        assertEquals(10, d.rungs[2].reps)
        val stale = derive(workout(), sets, Draft(7, 2, 0, kg = 135.0)) as WidgetModel.Running
        assertEquals(140.0, stale.kg, 0.0)
    }

    @Test fun nextSetKeepsLoggedKg() {
        val sets = listOf(set(1, 0, 60.0, 9), set(1, 1, 60.0, 9), set(2, 0, 145.0, 12))
        val m = derive(workout(), sets) as WidgetModel.Running
        assertEquals(145.0, m.kg, 0.0)
        assertEquals(1, m.kgVs)
        assertEquals(11, m.reps)
    }

    @Test fun nextUpAfterLastSetOfAnExercise() {
        val sets = listOf(set(1, 0, 60.0, 10), set(1, 1, 60.0, 10))
        val m = derive(workout(nextUpFrom = 1), sets) as WidgetModel.NextUp
        assertEquals(2L, m.planExId)
        assertEquals("Leg press", m.name)
        assertEquals(2, m.rank)
        assertEquals(140.0, m.kg, 0.0)
        assertEquals("Romanian deadlift", m.prevName)
        // Every set at the top of the range: +10 % of 60 rounded to the 2.5 step.
        assertEquals(65.0, m.prevNextKg!!, 0.0)
        // Without "Next up" in the workout row, the next exercise's first set shows.
        assertTrue(derive(workout(), sets) is WidgetModel.Running)
    }

    @Test fun doneWhenEveryExerciseIsDone() {
        val sets = listOf(set(1, 0, 60.0, 9), set(1, 1, 60.0, 9)) + (0..2).map { set(2, it, 140.0, 12) }
        val m = derive(workout(nextUpFrom = 2), sets) as WidgetModel.Done
        assertEquals("Lower B", m.dayName)
        assertEquals(5, m.sets)
        assertEquals(listOf(null, 155.0), m.lines.map { it.nextKg })
    }

    @Test fun skippedExerciseCountsAsDone() {
        // Skip after one of two sets: the widget moves on to the next exercise instead of logging into the skipped one.
        val m = derive(workout(setCounts = mapOf(1L to 1)), listOf(set(1, 0, 60.0, 9))) as WidgetModel.Running
        assertEquals(2L, m.planExId)
        assertEquals(0, m.setIndex)
        assertEquals(listOf(TrackSeg(1, 1, false), TrackSeg(0, 3, true)), m.track)
        assertTrue(derive(workout(), listOf(set(1, 0, 60.0, 9))).let { it is WidgetModel.Running && it.planExId == 1L })
    }

    @Test fun extraSetKeepsTheExerciseGoing() {
        val sets = listOf(set(1, 0, 60.0, 9), set(1, 1, 60.0, 9))
        val m = derive(workout(setCounts = mapOf(1L to 3)), sets) as WidgetModel.Running
        assertEquals(1L, m.planExId)
        assertEquals(2, m.setIndex)
        assertEquals(3, m.setCount)
        assertEquals(listOf(RungState.Done, RungState.Done, RungState.Now), m.rungs.map { it.state })
        assertEquals(TrackSeg(2, 3, true), m.track[0])
        // Without the extra set, the plan's two sets finish it.
        assertEquals(2L, (derive(workout(), sets) as WidgetModel.Running).planExId)
    }
}
