package com.apollo.overload.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Single access point for screens. Screens observe DAO flows directly via [dao]. */
class Repository(private val context: Context, val db: GymDb) {
    val dao = db.dao()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Loads the bundled exercise DB, keeping user-edited ones. */
    suspend fun init() {
        val custom = dao.allExercises().filter { it.custom }.map { it.id }.toSet()
        val bundled: List<Exercise> = context.assets.open("exercises.json").use {
            json.decodeFromString(it.readBytes().decodeToString())
        }
        dao.upsertExercises(bundled.filter { it.id !in custom })
    }

    suspend fun exportJson(): String = json.encodeToString(
        Backup.serializer(),
        Backup(
            exportedAt = System.currentTimeMillis(),
            exercises = dao.allExercises().filter { it.custom },
            splits = dao.allSplits(),
            days = dao.allDays(),
            planExercises = dao.allPlanExercises(),
            workouts = dao.allWorkouts(),
            sets = dao.allSetsNow(),
        )
    )

    suspend fun importJson(text: String) {
        val b = json.decodeFromString(Backup.serializer(), text)
        // Backups from before the percentage increase: fill it in by exercise type...
        val types = (dao.allExercises() + b.exercises).associate { it.id to it.type }
        val old = b.planExercises.any { it.incrementPct < 0 }
        db.withTransaction {
            dao.replaceAll(b.copy(planExercises = b.planExercises.map {
                if (it.incrementPct >= 0) it else it.copy(incrementPct = PlanExercise.defaultPct(types[it.exerciseId]))
            }))
            // ...and to one shared load per exercise in a split, like the 2 -> 3 migration.
            if (old) dao.shareSplitLoads()
        }
        // Brings back bundled exercises that were custom-edited before the import.
        init()
    }
}
