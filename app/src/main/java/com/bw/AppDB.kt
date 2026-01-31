package com.bw

import android.content.Context
import androidx.room.*

@Entity(tableName = "workout_days")
data class WorkoutDayEntity(
    @PrimaryKey val date: String // "YYYY-MM-DD"
)

@Entity(
    tableName = "workout_exercises",
    primaryKeys = ["date", "name"]
)
data class WorkoutExerciseEntity(
    val date: String,
    val name: String,
    val position: Int
)

@Entity(
    tableName = "workout_sets",
    primaryKeys = ["date", "exerciseName", "setIndex"]
)
data class WorkoutSetEntity(
    val date: String,
    val exerciseName: String,
    val setIndex: Int,
    val reps: Int
)

data class ExerciseTotal(
    val exerciseName: String,
    val totalReps: Int
)

@Dao
interface WorkoutDao {
    @Query("SELECT date FROM workout_days ORDER BY date DESC")
    suspend fun getActiveDates(): List<String>

    @Query("SELECT DISTINCT name FROM workout_exercises ORDER BY name ASC")
    suspend fun getAllExerciseNames(): List<String>

    @Query("SELECT * FROM workout_exercises WHERE date = :date ORDER BY position ASC")
    suspend fun getExercises(date: String): List<WorkoutExerciseEntity>

    @Query("SELECT * FROM workout_sets WHERE date = :date")
    suspend fun getSets(date: String): List<WorkoutSetEntity>

    @Query("DELETE FROM workout_days WHERE date = :date")
    suspend fun deleteDay(date: String)

    @Query("DELETE FROM workout_exercises WHERE date = :date")
    suspend fun deleteExercises(date: String)

    @Query("DELETE FROM workout_sets WHERE date = :date")
    suspend fun deleteSets(date: String)

    @Query("SELECT COALESCE(SUM(reps), 0) FROM workout_sets")
    suspend fun getAllTimeTotalReps(): Int

    @Query("""
    SELECT exerciseName AS exerciseName, COALESCE(SUM(reps), 0) AS totalReps
    FROM workout_sets
    GROUP BY exerciseName
    ORDER BY totalReps DESC
    """)
    suspend fun getTotalsByExercise(): List<ExerciseTotal>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDay(day: WorkoutDayEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExercises(items: List<WorkoutExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSets(items: List<WorkoutSetEntity>)

    @Transaction
    suspend fun replaceDay(
        date: String,
        exercises: List<WorkoutExerciseEntity>,
        sets: List<WorkoutSetEntity>
    ) {
        deleteSets(date)
        deleteExercises(date)

        if (exercises.isEmpty()) {
            // no content: delete day header too
            deleteDay(date)
            return
        }

        upsertDay(WorkoutDayEntity(date))
        upsertExercises(exercises)
        if (sets.isNotEmpty()) upsertSets(sets)
    }
}

@Database(
    entities = [WorkoutDayEntity::class, WorkoutExerciseEntity::class, WorkoutSetEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao

    companion object {
        @Volatile private var INSTANCE: AppDb? = null

        fun get(context: Context): AppDb =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDb::class.java,
                    "bw.db"
                ).build().also { INSTANCE = it }
            }
    }
}


