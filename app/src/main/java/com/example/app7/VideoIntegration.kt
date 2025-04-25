package com.example.app7

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.Manifest
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.VideoView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI
import java.net.URISyntaxException
import java.util.UUID
//
class VideoIntegration : AppCompatActivity() {
    private lateinit var webSocketClient: WebSocketClient
    private lateinit var videoView: VideoView
    private lateinit var recyclerView: RecyclerView
    private lateinit var btnAddCard: MaterialButton
    private var cardAdapter: CardAdapter? = null

    private lateinit var videoStorage: VideoStorage
    private lateinit var textToSpeech: TextToSpeech
    private val TAG = "VideoIntegration"
    private val MEDIA_PERMISSION_REQUEST_CODE = 1001

//    companion object {
//        val cardList = mutableListOf<CardData>()  // Now accessible globally
//    }
private val cardList = mutableListOf<CardData>()

    private val videoPickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                cardDetailsDialog?.setVideoUri(uri)
            }
        }

    private var cardDetailsDialog: CardDetailsDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.video_integ)

        initializeViews()
        initializeTextToSpeech()
        setupRecyclerView()
        loadSavedVideos()
        connectWebSocket()
    }

    private fun initializeViews() {
        videoView = findViewById(R.id.videoView)
        recyclerView = findViewById(R.id.cardsRecyclerView)
        btnAddCard = findViewById(R.id.btnAddCard)
        videoStorage = VideoStorage(applicationContext)

        videoView.visibility = View.GONE // Initially hide VideoView

        btnAddCard.setOnClickListener {
            openCardDetailsDialog()
        }
    }


    private fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(this) { status ->
            if (status != TextToSpeech.SUCCESS) {
                Log.e(TAG, "TextToSpeech initialization failed")
            }
        }
    }

    private fun connectWebSocket() {
        try {
            val serverUri = URI("ws://172.16.66.142:9090")
            webSocketClient = object : WebSocketClient(serverUri) {
                override fun onOpen(handshake: ServerHandshake?) {
                    Log.d(TAG, "WebSocket Connection opened")
                    subscribeToTopics()
                }

                override fun onMessage(message: String?) {
                    message?.let {
                        lifecycleScope.launch(Dispatchers.IO) {
                            handleMessage(it)
                        }
                    }
                }

                override fun onClose(code: Int, reason: String?, remote: Boolean) {
                    Log.d(TAG, "WebSocket Connection closed: $reason")
                }

                override fun onError(ex: Exception?) {
                    Log.e(TAG, "WebSocket Error: ${ex?.message}")
                }
            }
            webSocketClient.connect()
        } catch (e: URISyntaxException) {
            Log.e(TAG, "URI Syntax Error: ${e.message}")
        }
    }

    private fun subscribeToTopics() {
        if (webSocketClient.isOpen) {
            val subscribeMsg = JSONObject().apply {
                put("op", "subscribe")
                put("topic", "/goal_pose")
                put("type", "geometry_msgs/PoseStamped")
            }
            webSocketClient.send(subscribeMsg.toString())
        }
    }

    private suspend fun handleMessage(message: String) {
        try {
            val jsonMessage = JSONObject(message)
            val msg = JSONObject(jsonMessage.getString("msg"))
            val pose = msg.getJSONObject("pose")
            val position = pose.getJSONObject("position")

            val x = position.getDouble("x").toInt()
            val y = position.getDouble("y").toInt()

            // Find matching video for the position
            //findAndPlayMatchingVideo(x, y)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling message: ${e.message}")
        }
    }

//    private fun findAndPlayMatchingVideo(botX: Int, botY: Int) {
//        for (card in cardList) {
//            if (isPositionInRange(botX, botY, card.intValue1, card.intValue2)) {
//                runOnUiThread {
//                    stopCurrentPlayback()
//                    playVideo(card)
//                }
//                break
//            }
//        }
//    }
//
//    private fun isPositionInRange(botX: Int, botY: Int, targetX: Int, targetY: Int): Boolean {
//        return (botX >= targetX - 2 && botX <= targetX + 2) &&
//                (botY >= targetY - 2 && botY <= targetY + 2)
//    }
//
//    private fun stopCurrentPlayback() {
//        if (videoView.isPlaying) {
//            videoView.stopPlayback()
//        }
//        videoView.visibility = View.GONE // Hide VideoView when playback stops
//        textToSpeech.stop()
//    }
//
//
//    private fun playVideo(card: CardData) {
//        val intent = Intent(this, FullScreenVideoActivity::class.java).apply {
//            putExtra("videoUri", card.videoUri)
//        }
//        startActivity(intent)
//
//        // Speak the associated text
//        card.textValue.let { text ->
//            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
//        }
//    }



    private fun hasReadMediaPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        } else {
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestReadMediaPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.READ_MEDIA_VIDEO), MEDIA_PERMISSION_REQUEST_CODE)
        } else {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), MEDIA_PERMISSION_REQUEST_CODE)
        }
    }

    private fun setupRecyclerView() {
        val spacingInPixels = 16 // Change this value as needed
        recyclerView.addItemDecoration(SpacesItemDecoration(spacingInPixels))
        recyclerView.layoutManager = LinearLayoutManager(this)
        cardAdapter = CardAdapter(cardList)
        recyclerView.adapter = cardAdapter
    }

    // Also add the loadSavedVideos function that was referenced:
    private fun loadSavedVideos() {
        videoStorage.getAllVideos().forEach { videoId ->
            val metadata = videoStorage.getVideoMetadata(videoId)
            val videoFile = videoStorage.getVideoFile(videoId)

            if (metadata != null && videoFile != null) {
                val (x, y, text) = metadata
                val uri = Uri.fromFile(videoFile)
                cardList.add(CardData(x, y, text, uri, videoId))
            }
        }
        cardAdapter?.notifyDataSetChanged()
    }

    // Your existing methods for dialog handling
    private fun openCardDetailsDialog() {
        cardDetailsDialog = CardDetailsDialog(this)
        cardDetailsDialog?.apply {
            setOnUploadVideoListener {
                videoPickerLauncher.launch("video/*")
            }
            setOnSubmitListener { int1, int2, videoUri ->
                val text = textValue.text?.toString().orEmpty()
                val videoId = UUID.randomUUID().toString()
                cardList.add(CardData(int1, int2, text, videoUri, videoId))
                cardAdapter?.notifyItemInserted(cardList.size - 1)
            }
        }
        cardDetailsDialog?.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (this::webSocketClient.isInitialized && webSocketClient.isOpen) {
            webSocketClient.close()
        }
        if (this::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
    }
}


