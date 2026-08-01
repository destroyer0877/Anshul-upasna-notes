package com.example.data

import kotlinx.coroutines.flow.Flow

class NoteRepository(private val noteDao: NoteDao) {
    val allNotes: Flow<List<NoteEntity>> = noteDao.getAllNotes()
    val favoriteNotes: Flow<List<NoteEntity>> = noteDao.getFavoriteNotes()
    val deletedNotes: Flow<List<NoteEntity>> = noteDao.getDeletedNotes()
    val allFolders: Flow<List<FolderEntity>> = noteDao.getAllFolders()

    suspend fun getNoteById(id: Int): NoteEntity? = noteDao.getNoteById(id)

    fun getNotesInFolder(folderId: Int): Flow<List<NoteEntity>> = noteDao.getNotesInFolder(folderId)

    fun getNotesByType(type: String): Flow<List<NoteEntity>> = noteDao.getNotesByType(type)

    suspend fun saveNote(note: NoteEntity): Long {
        return if (note.id == 0) {
            noteDao.insertNote(note)
        } else {
            noteDao.updateNote(note)
            note.id.toLong()
        }
    }

    suspend fun updateNote(note: NoteEntity) = noteDao.updateNote(note)

    suspend fun deleteNote(note: NoteEntity) = noteDao.deleteNote(note)

    suspend fun moveToTrash(id: Int) = noteDao.moveToTrash(id)

    suspend fun restoreFromTrash(id: Int) = noteDao.restoreFromTrash(id)

    suspend fun emptyTrash() = noteDao.emptyTrash()

    // --- Folders ---
    suspend fun saveFolder(folder: FolderEntity): Long = noteDao.insertFolder(folder)

    suspend fun deleteFolder(folderId: Int) = noteDao.deleteFolder(folderId)
}
