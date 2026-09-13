package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.RecordingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY timestamp DESC")
    fun getAllRecordings(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE isStarred = 1 ORDER BY timestamp DESC")
    fun getStarredRecordings(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE sourceApp = :source ORDER BY timestamp DESC")
    fun getRecordingsBySource(source: String): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE id = :id LIMIT 1")
    suspend fun getRecordingById(id: Long): RecordingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(recording: RecordingEntity): Long

    @Update
    suspend fun updateRecording(recording: RecordingEntity)

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteRecordingById(id: Long)

    @Query("UPDATE recordings SET isStarred = :isStarred WHERE id = :id")
    suspend fun updateStarred(id: Long, isStarred: Boolean)

    @Query("UPDATE recordings SET title = :newTitle WHERE id = :id")
    suspend fun updateTitle(id: Long, newTitle: String)

    @Query("UPDATE recordings SET note = :note WHERE id = :id")
    suspend fun updateNote(id: Long, note: String)

    @Query("SELECT COUNT(*) FROM recordings")
    fun getRecordingsCount(): Flow<Int>

    @Query("SELECT SUM(fileSizeBytes) FROM recordings")
    fun getTotalStorageUsed(): Flow<Long?>
}
