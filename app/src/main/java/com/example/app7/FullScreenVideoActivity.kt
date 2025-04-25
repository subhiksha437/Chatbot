package com.example.app7

import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.MediaController
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity

class FullScreenVideoActivity : AppCompatActivity() {
    private lateinit var videoView: VideoView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_screen_video)

        videoView = findViewById(R.id.fullScreenVideoView)

        // Get the video URI from the intent
        val videoUri = intent.getParcelableExtra<Uri>("videoUri")
        if (videoUri != null) {
            videoView.setVideoURI(videoUri)
            videoView.setOnPreparedListener { mediaPlayer ->
                mediaPlayer.start()
            }
            videoView.setOnCompletionListener {
                finish() // Close the activity when the video finishes
            }
        } else {
            finish() // Close the activity if no video URI is provided
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        videoView.stopPlayback() // Stop video playback when the activity is destroyed
    }
}