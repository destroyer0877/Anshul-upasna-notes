package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val colorHex: String,
    val iconType: String // "API", "CODE", "VIDEO", "NORMAL", "CUSTOM"
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val folderId: Int = -1, // -1 means custom/none or generic
    val title: String,
    val content: String,
    val isFavorite: Boolean = false,
    val isLocked: Boolean = false,
    val isDeleted: Boolean = false,
    val type: String = "NORMAL", // "API", "CODE", "VIDEO", "NORMAL"
    val themeBgHex: String = "#1C1C1E", // Default glassy dark
    val themeType: String = "GLASS_DARK", // "GLASS_DARK", "NEON_BLUE", "SUNSET", "MINT_GLASS", "CHERRY"
    val imagePath: String? = null,
    val filePath: String? = null,
    val reminderTime: Long? = null,
    val reminderTone: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE isDeleted = 0 ORDER BY updatedAt DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: Int): NoteEntity?

    @Query("SELECT * FROM notes WHERE isDeleted = 0 AND isFavorite = 1 ORDER BY updatedAt DESC")
    fun getFavoriteNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isDeleted = 1 ORDER BY updatedAt DESC")
    fun getDeletedNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isDeleted = 0 AND folderId = :folderId ORDER BY updatedAt DESC")
    fun getNotesInFolder(folderId: Int): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isDeleted = 0 AND type = :type ORDER BY updatedAt DESC")
    fun getNotesByType(type: String): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity): Long

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Delete
    suspend fun deleteNote(note: NoteEntity)

    @Query("UPDATE notes SET isDeleted = 1 WHERE id = :id")
    suspend fun moveToTrash(id: Int)

    @Query("UPDATE notes SET isDeleted = 0 WHERE id = :id")
    suspend fun restoreFromTrash(id: Int)

    @Query("DELETE FROM notes WHERE isDeleted = 1")
    suspend fun emptyTrash()

    // --- Folders ---
    @Query("SELECT * FROM folders ORDER BY id ASC")
    fun getAllFolders(): Flow<List<FolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity): Long

    @Query("DELETE FROM folders WHERE id = :folderId")
    suspend fun deleteFolder(folderId: Int)
}

@Database(entities = [NoteEntity::class, FolderEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
}
