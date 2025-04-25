package com.example.app7


import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.util.*

class VideoPlayerActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var videoView: VideoView
    private lateinit var btnClose: MaterialButton
    private lateinit var textToSpeech: TextToSpeech
    private var textToSpeak: String? = null
    //private lateinit var btnDelete: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)

        videoView = findViewById(R.id.fullScreenVideoView)
        btnClose = findViewById(R.id.btnClose)


        // Initialize Text-to-Speech
        textToSpeech = TextToSpeech(this, this)

        // Get video URI and text
        val videoUri = intent.getStringExtra("VIDEO_URI")
        textToSpeak = intent.getStringExtra("TEXT_TO_SPEAK")

        if (!videoUri.isNullOrEmpty()) {
            videoView.setVideoURI(Uri.parse(videoUri))
            videoView.setOnPreparedListener { it.start() }
        } else {
            Log.e("VideoPlayerActivity", "Invalid video URI")
            finish()
        }

        btnClose.setOnClickListener {
            textToSpeech.stop()
            textToSpeech.shutdown()
            finish()
        }

    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech.language = Locale.US
//            textToSpeak?.let {
//                textToSpeech.speak(it, TextToSpeech.QUEUE_FLUSH, null, null)
//            }
        } else {
            Log.e("TextToSpeech", "Initialization failed")
        }
    }

    override fun onDestroy() {
        textToSpeech.stop()
        textToSpeech.shutdown()
        super.onDestroy()
    }
}