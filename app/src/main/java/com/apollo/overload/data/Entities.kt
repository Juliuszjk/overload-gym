package com.apollo.overload.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** Body-map muscle keys shared by the exercise DB, the body figure and the ranking. */
object Muscles {
    val all = listOf(
        "chest", "delts", "reardelts", "traps", "back", "lowerback", "biceps", "triceps", "forearms",
        "abs", "obliques", "glutes", "adductors", "quads", "hamstrings", "calves",
    )
    val label = mapOf(
        "chest" to "Chest", "delts" to "Front & side delts", "reardelts" to "Rear delts", "traps" to "Traps",
        "back" to "Upper back & lats", "lowerback" to "Lower back", "biceps" to "Biceps", "triceps" to "Triceps",
        "forearms" to "Forearms", "abs" to "Abs", "obliques" to "Obliques", "glutes" to "Glutes",
        "adductors" to "Adductors", "quads" to "Quads", "hamstrings" to "Hamstrings", "calves" to "Calves",
    )
}

/** type: push | pull | legs | core. muscles: muscle key -> activation 0..1 (1 = primary). */
@Serializable
@Entity(tableName = "exercise")
data class Exercise(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val equipment: String,
    val muscles: Map<String, Float>,
    val custom: Boolean = false,
)

/** A split such as ULUL; exactly one is active. */
@Serializable
@Entity(tableName = "split")
data class Split(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val active: Boolean = false,
)

/** kind: upper | lower | push | pull | legs | full | custom; drives "Suggested" in Add exercise. */
@Serializable
@Entity(tableName = "plan_day", indices = [Index("splitId")])
data class PlanDay(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val splitId: Long,
    val name: String,
    val kind: String,
    val position: Int,
)

/**
 * One exercise in a plan day. Adjacent rows with the same non-null [pairId] form a push/pull pair
 * (the plan order inside the pair is the "plan-first" order). kg is always the total load.
 */
@Serializable
@Entity(tableName = "plan_exercise", indices = [Index("dayId")])
data class PlanExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayId: Long,
    val exerciseId: String,
    val position: Int,
    val pairId: Int? = null,
    val kg: Double,
    val sets: Int = 3,
    val repMin: Int = 7,
    val repMax: Int = 9,
    /** Load step of the machine or plates; the progression jump is rounded to it. */
    val increment: Double = 2.5,
    /** Load increase in % when the progression is earned; negative = not set yet (an old backup), filled by type. */
    val incrementPct: Double = -1.0,
    /** Machine settings and the like, e.g. "Seat 5". */
    val note: String = "",
    /** How-to video link. */
    val url: String = "",
) {
    companion object {
        /** Default load increase in % by [Exercise.type]: legs take bigger jumps. */
        fun defaultPct(type: String?): Double = if (type == "legs") 10.0 else 5.0
    }
}

/**
 * order: plan-exercise ids in the order the session is being done (re-planned live).
 * nextUpFrom: the exercise just finished while the "Next up" screen shows, until the next one is started.
 * setCounts: per plan-exercise set-count overrides for today ("+ Set", or Skip after some sets were logged).
 */
@Serializable
@Entity(tableName = "workout", indices = [Index("dayId")])
data class Workout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayId: Long,
    val dayName: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val order: List<Long> = emptyList(),
    val pullFirst: Boolean = false,
    val nextUpFrom: Long? = null,
    val setCounts: Map<Long, Int> = emptyMap(),
)

@Serializable
@Entity(tableName = "set_entry", indices = [Index("workoutId"), Index("exerciseId")])
data class SetEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exerciseId: String,
    val planExerciseId: Long?,
    val setIndex: Int,
    val kg: Double,
    val reps: Int,
    val loggedAt: Long,
)
