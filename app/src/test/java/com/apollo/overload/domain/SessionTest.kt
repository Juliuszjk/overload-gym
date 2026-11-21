package com.apollo.overload.domain

import com.apollo.overload.data.PlanExercise
import com.apollo.overload.data.SetEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTest {
    private val plan = listOf(
        PlanExercise(id = 1, dayId = 1, exerciseId = "smith", position = 0, pairId = 1, kg = 35.0),
        PlanExercise(id = 2, dayId = 1, exerciseId = "pulldown", position = 1, pairId = 1, kg = 55.0),
        PlanExercise(id = 3, dayId = 1, exerciseId = "pecdeck", position = 2, kg = 66.0, sets = 2),
    )
    private val types = mapOf("smith" to "push", "pulldown" to "pull", "pecdeck" to "push")
    private fun set(pe: Long, i: Int, reps: Int = 8) =
        SetEntry(id = pe * 10 + i, workoutId = 1, exerciseId = "x", planExerciseId = pe, setIndex = i, kg = 10.0, reps = reps, loggedAt = pe * 10 + i)

    @Test fun activeIsFirstUnfinishedInOrder() {
        val s = Session(plan, listOf(2, 1, 3), listOf(set(2, 0), set(2, 1), set(2, 2)), types)
        assertTrue(s.isDone(2))
        assertEquals(1L, s.active)
        assertEquals(1, s.rank(2))
        assertEquals(2, s.planPos(2))
        assertEquals(1, s.moved(2))
    }

    @Test fun setCountOverridesExtendOrEndExercise() {
        val sets = listOf(set(1, 0), set(1, 1), set(1, 2))
        assertEquals(2L, Session(plan, listOf(1, 2, 3), sets, types).active)
        assertEquals(1L, Session(plan, listOf(1, 2, 3), sets, types, mapOf(1L to 4)).active)
        val early = Session(plan, listOf(1, 2, 3), listOf(set(2, 0)), types, mapOf(2L to 1))
        assertTrue(early.isDone(2))
        assertFalse(early.isDone(1))
    }

    @Test fun skippedExerciseIsNotInSession() {
        val s = Session(plan, listOf(1, 2), emptyList(), types)
        assertEquals(listOf(1L, 2), s.plan.map { it.id })
        assertEquals(1L, s.active)
        val all = Session(plan, listOf(1, 2), listOf(set(1, 0), set(1, 1), set(1, 2), set(2, 0), set(2, 1), set(2, 2)), types)
        assertEquals(null, all.active)
    }

    @Test fun loggedExerciseRemovedFromPlanIsExtra() {
        val s = Session(plan.filter { it.id != 2L }, listOf(1, 2, 3), listOf(set(2, 0), set(1, 0), set(2, 1)), types)
        assertEquals(listOf(1L, 3), s.order)
        assertEquals(listOf(2L), s.extra)
        assertEquals(2, s.today(2).size)
        assertEquals(emptyList<Long>(), Session(plan, listOf(1, 2, 3), listOf(set(2, 0)), types).extra)
    }

    @Test fun exerciseAddedMidWorkoutJoinsAtTheEnd() {
        val added = PlanExercise(id = 4, dayId = 1, exerciseId = "curl", position = 3, kg = 12.0)
        val done = listOf(set(1, 0), set(1, 1), set(1, 2), set(2, 0), set(2, 1), set(2, 2), set(3, 0), set(3, 1))
        // Only in the plan: not part of the session.
        assertEquals(null, Session(plan + added, listOf(1, 2, 3), done, types).active)
        // Appended to Workout.order: it is next and loggable, and removing it again leaves the session intact.
        val s = Session(plan + added, listOf(1, 2, 3, 4), done, types)
        assertEquals(4L, s.active)
        assertEquals(4, s.rank(4))
        assertEquals(null, Session(plan, listOf(1, 2, 3, 4), done, types).active)
    }

    @Test fun startUsesExerciseTypes() {
        val r = Session(plan, listOf(1, 2, 3), emptyList(), types).start(2, false)
        assertEquals(OrderSwitch.PullFirst, r.switched)
        assertEquals(listOf(2L, 1, 3), r.order)
    }

    @Test fun orderChangedAgainstPlan() {
        assertFalse(Session(plan, listOf(1, 2, 3), emptyList(), types).orderChanged)
        assertTrue(Session(plan, listOf(3, 1, 2), emptyList(), types).orderChanged)
        // A skipped exercise is out of the session, not a changed order.
        assertFalse(Session(plan, listOf(1, 3), emptyList(), types).orderChanged)
    }

    @Test fun planOrderKeepsLoggedExercisesInPlace() {
        // Started out of order but nothing logged: the full plan order comes back.
        assertEquals(listOf(1L, 2, 3), Session(plan, listOf(3, 1, 2), emptyList(), types).planOrder())
        // Pec deck already has a set: it keeps its place, the rest follow the plan.
        assertEquals(listOf(3L, 1, 2), Session(plan, listOf(3, 2, 1), listOf(set(3, 0)), types).planOrder())
        // Nothing left to move back.
        val s = Session(plan, listOf(3, 1, 2), listOf(set(3, 0)), types)
        assertTrue(s.orderChanged)
        assertEquals(s.order, s.planOrder())
    }
}
