package com.example.app7

//import VideoStorage
import android.app.Dialog
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.Toast
import android.widget.VideoView
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.MainScope
class CardDetailsDialog(context: Context) : Dialog(context), CoroutineScope by MainScope() {

    private lateinit var videoUri: Uri
    private lateinit var intInput1: TextInputEditText
    private lateinit var intInput2: TextInputEditText
    lateinit var textValue: TextInputEditText
    private lateinit var videoView: VideoView
    private lateinit var btnUploadVideo: Button
    private lateinit var btnSubmit: Button
    private var onSubmitListener: ((Int, Int, Uri) -> Unit)? = null
    private var onUploadVideoListener: (() -> Unit)? = null

    private lateinit var videoStorage: VideoStorage


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.card_item)

        videoStorage = VideoStorage(context)

        intInput1 = findViewById(R.id.intValue1)
        intInput2 = findViewById(R.id.intValue2)
        textValue = findViewById(R.id.textInput)
        videoView = findViewById(R.id.videoView)
        btnUploadVideo = findViewById(R.id.btnUploadVideo)
        btnSubmit = findViewById(R.id.btnSubmit)

        btnUploadVideo.setOnClickListener {
            // Delegate video picking logic to the parent Activity or Fragment
            onUploadVideoListener?.invoke()
        }

        btnSubmit.setOnClickListener {
            val int1 = intInput1.text.toString().toIntOrNull()
            val int2 = intInput2.text.toString().toIntOrNull()
            val text = textValue.text.toString()?.ifBlank { null }

            if (int1 != null && int2 != null && text != null && ::videoUri.isInitialized) {
                //onSubmitListener?.invoke(int1, int2, videoUri)
                //dismiss()
                launch {
                    val videoId = videoStorage.saveVideo(videoUri)
                    videoStorage.savePoint(videoId, int1, int2, text)
                    onSubmitListener?.invoke(int1, int2, videoUri)
                    dismiss()
                }
            } else {
                Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
            }

        }

    }

    fun onVideoTap(videoId: String, x: Int, y: Int) {
        videoStorage.savePoint(videoId, x, y)

        val videoFile = videoStorage.getVideoFile(videoId)
        if (videoFile != null) {
            val videoUri = Uri.fromFile(videoFile)
            videoView.setVideoURI(videoUri)
            videoView.start()
        } else {
            Log.e("VideoStorage", "Video file not found")
        }
    }

    fun setOnSubmitListener(listener: (Int, Int, Uri) -> Unit) {
        onSubmitListener = listener
    }

    fun setOnUploadVideoListener(listener: () -> Unit) {
        onUploadVideoListener = listener
    }

    fun setVideoUri(uri: Uri) {
        videoUri = uri
        videoView.setVideoURI(uri)
        videoView.start()
    }
}