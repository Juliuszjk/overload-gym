package com.apollo.overload.domain

import com.apollo.overload.data.PlanExercise
import com.apollo.overload.data.SetEntry
import com.apollo.overload.data.Workout
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/** Today's starting suggestion for one exercise: load for every set and target reps per set. */
data class Prescription(val kg: Double, val reps: List<Int>, val raised: Boolean)

/**
 * Double progression: work inside repMin..repMax; once every set reaches repMax, add [jump] (the plan's
 * [PlanExercise.incrementPct] of the load, rounded to the load step [PlanExercise.increment]) and start again from
 * repMin. The plan's kg is the load, shared by every row of the same exercise in a split; finishing a workout writes
 * the load used (plus the jump when earned) back to the plan.
 */
object Progression {

    fun round2(v: Double): Double = (v * 100).roundToLong() / 100.0

    /**
     * Load: always the plan's kg. Reps: [last] (sets of the most recent finished session of this exercise, ordered by
     * set index) + 1, capped at repMax; repMin when there is no history or the plan load is above last time's.
     */
    fun prescribe(p: PlanExercise, last: List<SetEntry>): Prescription {
        if (last.isEmpty()) return Prescription(p.kg, List(p.sets) { p.repMin }, raised = false)
        if (p.kg > last.maxOf { it.kg }) return Prescription(p.kg, List(p.sets) { p.repMin }, raised = true)
        val reps = List(p.sets) { k -> min(p.repMax, (last.getOrNull(k)?.reps ?: (p.repMin - 1)) + 1) }
        return Prescription(p.kg, reps, raised = false)
    }

    /**
     * Load and reps to prefill for set [index] (0-based) today. Load: the typed draft, else the load of today's
     * latest logged set, else the prescription. Reps: the typed draft, else the prescription for that set.
     */
    fun setTarget(
        p: PlanExercise, rx: Prescription, today: List<SetEntry>, index: Int,
        draftKg: Double? = null, draftReps: Int? = null,
    ): Pair<Double, Int> {
        val kg = draftKg ?: today.lastOrNull()?.kg ?: rx.kg
        val reps = draftReps ?: rx.reps.getOrNull(index) ?: rx.reps.lastOrNull() ?: p.repMin
        return kg to reps
    }

    fun topCount(p: PlanExercise, today: List<SetEntry>): Int = today.count { it.reps >= p.repMax }

    /** True when today's planned sets are all logged and every set reached the top of the range. */
    fun earned(p: PlanExercise, today: List<SetEntry>): Boolean =
        today.isNotEmpty() && today.size >= p.sets && today.all { it.reps >= p.repMax }

    /**
     * Kg added to [kg] when the progression is earned: [PlanExercise.incrementPct] of it rounded to the load step
     * [PlanExercise.increment], at least one step. Without a step, the new load is rounded to 0.5 kg.
     */
    fun jump(p: PlanExercise, kg: Double): Double {
        val step = p.increment
        val raw = kg * p.incrementPct / 100
        if (step <= 0) return round2(((kg + raw) * 2).roundToLong() / 2.0 - kg)
        return round2(max(step, (raw / step).roundToLong() * step))
    }

    fun nextKg(p: PlanExercise, today: List<SetEntry>): Double {
        val top = today.maxOf { it.kg }
        return round2(top + jump(p, top))
    }

    /**
     * Plan rows whose load should follow today's session when it finishes: the top load used, plus the jump when the
     * progression was earned. [plan] is the whole split: the load is shared, so every row of an exercise trained today
     * (on any day) takes it. Exercises without sets today stay as they are.
     */
    fun planUpdates(plan: List<PlanExercise>, today: Map<Long, List<SetEntry>>): List<PlanExercise> {
        val loads = plan.mapNotNull { p ->
            val sets = today[p.id].orEmpty()
            if (sets.isEmpty()) null else p.exerciseId to (if (earned(p, sets)) nextKg(p, sets) else sets.maxOf { it.kg })
        }.groupBy({ it.first }, { it.second }).mapValues { (_, kgs) -> kgs.max() }
        return plan.mapNotNull { p -> loads[p.exerciseId]?.let { kg -> p.copy(kg = kg).takeIf { kg != p.kg } } }
    }
}

/** Which day of the split comes next. */
object Rotation {
    /** The day after [lastDayId] in [dayIds] (wrapping), or the first day when there is no history. */
    fun nextDay(dayIds: List<Long>, lastDayId: Long?): Long? {
        if (dayIds.isEmpty()) return null
        val i = dayIds.indexOf(lastDayId)
        return if (i < 0) dayIds.first() else dayIds[(i + 1) % dayIds.size]
    }

    /** The day after the most recently started finished workout of [dayIds]. */
    fun nextDay(dayIds: List<Long>, workouts: List<Workout>): Long? =
        nextDay(dayIds, workouts.filter { it.finishedAt != null && it.dayId in dayIds }.maxByOrNull { it.startedAt }?.dayId)
}
