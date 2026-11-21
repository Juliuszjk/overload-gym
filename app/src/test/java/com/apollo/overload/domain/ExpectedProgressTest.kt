package com.apollo.overload.domain

import com.apollo.overload.data.SetEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate

/** Sample lifts (5 estimated sessions before the logged ones) and a longer double-progression history. */
class ExpectedProgressTest {
    private fun s(kg: Double, vararg reps: Int) = reps.mapIndexed { i, r ->
        SetEntry(workoutId = 1, exerciseId = "x", planExerciseId = null, setIndex = i, kg = kg, reps = r, loggedAt = 0)
    }
    private fun lift(vararg sessions: List<SetEntry>) = sessions.mapIndexed { i, it -> LiftSession(i.toLong(), it) }

    private val smith = lift(s(30.0, 9, 9, 8), s(30.0, 9, 9, 9), s(32.5, 8, 8, 7), s(32.5, 9, 8, 8), s(32.5, 9, 9, 9), s(35.0, 8, 8, 7))
    private val lat = lift(s(45.0, 8, 8, 7), s(47.5, 8, 7, 7), s(47.5, 8, 8, 8), s(50.0, 9, 9, 8), s(52.5, 9, 9, 8), s(55.0, 9, 8, 8))
    private val ohp = lift(s(25.0, 8, 8, 7), s(27.5, 8, 8, 7), s(27.5, 9, 9, 8), s(30.0, 8, 7, 7), s(30.0, 8, 7, 7), s(30.0, 7, 7, 7))
    private val tri = lift(s(12.5, 8, 8, 7), s(12.5, 9, 9, 8), s(15.0, 8, 8, 7), s(15.0, 9, 9, 9), s(17.5, 9, 9, 8), s(17.5, 9, 9, 9))

    private fun model(l: List<LiftSession>, n0: Int = 5, perWeek: Double = 1.0) =
        ExpectedProgress.model(l, n0, repMin = 7, repMax = 9, increment = 2.5, sessionsPerWeek = perWeek)!!

    @Test fun statusComparesRecentWithUsualPace() {
        // Steady small gains: recent pace close to the usual one.
        assertEquals(PaceStatus.OnTrack, model(smith).status)
        // A 2.5 kg step almost every session, before and lately alike.
        assertEquals(PaceStatus.OnTrack, model(lat).status)
        // Reps a little down at 30 kg for 3 sessions: within one double-progression step of its slow usual pace.
        assertEquals(PaceStatus.OnTrack, model(ohp).status)
        // +5 kg in 6 sessions, steadily.
        assertEquals(PaceStatus.OnTrack, model(tri).status)
    }

    /** Sample logged sessions (8 or 6 estimated sessions before), one a week, 7–9 reps, +2.5 kg. */
    @Test fun sampleDoubleProgression() {
        val legExt = lift(s(40.0, 8, 8, 8), s(40.0, 8, 8, 8), s(50.0, 8, 8, 8), s(55.0, 7, 7, 7), s(60.0, 8, 8, 8))
        val shoulder = lift(
            s(20.0, 8, 8, 8), s(25.0, 7, 7, 7), s(25.0, 7, 7, 7), s(25.0, 7, 7, 7), s(30.0, 7, 7, 7), s(30.0, 7, 7, 7),
        )
        val row = lift(
            s(55.0, 8, 8, 8), s(55.0, 8, 8, 8), s(55.0, 8, 8, 7), s(55.0, 8, 8, 7), s(60.0, 7, 6, 6), s(60.0, 7, 7, 7),
        )
        val pushdown = lift(
            s(15.0, 8, 8, 8), s(15.0, 8, 8, 8), s(15.0, 8, 8, 7), s(15.0, 8, 8, 8), s(17.5, 7, 7, 7), s(17.5, 7, 7, 7),
        )
        val dips = lift(s(80.0, 9, 9, 9), s(90.0, 8, 8, 8), s(90.0, 8, 8, 8), s(90.0, 8, 8, 8), s(90.0, 8, 8, 8))
        val le = model(legExt, n0 = 6)
        // +20 kg in 3 sessions, but the curve is steep over that stretch too: within 35 % of its pace.
        assertEquals(PaceStatus.OnTrack, le.status)
        assertEquals(4.98, le.pacePerWeek, 0.01)
        val sp = model(shoulder, n0 = 8)
        assertEquals(PaceStatus.OnTrack, sp.status)
        assertEquals(1.58, sp.pacePerWeek, 0.01)
        val r = model(row, n0 = 8)
        assertEquals(PaceStatus.OnTrack, r.status)
        assertEquals(0.60, r.pacePerWeek, 0.01)
        assertEquals(PaceStatus.OnTrack, model(pushdown, n0 = 8).status)
        // 90 kg × 8 for 4 sessions after a fast start: behind its own pace.
        assertEquals(PaceStatus.Behind, model(dips, n0 = 6).status)
        listOf(le, sp, r).forEach { assertTrue(it.pacePerWeek in 0.0..5.0 && it.b > 0) }
    }

    @Test fun paceScalesWithSessionsPerWeek() {
        val one = model(smith); val two = model(smith, perWeek = 2.0)
        assertEquals(2 * one.pacePerWeek, two.pacePerWeek, 1e-9)
        assertEquals(2 * one.recentPerWeek, two.recentPerWeek, 1e-9)
    }

    @Test fun toleranceScalesWithSessionsPerWeek() {
        // 40 % of the increment per session: at 2 sessions a week the band doubles, so the status does not change.
        listOf(smith, lat, ohp, tri).forEach { l ->
            val one = model(l); val two = model(l, perWeek = 2.0)
            assertEquals(2 * one.tolerance, two.tolerance, 1e-9)
            assertEquals(one.status, two.status)
        }
        assertEquals(1.0, model(ohp).tolerance, 1e-9)
    }

    @Test fun sessionsOnTheCurveAreOnTrack() {
        // est. max = 40 + 10 ln k exactly: recent and usual pace agree (the curve's slope at the last session would be
        // ~28 % lower and read as Ahead with a tighter band).
        val reps = 8
        val onCurve = (1..6).map { k ->
            val kg = (40 + 10 * kotlin.math.ln(k.toDouble())) / (1 + reps / 30.0)
            LiftSession(k.toLong(), s(kg, reps))
        }
        val m = model(onCurve, n0 = 0)
        assertEquals(PaceStatus.OnTrack, m.status)
        assertEquals(m.pacePerWeek, m.recentPerWeek, 0.01 * m.pacePerWeek)
    }

    @Test fun tooFewSessionsGiveNoModel() {
        assertNull(ExpectedProgress.model(smith.take(3), 5, 7, 9, 2.5, 1.0))
        assertNotNull(ExpectedProgress.model(smith.take(4), 5, 7, 9, 2.5, 1.0))
        assertNull(ExpectedProgress.nextJump(smith.take(1), 9, 2.5))
    }

    @Test fun implausibleReps() {
        assertTrue(ExpectedProgress.plausibleReps(15, 9))
        assertTrue(!ExpectedProgress.plausibleReps(16, 9))
        assertTrue(!ExpectedProgress.plausibleReps(83, 9))
        assertTrue(ExpectedProgress.plausibleReps(30, null))
        assertTrue(!ExpectedProgress.plausibleReps(31, 25))
    }

    @Test fun nextJump() {
        // 35 kg once: falls back to gains at repeated weights (+0.56 reps a session), 1.33 reps to go -> ~2 sessions.
        val a = ExpectedProgress.nextJump(smith, 9, 2.5)!!
        assertEquals(2, a.sessions); assertEquals(1, a.atWeight); assertEquals(0.556, a.gain!!, 1e-3)
        // Reps fell over 3 sessions at 30 kg: no estimate.
        val b = ExpectedProgress.nextJump(ohp, 9, 2.5)!!
        assertNull(b.sessions); assertEquals(3, b.atWeight)
        // Top of range on every set: next session.
        assertEquals(1, ExpectedProgress.nextJump(tri, 9, 2.5)!!.sessions)
    }

    @Test fun typicalRangeGrowsFromStart() {
        assertEquals(20.0 to 20.0, ExpectedProgress.typicalRange(20.0, 0.0))
        val (lo, hi) = ExpectedProgress.typicalRange(20.0, 4.5)
        assertEquals(24.0, lo, 1e-9); assertEquals(31.0, hi, 1e-9)
    }

    @Test fun scheduleFromRecentWeekdays() {
        val mon = LocalDate.of(2026, 11, 16)
        val dates = (0L until 4).flatMap { w -> listOf(0L, 2, 4, 6).map { mon.minusWeeks(w).plusDays(it - 7) } } +
            LocalDate.of(2026, 11, 3) // one-off Tuesday
        assertEquals(setOf(MONDAY, WEDNESDAY, FRIDAY, SUNDAY), Schedule.trainingDays(dates))
    }

    @Test fun nextDatesFollowRotation() {
        // ULUL = days 1..4, Upper A (1) done Monday 16 Nov; Mon/Wed/Fri/Sun schedule.
        val week = setOf(MONDAY, WEDNESDAY, FRIDAY, SUNDAY)
        val mon = LocalDate.of(2026, 11, 16)
        assertEquals(
            listOf(LocalDate.of(2026, 11, 23), LocalDate.of(2026, 11, 30)),
            Schedule.nextDates(listOf(1, 2, 3, 4), 1, week, setOf(1), mon, 2),
        )
        assertEquals(listOf(LocalDate.of(2026, 11, 20)), Schedule.nextDates(listOf(1, 2, 3, 4), 1, week, setOf(3), mon, 1))
        assertEquals(emptyList<LocalDate>(), Schedule.nextDates(listOf(1, 2), 1, emptySet(), setOf(1), mon, 1))
    }

    @Test fun nextDatesCountEveryDayWithTheLift() {
        // Lift on Upper A (1) and Upper B (3): sessions on Fri 20 (B) and Mon 23 (A).
        val week = setOf(MONDAY, WEDNESDAY, FRIDAY, SUNDAY)
        assertEquals(
            listOf(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 23)),
            Schedule.nextDates(listOf(1, 2, 3, 4), 1, week, setOf(1, 3), LocalDate.of(2026, 11, 16), 2),
        )
    }

    @Test fun estMaxUsesWorkingSetsOnly() {
        val warm = s(20.0, 10) + s(30.0, 9, 9, 8)
        assertEquals(LiftSession(0, s(30.0, 9, 9, 8)).estMax, LiftSession(0, warm).estMax, 1e-9)
        assertEquals(30.0 * (1 + 26 / 90.0), LiftSession(0, warm).estMax, 1e-9)
    }
}
