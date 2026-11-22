package com.apollo.overload.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json { ignoreUnknownKeys = true }
    @TypeConverter fun musclesToString(v: Map<String, Float>): String = json.encodeToString(v)
    @TypeConverter fun stringToMuscles(s: String): Map<String, Float> = json.decodeFromString(s)
    @TypeConverter fun idsToString(v: List<Long>): String = json.encodeToString(v)
    @TypeConverter fun stringToIds(s: String): List<Long> = json.decodeFromString(s)
    @TypeConverter fun countsToString(v: Map<Long, Int>): String = json.encodeToString(v)
    @TypeConverter fun stringToCounts(s: String): Map<Long, Int> = json.decodeFromString(s)
    @TypeConverter fun swapsToString(v: Map<Long, Swap>): String = json.encodeToString(v)
    @TypeConverter fun stringToSwaps(s: String): Map<Long, Swap> = json.decodeFromString(s)
}

@Dao
interface GymDao {
    // Exercises
    @Query("SELECT * FROM exercise ORDER BY name") fun exercises(): Flow<List<Exercise>>
    @Query("SELECT * FROM exercise ORDER BY name") suspend fun allExercises(): List<Exercise>
    @Query("SELECT * FROM exercise WHERE id = :id") suspend fun exercise(id: String): Exercise?
    @Query("SELECT COUNT(*) FROM exercise") suspend fun exerciseCount(): Int
    @Upsert suspend fun upsertExercises(list: List<Exercise>)
    @Upsert suspend fun upsertExercise(e: Exercise)

    // Splits and plan
    @Query("SELECT * FROM split ORDER BY id") fun splits(): Flow<List<Split>>
    @Query("SELECT * FROM split ORDER BY id") suspend fun allSplits(): List<Split>
    @Query("SELECT * FROM split WHERE active = 1 LIMIT 1") fun activeSplit(): Flow<Split?>
    @Insert suspend fun insertSplit(s: Split): Long
    @Update suspend fun updateSplit(s: Split)
    @Query("UPDATE split SET active = (id = :id)") suspend fun activateSplit(id: Long)
    @Delete suspend fun deleteSplit(s: Split)

    @Query("SELECT * FROM plan_day WHERE splitId = :splitId ORDER BY position") fun days(splitId: Long): Flow<List<PlanDay>>
    @Query("SELECT * FROM plan_day ORDER BY splitId, position") suspend fun allDays(): List<PlanDay>
    @Query("SELECT * FROM plan_day WHERE id = :id") suspend fun day(id: Long): PlanDay?
    @Insert suspend fun insertDay(d: PlanDay): Long
    @Update suspend fun updateDay(d: PlanDay)
    @Delete suspend fun deleteDay(d: PlanDay)

    @Query("SELECT * FROM plan_exercise WHERE dayId = :dayId ORDER BY position") fun dayExercises(dayId: Long): Flow<List<PlanExercise>>
    @Query("SELECT * FROM plan_exercise WHERE dayId = :dayId ORDER BY position") suspend fun dayExercisesNow(dayId: Long): List<PlanExercise>
    @Query("SELECT * FROM plan_exercise ORDER BY dayId, position") suspend fun allPlanExercises(): List<PlanExercise>
    /** Every plan row of the split that day [dayId] belongs to. */
    @Query("""SELECT pe.* FROM plan_exercise pe JOIN plan_day d ON d.id = pe.dayId
        WHERE d.splitId = (SELECT splitId FROM plan_day WHERE id = :dayId) ORDER BY pe.dayId, pe.position""")
    suspend fun splitExercisesNow(dayId: Long): List<PlanExercise>
    @Insert suspend fun insertPlanExercise(p: PlanExercise): Long
    @Update suspend fun updatePlanExercises(list: List<PlanExercise>)
    @Delete suspend fun deletePlanExercise(p: PlanExercise)

    // Workouts
    @Query("SELECT * FROM workout WHERE finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1") fun openWorkout(): Flow<Workout?>
    @Query("SELECT * FROM workout WHERE finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1") suspend fun openWorkoutNow(): Workout?
    @Query("SELECT * FROM workout ORDER BY startedAt DESC") fun workouts(): Flow<List<Workout>>
    @Query("SELECT * FROM workout ORDER BY startedAt") suspend fun allWorkouts(): List<Workout>
    @Query("SELECT * FROM workout WHERE id = :id") suspend fun workout(id: Long): Workout?
    @Insert suspend fun insertWorkout(w: Workout): Long
    @Update suspend fun updateWorkout(w: Workout)
    @Delete suspend fun deleteWorkout(w: Workout)
    /** Column-only writes, so they never put back a stale copy of the rest of the row. */
    @Query("UPDATE workout SET nextUpFrom = :v WHERE id = :id AND nextUpFrom IS NOT :v") suspend fun setNextUpFrom(id: Long, v: Long?)
    @Query("UPDATE workout SET `order` = :order, pullFirst = :pullFirst WHERE id = :id") suspend fun setOrderJson(id: Long, order: String, pullFirst: Boolean)
    suspend fun setOrder(id: Long, order: List<Long>, pullFirst: Boolean) = setOrderJson(id, Converters().idsToString(order), pullFirst)
    @Query("UPDATE workout SET setCounts = :counts WHERE id = :id") suspend fun setSetCountsJson(id: Long, counts: String)
    suspend fun setSetCounts(id: Long, counts: Map<Long, Int>) = setSetCountsJson(id, Converters().countsToString(counts))
    @Query("UPDATE workout SET swaps = :swaps WHERE id = :id") suspend fun setSwapsJson(id: Long, swaps: String)
    suspend fun setSwaps(id: Long, swaps: Map<Long, Swap>) = setSwapsJson(id, Converters().swapsToString(swaps))
    /** Keeps an open workout of [dayId] in step with its plan: [planExerciseId] joins at the end, or leaves it. */
    @Transaction
    suspend fun syncOpenWorkout(dayId: Long, planExerciseId: Long, add: Boolean) {
        val w = openWorkoutNow()?.takeIf { it.dayId == dayId } ?: return
        val order = if (add) w.order + planExerciseId else w.order - planExerciseId
        if (order != w.order) setOrder(w.id, order, w.pullFirst)
    }

    @Query("SELECT * FROM set_entry WHERE workoutId = :workoutId ORDER BY loggedAt") fun sets(workoutId: Long): Flow<List<SetEntry>>
    @Query("SELECT * FROM set_entry WHERE workoutId = :workoutId ORDER BY loggedAt") suspend fun setsNow(workoutId: Long): List<SetEntry>
    @Query("SELECT * FROM set_entry ORDER BY loggedAt") fun allSets(): Flow<List<SetEntry>>
    @Query("SELECT * FROM set_entry ORDER BY loggedAt") suspend fun allSetsNow(): List<SetEntry>
    @Query("SELECT * FROM set_entry WHERE loggedAt >= :since ORDER BY loggedAt") fun setsSince(since: Long): Flow<List<SetEntry>>
    /** Sets of the most recent finished workout (other than [excludeWorkoutId]) that contained [exerciseId]. */
    @Query("""SELECT * FROM set_entry WHERE exerciseId = :exerciseId AND workoutId = (
        SELECT s.workoutId FROM set_entry s JOIN workout w ON w.id = s.workoutId
        WHERE s.exerciseId = :exerciseId AND s.workoutId != :excludeWorkoutId AND w.finishedAt IS NOT NULL
        ORDER BY s.loggedAt DESC LIMIT 1) ORDER BY setIndex""")
    suspend fun lastSessionSets(exerciseId: String, excludeWorkoutId: Long): List<SetEntry>
    @Insert suspend fun insertSet(s: SetEntry): Long
    @Update suspend fun updateSet(s: SetEntry)
    @Delete suspend fun deleteSet(s: SetEntry)

    // Import (replace everything)
    @Query("DELETE FROM set_entry") suspend fun clearSets()
    @Query("DELETE FROM workout") suspend fun clearWorkouts()
    @Query("DELETE FROM plan_exercise") suspend fun clearPlanExercises()
    @Query("DELETE FROM plan_day") suspend fun clearDays()
    @Query("DELETE FROM split") suspend fun clearSplits()
    @Query("DELETE FROM exercise WHERE custom = 1") suspend fun clearCustomExercises()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSplits(l: List<Split>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putDays(l: List<PlanDay>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putPlanExercises(l: List<PlanExercise>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putWorkouts(l: List<Workout>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSets(l: List<SetEntry>)
    /** One shared load per exercise in a split, as in [MIGRATION_2_3]; for backups from before it. */
    @Query(SHARE_SPLIT_LOADS) suspend fun shareSplitLoads()

    @Transaction
    suspend fun replaceAll(b: Backup) {
        clearCustomExercises(); clearSets(); clearWorkouts(); clearPlanExercises(); clearDays(); clearSplits()
        upsertExercises(b.exercises)
        putSplits(b.splits); putDays(b.days); putPlanExercises(b.planExercises); putWorkouts(b.workouts); putSets(b.sets)
    }
}

@Database(
    entities = [Exercise::class, Split::class, PlanDay::class, PlanExercise::class, Workout::class, SetEntry::class],
    version = 4,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@TypeConverters(Converters::class)
abstract class GymDb : RoomDatabase() {
    abstract fun dao(): GymDao

    companion object {
        fun create(context: Context): GymDb =
            Room.databaseBuilder(context, GymDb::class.java, "overload.db").addMigrations(MIGRATION_2_3, MIGRATION_3_4).build()
    }
}

/**
 * 2 -> 3: percentage load increase (10 % for legs, 5 % otherwise; increment stays as the load step), and one shared
 * load per exercise in a split: rows of the same exercise take the kg of the row logged most recently in a finished
 * workout (rows never logged that way keep their kg). Also adds the workout's per-exercise set-count overrides.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE plan_exercise ADD COLUMN incrementPct REAL NOT NULL DEFAULT 5")
        db.execSQL("UPDATE plan_exercise SET incrementPct = 10 WHERE exerciseId IN (SELECT id FROM exercise WHERE type = 'legs')")
        db.execSQL(SHARE_SPLIT_LOADS)
        db.execSQL("ALTER TABLE workout ADD COLUMN setCounts TEXT NOT NULL DEFAULT '{}'")
    }
}

/** 3 -> 4: today-only exercise replacements of a workout. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workout ADD COLUMN swaps TEXT NOT NULL DEFAULT '{}'")
    }
}

/** Rows of the same exercise in a split take the kg of the row logged most recently in a finished workout. */
const val SHARE_SPLIT_LOADS = """UPDATE plan_exercise SET kg = COALESCE((
    SELECT pe.kg FROM plan_exercise pe
    JOIN plan_day d ON d.id = pe.dayId
    JOIN set_entry s ON s.planExerciseId = pe.id
    JOIN workout w ON w.id = s.workoutId AND w.finishedAt IS NOT NULL
    WHERE pe.exerciseId = plan_exercise.exerciseId
      AND d.splitId = (SELECT splitId FROM plan_day WHERE id = plan_exercise.dayId)
    ORDER BY s.loggedAt DESC LIMIT 1), kg)"""

/** Export/import file format. exercises holds only custom or user-edited exercises. */
@kotlinx.serialization.Serializable
data class Backup(
    val version: Int = 1,
    val exportedAt: Long,
    val exercises: List<Exercise>,
    val splits: List<Split>,
    val days: List<PlanDay>,
    val planExercises: List<PlanExercise>,
    val workouts: List<Workout>,
    val sets: List<SetEntry>,
)
