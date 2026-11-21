package com.apollo.overload.ui.progress

import com.apollo.overload.data.Exercise
import com.apollo.overload.data.PlanDay
import com.apollo.overload.data.PlanExercise
import com.apollo.overload.data.SetEntry
import com.apollo.overload.data.Workout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ProgressDataTest {
    private fun set(w: Long, ex: String, kg: Double, reps: Int, t: Long, i: Int = 0) =
        SetEntry(workoutId = w, exerciseId = ex, planExerciseId = null, setIndex = i, kg = kg, reps = reps, loggedAt = t)

    @Test fun workingWeightIsHeaviestSetThenMostReps() {
        val ws = listOf(Workout(1, 1, "A", 1000, 61_000), Workout(2, 1, "B", 5000))
        val sets = listOf(
            set(1, "a", 60.0, 10, 1000), set(1, "a", 70.0, 3, 1100),
            set(2, "a", 100.0, 8, 5000, 0), set(2, "a", 100.0, 9, 5050, 1), set(2, "a", 90.0, 12, 5080, 2),
            set(2, "b", 20.0, 12, 5100),
        )
        val st = liftStats("a", sets, ws, null)
        assertEquals(2, st.points.size)
        assertEquals(70.0, st.points[0].kg, 0.0) // heaviest, not the 60 x 10 that has the higher e1RM
        assertEquals(3, st.points[0].reps)
        assertEquals(100.0, st.points[1].kg, 0.0)
        assertEquals(9, st.points[1].reps) // tie on kg -> most reps
        assertEquals(listOf(8, 9, 12), st.points[1].allReps)
        assertEquals(5, st.sets)
        assertNull(workingSet(emptyList()))

        val pr = personalRecord("a", sets)!!
        assertEquals(100.0, pr.kg, 0.0); assertEquals(9, pr.reps)

        val recs = records(sets, emptyList())
        assertEquals(100.0, recs.first { it.name == "a" }.kg, 0.0)
        assertEquals(listOf(2L, 1L), recentWorkouts(sets, ws).map { it.workout.id })
    }

    @Test fun liftGroupsFollowActivePlan() {
        val ex = listOf(
            Exercise("a", "Alpha", "push", "", emptyMap()), Exercise("b", "Beta", "pull", "", emptyMap()),
            Exercise("c", "Gamma", "legs", "", emptyMap()), Exercise("x", "Extra", "core", "", emptyMap()),
        )
        val days = listOf(PlanDay(11, 1, "Lower A", "lower", 1), PlanDay(10, 1, "Upper A", "upper", 0))
        val plan = listOf(
            PlanExercise(dayId = 10, exerciseId = "b", position = 1, kg = 0.0),
            PlanExercise(dayId = 10, exerciseId = "a", position = 0, kg = 0.0),
            PlanExercise(dayId = 11, exerciseId = "c", position = 0, kg = 0.0),
        )
        val sets = listOf(set(1, "a", 60.0, 8, 1000), set(2, "x", 10.0, 15, 2000))
        val groups = liftGroups(days, plan, sets, ex)
        assertEquals(listOf("Upper A", "Lower A", "Other"), groups.map { it.name })
        assertEquals(listOf("a", "b"), groups[0].lifts.map { it.id })
        assertEquals(listOf("x"), groups[2].lifts.map { it.id })
        assertEquals(OTHER, groups[2].key)
        val ws = listOf(Workout(1, 10, "Upper A", 1000), Workout(2, 11, "Lower A", 2000))
        assertEquals(11L, defaultGroupKey(groups, ws))
        assertEquals(10L, defaultGroupKey(groups, listOf(Workout(3, 99, "Old", 3000))))
    }

    @Test fun liftPaceChecksRepsAgainstEachSetsOwnRow() {
        val days = listOf(PlanDay(10, 1, "Upper A", "upper", 0), PlanDay(11, 1, "Upper B", "upper", 1))
        val plan = listOf(
            PlanExercise(id = 1, dayId = 10, exerciseId = "a", position = 0, kg = 50.0, repMin = 7, repMax = 9),
            PlanExercise(id = 2, dayId = 11, exerciseId = "a", position = 0, kg = 40.0, repMin = 12, repMax = 15),
        )
        fun t(week: Long) = LocalDate.of(2026, 10, 12).plusWeeks(week).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val ws = listOf(10L, 11, 10, 10, 10, 11, 10).mapIndexed { i, d -> Workout(i + 1L, d, "", t(i.toLong()), t(i.toLong()) + 3_600_000) }
        fun st(w: Long, row: Long?, kg: Double, reps: Int) =
            SetEntry(workoutId = w, exerciseId = "a", planExerciseId = row, setIndex = 0, kg = kg, reps = reps, loggedAt = t(w - 1))
        val sets = listOf(
            st(1, 1, 50.0, 8), st(1, 1, 50.0, 8),
            st(2, 2, 40.0, 18), st(2, 2, 40.0, 18), // fine for Upper B's 12–15, too many for Upper A's 7–9
            st(3, 1, 50.0, 9), st(3, 1, 52.5, 8), st(3, 1, 52.5, 80), // typo dropped, 52.5 stays the working weight
            st(4, 1, 50.0, 9), st(4, 1, 55.0, 80), // only working-weight set is a typo: session left out
            st(5, 1, 52.5, 9), st(5, 1, 52.5, 9),
            st(6, 99, 40.0, 12), st(6, null, 40.0, 20), // unknown row: checked against Upper A
            st(7, 1, 52.5, 9),
        )
        val m = liftPace("a", 10, sets, ws, days, plan, now = t(7), zone = ZoneOffset.UTC).model!!
        assertEquals(listOf(50.0, 40.0, 52.5, 52.5, 40.0, 52.5), m.sessions.map { it.kg })
        assertEquals(listOf(listOf(8, 8), listOf(18, 18), listOf(8), listOf(9, 9), listOf(12), listOf(9)), m.sessions.map { it.reps })
    }
}
