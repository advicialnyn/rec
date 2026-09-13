package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val sourceApp: String, // "WhatsApp", "IMO", "Messenger", "Phone", "Manual", "Telegram"
    val callerNumberOrName: String,
    val filePath: String,
    val fileName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val durationMs: Long = 0,
    val fileSizeBytes: Long = 0,
    val isAutoRecorded: Boolean = false,
    val isStarred: Boolean = false,
    val note: String = ""
)
