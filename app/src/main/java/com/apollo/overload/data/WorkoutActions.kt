package com.apollo.overload.data

import androidx.room.withTransaction
import com.apollo.overload.domain.Rotation
import com.apollo.overload.domain.Session
import com.apollo.overload.domain.StartResult
import kotlinx.coroutines.flow.first

/* Workout writes shared by the session screen and the home-screen widget, so both write the same way. */

/** Starts a workout of [day] with its plan order. Does nothing (null) when a workout is already open or the day is empty. */
suspend fun Repository.startWorkout(day: PlanDay): Long? = db.withTransaction {
    if (dao.openWorkoutNow() != null) return@withTransaction null
    val plan = dao.dayExercisesNow(day.id)
    if (plan.isEmpty()) return@withTransaction null
    dao.insertWorkout(Workout(dayId = day.id, dayName = day.name, startedAt = System.currentTimeMillis(), order = plan.map { it.id }))
}

/** The next day of the active split in the rotation, or null without a plan. */
suspend fun Repository.nextDay(): PlanDay? {
    val split = dao.activeSplit().first() ?: return null
    val days = dao.days(split.id).first()
    val id = Rotation.nextDay(days.map { it.id }, dao.workouts().first())
    return days.firstOrNull { it.id == id }
}

/**
 * Logs set [setIndex] of plan row [pe]. When the set [finishes] the exercise and [markNextUp] still agrees, the
 * workout's "Next up" is set in the same transaction, so it survives the caller going away. Returns whether it was set.
 */
suspend fun Repository.logSet(
    workoutId: Long, pe: PlanExercise, setIndex: Int, kg: Double, reps: Int, finishes: Boolean,
    markNextUp: () -> Boolean = { true },
): Boolean = db.withTransaction {
    dao.insertSet(
        SetEntry(
            workoutId = workoutId, exerciseId = pe.exerciseId, planExerciseId = pe.id, setIndex = setIndex,
            kg = kg, reps = reps, loggedAt = System.currentTimeMillis(),
        )
    )
    (finishes && markNextUp()).also { if (it) dao.setNextUpFrom(workoutId, pe.id) }
}

/**
 * Starts exercise [id] of workout [workoutId] now (the pair rule may re-order the rest) and leaves "Next up".
 * Returns null when the workout is gone or the exercise is already done.
 */
suspend fun Repository.startExercise(workoutId: Long, id: Long): StartResult? = db.withTransaction {
    val cur = dao.workout(workoutId)?.takeIf { it.finishedAt == null } ?: return@withTransaction null
    val types = dao.allExercises().associate { it.id to it.type }
    val s = Session(cur.todayPlan(dao.dayExercisesNow(cur.dayId)), cur.order, dao.setsNow(cur.id), types, cur.setCounts)
    if (s.isDone(id)) return@withTransaction null
    val r = s.start(id, cur.pullFirst)
    if (r.order != cur.order || r.pullFirst != cur.pullFirst) dao.setOrder(cur.id, r.order, r.pullFirst)
    dao.setNextUpFrom(cur.id, null)
    r
}

/**
 * Replaces plan row [planExId] with [exerciseId] for workout [workoutId] only (null or the planned exercise = back to
 * plan). The load comes from the split's row of that exercise, else its last session, else the planned load (0 for
 * bodyweight). Does nothing once the row has logged sets.
 */
suspend fun Repository.replaceExercise(workoutId: Long, planExId: Long, exerciseId: String?) = db.withTransaction {
    val cur = dao.workout(workoutId)?.takeIf { it.finishedAt == null } ?: return@withTransaction
    if (dao.setsNow(cur.id).any { it.planExerciseId == planExId }) return@withTransaction
    val row = dao.dayExercisesNow(cur.dayId).firstOrNull { it.id == planExId } ?: return@withTransaction
    val swaps = if (exerciseId == null || exerciseId == row.exerciseId) cur.swaps - planExId else {
        val shared = dao.splitExercisesNow(cur.dayId).firstOrNull { it.exerciseId == exerciseId }
        val ex = dao.exercise(exerciseId)
        // Never done: start from the planned load as a guess (bodyweight exercises from 0).
        val kg = shared?.kg ?: dao.lastSessionSets(exerciseId, cur.id).maxOfOrNull { it.kg }
            ?: if (ex?.equipment == "bodyweight") 0.0 else row.kg
        val pct = shared?.incrementPct ?: PlanExercise.defaultPct(ex?.type)
        cur.swaps + (planExId to Swap(exerciseId, kg, shared?.increment ?: row.increment, pct))
    }
    if (swaps != cur.swaps) dao.setSwaps(cur.id, swaps)
}
