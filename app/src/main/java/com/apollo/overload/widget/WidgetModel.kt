package com.apollo.overload.widget

import com.apollo.overload.data.Exercise
import com.apollo.overload.data.PlanDay
import com.apollo.overload.data.PlanExercise
import com.apollo.overload.data.SetEntry
import com.apollo.overload.data.Workout
import com.apollo.overload.domain.Progression
import com.apollo.overload.domain.Session
import com.apollo.overload.domain.VsLast

/** Kg / reps adjusted with − / + on the widget, for one set of one exercise in one workout. */
data class Draft(val workoutId: Long, val planExId: Long, val setIndex: Int, val kg: Double? = null, val reps: Int? = null) {
    val key: String get() = key(workoutId, planExId, setIndex)

    companion object {
        fun key(workoutId: Long, planExId: Long, setIndex: Int) = "$workoutId:$planExId:$setIndex"
    }
}

/** One exercise segment of the track: [done] of [target] sets, [current] = the exercise being done. */
data class TrackSeg(val done: Int, val target: Int, val current: Boolean)

enum class RungState { Done, Now, Upcoming }

/** One set of the current exercise. [lastReps]: reps of the same set last time. */
data class Rung(val index: Int, val kg: Double, val reps: Int, val state: RungState, val lastReps: Int?, val top: Boolean)

/** −1 = lower than last time, 0 = same or no history, 1 = higher. */
typealias Vs = Int

/** What the widget shows, derived from the database (and the widget's own draft) only. */
sealed interface WidgetModel {
    /** No plan to start (no split, no days, or the next day has no exercises). */
    data class Empty(val dayName: String?) : WidgetModel

    /** No open workout: the next day in the rotation. */
    data class Idle(val dayName: String, val exercises: List<IdleLine>, val totalSets: Int) : WidgetModel

    data class IdleLine(val name: String, val kg: Double, val sets: Int, val repMin: Int, val repMax: Int)

    /** A set to log. kg / reps are what Log set writes. */
    data class Running(
        val workoutId: Long, val planExId: Long, val name: String,
        val setIndex: Int, val setCount: Int, val kg: Double, val reps: Int, val increment: Double,
        val kgVs: Vs, val repsVs: Vs,
        /** Last time's kg and reps for this set (or its last set); null = first time. */
        val last: Pair<Double, Int>?,
        /** The next unfinished exercise in session order; null = this is the last one. */
        val nextName: String?,
        val rungs: List<Rung>, val track: List<TrackSeg>,
    ) : WidgetModel {
        val draftKey: String get() = Draft.key(workoutId, planExId, setIndex)
    }

    /** An exercise was just finished; [name] is the next one, waiting for Start. */
    data class NextUp(
        val workoutId: Long, val planExId: Long, val name: String, val rank: Int, val total: Int,
        val kg: Double, val sets: Int, val repMin: Int, val repMax: Int,
        val prevName: String, val prevShort: String, val prevSets: List<Pair<Double, Int>>,
        /** The next load of the finished exercise when its progression was earned. */
        val prevNextKg: Double?,
        val lastSets: List<SetEntry>, val track: List<TrackSeg>,
    ) : WidgetModel

    /** Every exercise is done; finishing happens in the app (the summary writes the plan). */
    data class Done(val dayName: String, val sets: Int, val lines: List<DoneLine>) : WidgetModel

    data class DoneLine(val name: String, val short: String, val sets: Int, val nextKg: Double?)

    companion object {
        /**
         * The widget state, using the same domain logic as the session screen.
         *
         * @param dayPlan plan rows of the open workout's day (position order); unused when [workout] is null.
         * @param sets the open workout's sets.
         * @param last per exercise id: sets of the most recent finished session (DAO lastSessionSets).
         * @param nextDay the next day in the rotation and its rows, used when no workout is open.
         */
        fun derive(
            workout: Workout?, dayPlan: List<PlanExercise>, sets: List<SetEntry>,
            exercises: Map<String, Exercise>, last: Map<String, List<SetEntry>>, draft: Draft?,
            nextDay: PlanDay?, nextDayRows: List<PlanExercise>,
            shortName: (String) -> String = { it },
        ): WidgetModel {
            fun name(pe: PlanExercise) = exercises[pe.exerciseId]?.name ?: pe.exerciseId
            if (workout == null) {
                if (nextDay == null || nextDayRows.isEmpty()) return Empty(nextDay?.name)
                return Idle(
                    nextDay.name, nextDayRows.map { IdleLine(name(it), it.kg, it.sets, it.repMin, it.repMax) },
                    nextDayRows.sumOf { it.sets },
                )
            }
            val s = Session(dayPlan, workout.order, sets, exercises.mapValues { it.value.type }, workout.setCounts)
            val active = s.active
            if (active == null) {
                return Done(workout.dayName, sets.size, s.order.map { id ->
                    val pe = s.planRow(id)!!
                    val today = s.today(id)
                    DoneLine(name(pe), shortName(name(pe)), today.size, if (Progression.earned(pe, today)) Progression.nextKg(pe, today) else null)
                })
            }
            val pe = s.planRow(active)!!
            val lastSets = last[pe.exerciseId].orEmpty()
            val rx = Progression.prescribe(pe, lastSets)
            val today = s.today(active)
            val i = today.size
            val n = s.target(active)
            // Same rule as the session screen: "Next up" shows while the just-finished exercise is done.
            val prev = workout.nextUpFrom?.takeIf { it in s.order && s.isDone(it) }
            val track = s.order.map { TrackSeg(s.today(it).size, s.target(it), it == active && prev == null) }
            if (prev != null) {
                val prevPe = s.planRow(prev)!!
                val prevSets = s.today(prev)
                return NextUp(
                    workout.id, active, name(pe), s.rank(active), s.order.size,
                    Progression.setTarget(pe, rx, today, i).first, n, pe.repMin, pe.repMax,
                    name(prevPe), shortName(name(prevPe)), prevSets.map { it.kg to it.reps },
                    if (Progression.earned(prevPe, prevSets)) Progression.nextKg(prevPe, prevSets) else null,
                    lastSets, track,
                )
            }
            val d = draft?.takeIf { it.workoutId == workout.id && it.planExId == active && it.setIndex == i }
            val (kg, reps) = Progression.setTarget(pe, rx, today, i, d?.kg, d?.reps)
            val rungs = (0 until n).map { k ->
                val logged = today.getOrNull(k)
                val lastReps = lastSets.getOrNull(k)?.reps
                when {
                    logged != null -> Rung(k, logged.kg, logged.reps, RungState.Done, lastReps, logged.reps >= pe.repMax)
                    k == i -> Rung(k, kg, reps, RungState.Now, lastReps, false)
                    else -> Progression.setTarget(pe, rx, today, k, d?.kg).let { (tk, tr) -> Rung(k, tk, tr, RungState.Upcoming, lastReps, false) }
                }
            }
            val next = s.order.firstOrNull { !s.isDone(it) && it != active }?.let { s.planRow(it) }
            return Running(
                workout.id, active, name(pe), i, n, kg, reps, pe.increment,
                kgVs = sign(VsLast.kg(kg, lastSets)), repsVs = sign(VsLast.reps(reps, lastSets, i)?.toDouble()),
                last = (lastSets.getOrNull(i) ?: lastSets.lastOrNull())?.let { it.kg to it.reps },
                nextName = next?.let(::name), rungs = rungs, track = track,
            )
        }

        private fun sign(d: Double?): Vs = when {
            d == null || d == 0.0 -> 0
            d > 0 -> 1
            else -> -1
        }
    }
}
