package `in`.gov.tribalfln.data

import android.content.Context
import android.util.Log
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "student_progress",
    indices = [
        Index(value = ["competencyCode"]),
        Index(value = ["gradeLevel"]),
        Index(value = ["studentId"])
    ]
)
data class StudentProgressEntity(
    @PrimaryKey val studentId: String,
    val name: String,
    val gradeLevel: Int = 1,
    val languageCode: String = "san",
    val competencyCode: String = "",
    val masteryPercentage: Float = 0f,
    val totalAttempts: Int = 0,
    val correctAttempts: Int = 0,
    val lastActivityTimestamp: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)

data class CompetencyMastery(
    @ColumnInfo(name = "competencyCode") val code: String,
    val competencyName: String,
    val masteryPct: Float
)

@Dao
interface ProgressDao {
    @Query("SELECT COUNT(*) FROM student_progress WHERE isActive = 1")
    fun getActiveStudentCount(): Flow<Int>

    @Query("SELECT AVG(masteryPercentage) FROM student_progress WHERE isActive = 1")
    fun getClassMasteryPercentage(): Flow<Float>

    @Query("SELECT * FROM student_progress WHERE studentId = :studentId")
    fun getByStudentId(studentId: String): Flow<List<StudentProgressEntity>>

    @Query("SELECT * FROM student_progress WHERE competencyCode = :code")
    fun getByCompetencyCode(code: String): Flow<List<StudentProgressEntity>>

    @Query("SELECT * FROM student_progress WHERE gradeLevel = :gradeLevel AND isActive = 1")
    fun getByGradeLevel(gradeLevel: Int): Flow<List<StudentProgressEntity>>

    @Query("SELECT competencyCode, name as competencyName, masteryPercentage as masteryPct FROM student_progress WHERE studentId = :studentId AND isActive = 1")
    fun getCompetencyMasteryForStudent(studentId: String): Flow<List<CompetencyMastery>>

    @Query("UPDATE student_progress SET masteryPercentage = :mastery, totalAttempts = totalAttempts + 1, correctAttempts = correctAttempts + :correctDelta, lastActivityTimestamp = :timestamp WHERE studentId = :studentId AND competencyCode = :code")
    suspend fun updateMastery(studentId: String, code: String, mastery: Float, correctDelta: Int, timestamp: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(student: StudentProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(students: List<StudentProgressEntity>)

    @Query("DELETE FROM student_progress WHERE isActive = 0")
    suspend fun purgeInactiveStudents()
}

@Database(
    entities = [StudentProgressEntity::class],
    version = 1,
    exportSchema = false
)
abstract class StudentProgressDatabase : RoomDatabase() {

    companion object {
        private const val TAG = "StudentProgressDB"
        private const val DB_NAME = "student_progress.db"

        @Volatile
        private var INSTANCE: StudentProgressDatabase? = null

        fun getInstance(context: Context): StudentProgressDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }

        private fun buildDatabase(context: Context): StudentProgressDatabase {
            val startTime = System.currentTimeMillis()
            val database = Room.databaseBuilder(
                context.applicationContext,
                StudentProgressDatabase::class.java,
                DB_NAME
            )
                .fallbackToDestructiveMigration()
                .build()
            val elapsed = System.currentTimeMillis() - startTime
            Log.i(TAG, "Student progress database created in ${elapsed}ms")
            return database
        }
    }

    abstract fun progressDao(): ProgressDao
}
