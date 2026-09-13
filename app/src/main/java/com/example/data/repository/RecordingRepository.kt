package com.example.data.repository

import android.content.Context
import com.example.data.db.RecordingDao
import com.example.data.model.RecordingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class RecordingRepository(
    private val recordingDao: RecordingDao,
    private val context: Context
) {
    val allRecordings: Flow<List<RecordingEntity>> = recordingDao.getAllRecordings()
    val starredRecordings: Flow<List<RecordingEntity>> = recordingDao.getStarredRecordings()
    val recordingsCount: Flow<Int> = recordingDao.getRecordingsCount()
    val totalStorageUsed: Flow<Long?> = recordingDao.getTotalStorageUsed()

    fun getRecordingsBySource(source: String): Flow<List<RecordingEntity>> {
        return recordingDao.getRecordingsBySource(source)
    }

    suspend fun insertRecording(recording: RecordingEntity): Long = withContext(Dispatchers.IO) {
        recordingDao.insertRecording(recording)
    }

    suspend fun deleteRecording(id: Long) = withContext(Dispatchers.IO) {
        val recording = recordingDao.getRecordingById(id)
        if (recording != null) {
            try {
                val file = File(recording.filePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            recordingDao.deleteRecordingById(id)
        }
    }

    suspend fun toggleStarred(id: Long, currentStatus: Boolean) = withContext(Dispatchers.IO) {
        recordingDao.updateStarred(id, !currentStatus)
    }

    suspend fun renameRecording(id: Long, newTitle: String) = withContext(Dispatchers.IO) {
        recordingDao.updateTitle(id, newTitle)
    }

    suspend fun updateNote(id: Long, note: String) = withContext(Dispatchers.IO) {
        recordingDao.updateNote(id, note)
    }

    suspend fun getRecordingById(id: Long): RecordingEntity? = withContext(Dispatchers.IO) {
        recordingDao.getRecordingById(id)
    }
}
