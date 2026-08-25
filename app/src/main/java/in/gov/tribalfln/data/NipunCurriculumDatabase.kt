package `in`.gov.tribalfln.data

import android.content.Context
import android.util.Log
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Database(
    entities = [
        NipunCurriculumEntity::class,
        NipunCurriculumFts::class,
        NipunCompetencyEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class NipunCurriculumDatabase : RoomDatabase() {

    companion object {
        private const val TAG = "NipunCurriculumDB"
        private const val DB_NAME = "nipun_curriculum.db"
        private const val DB_ASSET_PATH = "database/nipun_curriculum_prepopulated.db"

        @Volatile
        private var INSTANCE: NipunCurriculumDatabase? = null

        fun getInstance(context: Context): NipunCurriculumDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }

        private fun buildDatabase(context: Context): NipunCurriculumDatabase {
            val startTime = System.currentTimeMillis()
            val database = Room.databaseBuilder(
                context.applicationContext,
                NipunCurriculumDatabase::class.java,
                DB_NAME
            )
                .createFromAsset(DB_ASSET_PATH)
                .fallbackToDestructiveMigration()
                .build()
            val elapsed = System.currentTimeMillis() - startTime
            Log.i(TAG, "Database created from asset in ${elapsed}ms — instant cold-start")
            return database
        }
    }

    abstract fun curriculumDao(): NipunCurriculumDao
    abstract fun competencyDao(): NipunCompetencyDao
}

enum class ContentType(val displayName: String, val displayNameHi: String) {
    LESSON_SCRIPT("Lesson Script", "पाठ स्क्रिप्ट"),
    ACTIVITY_INSTRUCTION("Activity Instruction", "गतिविधि निर्देश"),
    ASSESSMENT_PROMPT("Assessment Prompt", "मूल्यांकन प्रश्न")
}

@Fts4(
    contentEntity = NipunCurriculumEntity::class,
    tokenizer = FtsOptions.TOKENIZER_UNICODE61
)
@Entity(tableName = "nipun_curriculum_fts")
data class NipunCurriculumFts(
    @ColumnInfo(name = "titleHi") val titleHi: String,
    @ColumnInfo(name = "titleOlChiki") val titleOlChiki: String,
    @ColumnInfo(name = "description") val description: String,
    @ColumnInfo(name = "keywords") val keywords: String,
    @ColumnInfo(name = "contentType") val contentType: String
)

@Entity(
    tableName = "nipun_curriculum",
    indices = [
        Index(value = ["competencyCode"]),
        Index(value = ["gradeLevel"]),
        Index(value = ["subject"]),
        Index(value = ["contentType"])
    ]
)
data class NipunCurriculumEntity(
    @PrimaryKey val id: String,
    val competencyCode: String,
    val competencyName: String,
    val titleHi: String,
    val titleOlChiki: String,
    val description: String,
    val keywords: String,
    val gradeLevel: Int,
    val subject: String,
    val difficultyLevel: Int = 1,
    val contentType: String = ContentType.LESSON_SCRIPT.name,
    val audioAssetPath: String? = null,
    val imageAssetPath: String? = null,
    val isActive: Boolean = true
)

@Entity(
    tableName = "nipun_competencies",
    indices = [
        Index(value = ["code"]),
        Index(value = ["subject"])
    ]
)
data class NipunCompetencyEntity(
    @PrimaryKey(autoGenerate = true) val uid: Long = 0,
    val code: String,
    val name: String,
    val nameHi: String,
    val nameOlChiki: String,
    val subject: String,
    val gradeLevel: Int,
    val description: String
)

@Dao
interface NipunCurriculumDao {
    @Query("""
        SELECT nipun_curriculum.* FROM nipun_curriculum
        JOIN nipun_curriculum_fts ON nipun_curriculum.rowid = nipun_curriculum_fts.rowid
        WHERE nipun_curriculum_fts MATCH :query
        LIMIT :limit
    """)
    fun searchFullText(query: String, limit: Int = 20): List<NipunCurriculumEntity>

    @Query("SELECT * FROM nipun_curriculum WHERE competencyCode = :code LIMIT 1")
    suspend fun getByCompetencyCode(code: String): NipunCurriculumEntity?

    @Query("SELECT * FROM nipun_curriculum WHERE gradeLevel = :gradeLevel AND isActive = 1 ORDER BY competencyCode ASC")
    fun getByGradeLevel(gradeLevel: Int): Flow<List<NipunCurriculumEntity>>

    @Query("SELECT * FROM nipun_curriculum WHERE subject = :subject AND isActive = 1")
    fun getBySubject(subject: String): Flow<List<NipunCurriculumEntity>>

    @Query("SELECT * FROM nipun_curriculum WHERE contentType = :contentType AND isActive = 1 ORDER BY competencyCode ASC")
    fun getByContentType(contentType: String): Flow<List<NipunCurriculumEntity>>

    @Query("SELECT * FROM nipun_curriculum WHERE gradeLevel = :gradeLevel AND contentType = :contentType AND isActive = 1 ORDER BY competencyCode ASC")
    fun getByGradeLevelAndContentType(gradeLevel: Int, contentType: String): Flow<List<NipunCurriculumEntity>>

    @Query("SELECT COUNT(*) FROM nipun_curriculum WHERE contentType = :contentType AND isActive = 1")
    fun getCountByContentType(contentType: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM nipun_curriculum WHERE isActive = 1")
    fun getActiveCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: NipunCurriculumEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<NipunCurriculumEntity>)
}

@Dao
interface NipunCompetencyDao {
    @Query("SELECT * FROM nipun_competencies WHERE subject = :subject AND gradeLevel = :gradeLevel")
    fun getBySubjectAndGrade(subject: String, gradeLevel: Int): Flow<List<NipunCompetencyEntity>>

    @Query("SELECT * FROM nipun_competencies WHERE code = :code LIMIT 1")
    suspend fun getByCode(code: String): NipunCompetencyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompetencies(competencies: List<NipunCompetencyEntity>)

    @Query("SELECT COUNT(*) FROM nipun_competencies")
    fun getCount(): Flow<Int>
}
