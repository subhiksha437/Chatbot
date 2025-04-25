package com.example.app7



import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.*

class VideoStorage(private val context: Context) {
    private val videoDir: File
    private val sharedPreferences: SharedPreferences

    init {
        // Create a directory for storing videos
        videoDir = File(context.filesDir, "videos")
        if (!videoDir.exists()) {
            videoDir.mkdirs()
        }

        // Initialize SharedPreferences for storing metadata
        sharedPreferences = context.getSharedPreferences("video_metadata", Context.MODE_PRIVATE)
    }

    suspend fun saveVideo(uri: Uri): String {
        val videoId = UUID.randomUUID().toString()
        val videoFile = File(videoDir, "$videoId.mp4")

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(videoFile).use { output ->
                    input.copyTo(output)
                }
            }
            return videoId
        } catch (e: Exception) {
            Log.e("VideoStorage", "Error saving video", e)
            throw e
        }
    }

    fun savePoint(videoId: String, x: Int, y: Int, text: String = "") {
        sharedPreferences.edit().apply {
            putInt("${videoId}_x", x)
            putInt("${videoId}_y", y)
            putString("${videoId}_text", text)
            apply()
        }
    }

    fun getVideoMetadata(videoId: String): Triple<Int, Int, String>? {
        val x = sharedPreferences.getInt("${videoId}_x", -1)
        val y = sharedPreferences.getInt("${videoId}_y", -1)
        val text = sharedPreferences.getString("${videoId}_text", "") ?: ""

        return if (x != -1 && y != -1) {
            Triple(x, y, text)
        } else {
            null
        }
    }

    fun getVideoFile(videoId: String): File? {
        val file = File(videoDir, "$videoId.mp4")
        return if (file.exists()) file else null
    }

    fun getAllVideos(): List<String> {
        return videoDir.listFiles()
            ?.filter { it.extension == "mp4" }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()
    }

    fun deleteVideo(videoId: String) {
        // Delete the video file
        val file = File(videoDir, "$videoId.mp4")
        if (file.exists()) {
            file.delete()
        }

        // Delete metadata
        sharedPreferences.edit().apply {
            remove("${videoId}_x")
            remove("${videoId}_y")
            remove("${videoId}_text")
            apply()
        }
    }
}