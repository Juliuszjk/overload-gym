package com.apollo.overload.domain

import com.apollo.overload.data.SetEntry
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class PaceStatus(val label: String) { Ahead("Ahead"), OnTrack("On track"), Behind("Behind") }

/**
 * One workout of a lift: its start time and the logged sets (kg > 0, reps > 0) the est. max counts; [kg] is the working
 * weight, which may come from a set left out of [sets] (typo-like reps).
 */
class LiftSession(val time: Long, val sets: List<SetEntry>, val kg: Double = sets.maxOf { it.kg }) {
    /** Reps of the sets done at the working weight. */
    val reps: List<Int> = sets.filter { it.kg == kg }.map { it.reps }
    /** Epley, averaged over the sets done at the working weight (lighter warm-up sets left out). */
    val estMax: Double = reps.map { ExpectedProgress.epley(kg, it) }.average()
}

/**
 * Usual pace of a lift: decelerating curve est. max = a + b·ln(k) fitted to all logged sessions, k = session number of
 * this lift (counting the estimated sessions before [ExpectedProgress.LOGGED_FROM] too, so the curve flattens from where
 * training started). The status compares the recent pace (straight-line slope over the last [ExpectedProgress.RECENT]
 * sessions) with the curve's average slope over the same sessions.
 */
class PaceModel(
    val a: Double, val b: Double,
    /** Sessions before the logged data (estimates, left out of the fit). */
    val n0: Int,
    /** Logged sessions used. */
    val sessions: List<LiftSession>,
    /** Half-width of the band around the curve in est. max kg (spread of the sessions around it). */
    val half: Double,
    val status: PaceStatus,
    /** Average slope of the curve over the last [ExpectedProgress.RECENT] sessions (usual pace), chart kg per week. */
    val pacePerWeek: Double,
    /** Slope over the last [ExpectedProgress.RECENT] sessions, chart kg per week. */
    val recentPerWeek: Double,
    /** Recent pace this far from the usual pace (chart kg per week) still counts as on track. */
    val tolerance: Double,
    /** Reps the curve is converted to kg for (middle of the rep range). */
    val midReps: Double,
) {
    val n get() = sessions.size
    /** Curve value for logged session index [i] (0-based); future sessions continue past [n]. */
    fun curve(i: Int): Double = a + b * ln((n0 + i + 1).toDouble())
    /** Est. max shown as kg for [midReps] reps. */
    fun toKg(estMax: Double): Double = estMax / (1 + midReps / 30.0)
}

/** Next load jump: [sessions] null means reps are flat, so there is no estimate. */
class NextJump(
    val sessions: Int?, val increment: Double, val kg: Double, val avgReps: Double,
    /** Average reps per set gained per session (null when there is no same-weight pair yet). */
    val gain: Double?, val need: Double,
    /** Sessions done at the current weight. */
    val atWeight: Int,
)

object ExpectedProgress {
    /** Sessions before this date are treated as estimates and left out of the fit. */
    val LOGGED_FROM: LocalDate = LocalDate.of(2026, 10, 11)
    /** Sessions the recent pace is measured over (about 3 weeks at one session a week). */
    const val RECENT = 4
    private const val MIN_FIT = 4
    private const val BAND = 0.015

    fun epley(kg: Double, reps: Int): Double = kg * (1 + reps / 30.0)

    /** Reps well above the rep range (or over 30) are taken as typos: left out of the model, confirmed when logged. */
    fun plausibleReps(reps: Int, repMax: Int?): Boolean = reps <= minOf(30, (repMax ?: 30) + 6)

    private fun slope(xs: List<Double>, ys: List<Double>): Double {
        val mx = xs.average(); val my = ys.average()
        var sxy = 0.0; var sxx = 0.0
        xs.indices.forEach { sxy += (xs[it] - mx) * (ys[it] - my); sxx += (xs[it] - mx).pow(2) }
        return if (sxx == 0.0) 0.0 else sxy / sxx
    }

    /**
     * [logged]: sessions from [LOGGED_FROM] on, oldest first; [n0]: earlier sessions of the lift. Null with fewer than
     * [MIN_FIT] logged sessions. Ahead / behind when the recent pace is off the usual pace by more than 40 % of the
     * load increment a session (one double-progression step) or 35 % of the usual pace, whichever is larger, so a
     * session at the same weight while reps climb stays on track.
     */
    fun model(
        logged: List<LiftSession>, n0: Int, repMin: Int, repMax: Int, increment: Double, sessionsPerWeek: Double,
    ): PaceModel? {
        val n = logged.size
        if (n < MIN_FIT) return null
        val est = logged.map { it.estMax }
        val xs = (0 until n).map { ln((n0 + it + 1).toDouble()) }
        val b = slope(xs, est)
        val a = est.average() - b * xs.average()
        val rmse = sqrt(est.indices.sumOf { (est[it] - (a + b * xs[it])).pow(2) } / n)
        val half = max(rmse, BAND * est.last())
        val midReps = (repMin + repMax) / 2.0
        val toKg = 1 / (1 + midReps / 30.0)
        // Same window as the recent pace: a concave curve's slope at the last session would run below it.
        val usual = b * (ln((n0 + n).toDouble()) - ln((n0 + n - RECENT + 1).toDouble())) / (RECENT - 1) * sessionsPerWeek * toKg
        val recent = slope((0 until RECENT).map { it.toDouble() }, est.takeLast(RECENT)) * sessionsPerWeek * toKg
        val tol = max(0.4 * increment * sessionsPerWeek, 0.35 * abs(usual))
        val status = when {
            recent > usual + tol -> PaceStatus.Ahead
            recent < usual - tol -> PaceStatus.Behind
            else -> PaceStatus.OnTrack
        }
        return PaceModel(a, b, n0, logged, half, status, usual, recent, tol, midReps)
    }

    /**
     * Double progression: average reps per set gained per session at the current weight (else at any repeated
     * weight) against what is left to reach [repMax] on every set. Null with fewer than 2 sessions.
     */
    fun nextJump(logged: List<LiftSession>, repMax: Int, increment: Double): NextJump? {
        val n = logged.size
        if (n < 2) return null
        val cur = logged.last().kg
        val gains = mutableListOf<Double>()
        var i = n - 1
        while (i > 0 && logged[i].kg == cur && logged[i - 1].kg == cur) {
            gains += logged[i].reps.average() - logged[i - 1].reps.average(); i--
        }
        val atWeight = gains.size + 1
        val g = if (gains.isNotEmpty()) gains.average() else
            (1 until n).filter { logged[it].kg == logged[it - 1].kg }
                .map { logged[it].reps.average() - logged[it - 1].reps.average() }
                .takeIf { it.isNotEmpty() }?.average()
        val avgReps = logged.last().reps.average()
        val need = repMax - avgReps
        val sessions = when {
            need <= 0 -> 1
            g != null && g > 0.15 -> max(1, (need / g).roundToInt())
            else -> null
        }
        return NextJump(sessions, increment, cur, avgReps, g, need, atWeight)
    }

    /**
     * Typical beginner range for a lift [months] after training started at [startKg]: +20 % … +55 % at 4.5 months,
     * flattening (power 0.6). A rough reference, not a goal.
     */
    fun typicalRange(startKg: Double, months: Double): Pair<Double, Double> {
        val k = (max(0.0, months) / 4.5).pow(0.6)
        return startKg * (1 + 0.2 * k) to startKg * (1 + 0.55 * k)
    }
}

/** Training-day schedule derived from when workouts actually happen. */
object Schedule {
    /** Weekdays trained at least twice in the [weeks] weeks before [latest]; else every weekday trained in that time. */
    fun trainingDays(dates: List<LocalDate>, weeks: Long = 5): Set<DayOfWeek> {
        val latest = dates.maxOrNull() ?: return emptySet()
        val recent = dates.filter { it > latest.minusWeeks(weeks) }
        val counts = recent.groupingBy { it.dayOfWeek }.eachCount()
        return counts.filterValues { it >= 2 }.keys.ifEmpty { counts.keys }
    }

    /**
     * Dates of the next [count] sessions of any plan day in [targets]: walk the training weekdays after [after], each
     * one doing the next day of the split ([Rotation]) after [lastDayId].
     */
    fun nextDates(
        dayIds: List<Long>, lastDayId: Long?, weekdays: Set<DayOfWeek>, targets: Set<Long>, after: LocalDate, count: Int,
    ): List<LocalDate> {
        if (weekdays.isEmpty() || targets.none { it in dayIds } || count <= 0) return emptyList()
        val out = mutableListOf<LocalDate>()
        var last = lastDayId
        var d = after
        repeat(366 * 2) {
            if (out.size >= count) return out
            d = d.plusDays(1)
            if (d.dayOfWeek in weekdays) {
                last = Rotation.nextDay(dayIds, last)
                if (last in targets) out += d
            }
        }
        return out
    }
}
