package com.example.app7

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo

import android.net.Uri
import android.os.Bundle
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.app7.databinding.ActivityMainBinding
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerateContentResponse
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.*
import org.java_websocket.client.WebSocketClient
import org.json.JSONObject
import android.widget.Toast
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

import java.util.*

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var videoStorage: VideoStorage
    private val cardList = mutableListOf<CardData>()

    private lateinit var binding: ActivityMainBinding
    private lateinit var responseTextView: TextView
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var generativeModel: GenerativeModel
    private var isProcessingResponse = false
    private lateinit var callButton: Button
    private lateinit var startListeningButton: Button
    private var isButtonTriggeredListening = false
    private var currentLanguage = "en-US"
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var idleVideoUri: Uri
    private lateinit var speakVideoUri: Uri
    private var isSpeaking = false
    private lateinit var listViewButton: Button // Add this line
    private var listViewDialog: AlertDialog? = null
    private lateinit var webSocket: WebSocketClient
//    private val CAMERA_PERMISSION_REQUEST_CODE = 100
//
//    private var cameraService: BackgroundCameraService? = null
//    private var serviceBound = false
    private val serverUrl = "ws://172.16.66.142:9090"


    private var questionCount = 0
    private lateinit var sharedPreferences: SharedPreferences
    private var isSurveyActive = false
    private var currentSurveyQuestion = 0

//    private val serviceConnection = object : ServiceConnection {
//        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
//            val binder = service as BackgroundCameraService.LocalBinder
//            cameraService = binder.getService()
//            cameraService?.startCamera()
//            // Connect to your ROS bridge
//            cameraService?.connectToROS("your_ros_ip", 9090)
//        }
//
//        override fun onServiceDisconnected(name: ComponentName?) {
//            cameraService = null
//        }
//    }


    // Survey questions and choices
    private val surveyQuestions = listOf(
        SurveyQuestion(
            "How satisfied are you with the responses?",
            listOf("Very Satisfied", "Satisfied", "Neutral", "Dissatisfied", "Very Dissatisfied")
        ),
        SurveyQuestion(
            "How would you rate the voice clarity?",
            listOf("Excellent", "Good", "Average", "Poor", "Very Poor")
        ),
        SurveyQuestion(
            "Would you recommend this chatbot to others?",
            listOf("Definitely", "Probably", "Maybe", "Probably Not", "Definitely Not")
        )
    )


    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSION_REQUEST_CODE = 1
    }

    // Custom queries and responses
    private val customQueries = mapOf(
        // Institution related queries
        "tibet" to "Hey there! I'm tBot, your friendly assistant from Adapt Robotics. How can I help you today?",
        "who are you" to "I'm tBot! I was created by the team at Adapt Robotics to chat with awesome people like you. What can I help you with?",
        "sairam" to "Sri Sairam Engineering College is a premier educational institution located in Chennai, Tamil Nadu. It offers various engineering courses and is known for its excellent placement record.",
        "engineering college" to "Sri Sairam Engineering College was established in 1995 by MK Rajagopalan. The college offers 12 UG programs and 7 PG programs in engineering.",
        "incubation" to "Sri Sairam Techno Incubator Center is a non-profit firm that has incubated around 100 startups including Adapt Robotics.",
        "incubator" to "Sri Sairam Techno Incubator Center provides mentoring, funding, and resources to startups. It has successfully supported over 100 innovative projects.",
        "room" to "From the entrance, take left go straight and turn first left and you can reach to Sam sir's desk!! ",
        // Company related queries
        "adapt robotics" to "Adapt Robotics is an innovative startup that specializes in creating advanced robotics solutions, including myself - tBot.",
        "company" to "Adapt Robotics is a startup founded at Sri Sairam Techno Incubator. We focus on developing innovative robotics solutions.",

        // Facility related queries
        "lunch timing" to "Lunch time is from 1 PM to 2 PM.",
        "canteen" to "The canteen is located on the ground floor. It serves breakfast, lunch, and evening snacks.",
        "library" to "The library is located on the first floor. It's open from 8:30 AM to 4:30 PM.",

        // Contact related queries
        "principal" to "Dr. J. Raja is the Principal of Sri Sairam Engineering College. His office is located on the first floor of the main building.",
        "contact" to "You can reach the college at 044-2251 2222 or email at sairam@sairam.edu.in",

        // Add more custom queries and responses here
        "placement" to "Sri Sairam Engineering College has excellent placement records with top companies visiting regularly.",
        "departments" to "The college offers various engineering branches including CSE, IT, ECE, EEE, Mechanical, and more.",
        "location" to "Sri Sairam Engineering College is located in West Tambaram, Chennai, Tamil Nadu."
    )
    private val tamilCustomQueries = mapOf(
        "சாய்ராம்" to "சாய்ராம் பொறியியல் கல்லூரி சென்னையில் அமைந்துள்ள ஒரு முன்னணி கல்வி நிறுவனம்.",
        "உணவு நேரம்" to "மதிய உணவு நேரம் மதியம் 1 மணி முதல் 2 மணி வரை.",
        "நூலகம்" to "நூலகம் முதல் தளத்தில் உள்ளது. காலை 8:30 முதல் மாலை 4:30 வரை திறந்திருக்கும்."
    )


    // List of keywords that trigger the bot
    private val triggerKeywords = listOf(
        "tbot", "tea bot", "tea boat", "tibet"
    )

    data class SurveyQuestion(
        val question: String,
        val choices: List<String>
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)

            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)

            window.decorView.setBackgroundColor(android.graphics.Color.argb(0, 0, 0, 0))

            responseTextView = findViewById(R.id.responseTextView)
            responseTextView.apply {
                setTextColor(ContextCompat.getColor(context, android.R.color.white))
                textSize = 16f
            }

            sharedPreferences = getSharedPreferences("SurveyResponses", Context.MODE_PRIVATE)

            listViewButton = findViewById(R.id.listViewButton)
            listViewButton.setOnClickListener {
                showActivityListDialog()
            }
            videoStorage = VideoStorage(applicationContext)
            loadSavedVideos()

            startListeningButton = findViewById(R.id.startListeningButton)
            startListeningButton.setOnClickListener {
                if (!isButtonTriggeredListening) {
                    startButtonTriggeredListening()
                }
            }
            connectWebSocket()


            initializeComponents()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}", e)
            Toast.makeText(this, "Error", Toast.LENGTH_LONG).show()
        }
    }

    private fun initializeComponents() {
        setupPermissions()
        setupLanguageSpinner()
        setupVideoUris()
        setupVideoView()
        setupSpeechRecognizer()
        setupTextToSpeech()
        setupGeminiAI()
    }
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
    }

    // Update WebSocket message handling to include position matching
    private fun handlePoseMessage(jsonObject: JSONObject) {
        val data = jsonObject.optJSONObject("msg")
        val pose = data?.optJSONObject("pose")

        val position = pose?.optJSONObject("position")
        val x = position?.optDouble("x")?.toInt() ?: 0
        val y = position?.optDouble("y")?.toInt() ?: 0

        // Find and play matching video
        findAndPlayMatchingVideo(x, y)
    }

    // Add this method to find and play matching video
    private fun findAndPlayMatchingVideo(botX: Int, botY: Int) {
        for (card in cardList) {
            if (isPositionInRange(botX, botY, card.intValue1, card.intValue2)) {
                playVideo(card)
                break
            }
        }
    }

    // Add this method to check if the position is within range
    private fun isPositionInRange(botX: Int, botY: Int, targetX: Int, targetY: Int): Boolean {
        return (botX >= targetX - 2 && botX <= targetX + 2) &&
                (botY >= targetY - 2 && botY <= targetY + 2)
    }

    // Add this method to play the video
    private fun playVideo(card: CardData) {
        val intent = Intent(this, FullScreenVideoActivity::class.java).apply {
            putExtra("videoUri", card.videoUri)
        }
        startActivity(intent)

        // Speak the associated text
        card.textValue.let { text ->
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

//    private fun startCameraService() {
//        val serviceIntent = Intent(this, BackgroundCameraService::class.java)
//        startForegroundService(serviceIntent)
//        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
//    }

    private fun connectWebSocket() {
        webSocket = object : WebSocketClient(URI(serverUrl)) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                println("WebSocket connected")
                subscribeToTopics()
            }

            override fun onMessage(message: String?) {
                println("Received message: $message")
                message?.let {
                    runOnUiThread {
                        try {
                            val jsonObject = JSONObject(it)
                            when (jsonObject.optString("topic")) {
                                "/goal_pose" -> handlePoseMessage(jsonObject)
                                "/cmd_vel" -> handleVelocityMessage(jsonObject)
                                "/message" -> handleTextMessage(jsonObject)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing WebSocket message: ${e.message}")
                        }
                    }
                }
            }

            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                println("WebSocket closed: $reason")
            }

            override fun onError(ex: Exception?) {
                println("WebSocket error: ${ex?.message}")
            }
        }
        webSocket.connect()
    }

    private fun subscribeToTopics() {
        val topics = listOf(
            Pair("/goal_pose", "geometry_msgs/msg/PoseStamped"),
            Pair("/cmd_vel", "geometry_msgs/msg/Twist"),
            Pair("/message", "std_msgs/msg/String")
        )

        topics.forEach { (topic, type) ->
            val subscribeMsg = JSONObject().apply {
                put("op", "subscribe")
                put("topic", topic)
                put("type", type)
            }
            webSocket.send(subscribeMsg.toString())
        }
    }

    private fun sendCommand(linearX: Double, angularZ: Double) {
        val message = JSONObject().apply {
            put("op", "publish")
            put("topic", "/cmd_vel")
            put("msg", JSONObject().apply {
                put("linear", JSONObject().apply {
                    put("x", linearX)
                    put("y", 0)
                    put("z", 0)
                })
                put("angular", JSONObject().apply {
                    put("x", 0)
                    put("y", 0)
                    put("z", angularZ)
                })
            })
        }
        webSocket.send(message.toString())
    }



    private fun handleVelocityMessage(jsonObject: JSONObject) {
        val data = jsonObject.optJSONObject("msg")
        val linearVel = data?.optJSONObject("linear")
        val angularVel = data?.optJSONObject("angular")

        // Update UI with velocity data if needed
    }

    private fun handleTextMessage(jsonObject: JSONObject) {
        val msg = JSONObject(jsonObject.getString("msg"))
        val data = msg.getString("data")
        // Update UI with text message if needed
    }

    private fun showActivityListDialog() {
        // Create list of activities with their names and corresponding intents
        val activities = listOf(
            ActivityItem("Video Call", VideoCallActivity::class.java),
            ActivityItem("Bot Move", BotMove::class.java),
            ActivityItem("VideoIntegration", VideoIntegration::class.java),


            )

        // Create and configure the dialog
        val builder = AlertDialog.Builder(this, R.style.CustomAlertDialogStyle)
        builder.setTitle("Select Activity")

        // Create custom adapter for the list
        val adapter = object : ArrayAdapter<ActivityItem>(
            this,
            android.R.layout.simple_list_item_1,
            activities
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val textView = view.findViewById<TextView>(android.R.id.text1)
                textView.setTextColor(Color.BLACK)
                textView.textSize = 18f
                textView.setPadding(32, 32, 32, 32)
                return view
            }
        }

        builder.setAdapter(adapter) { _, position ->
            // Launch the selected activity
            val intent = Intent(this, activities[position].activityClass)
            startActivity(intent)
            listViewDialog?.dismiss()
        }

        // Add a cancel button
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }

        // Show the dialog and store the reference
        listViewDialog = builder.create().apply {
            window?.setBackgroundDrawableResource(R.drawable.dialog_background)
            show()
        }
    }

    // Data class to hold activity information
    data class ActivityItem(
        val name: String,
        val activityClass: Class<*>
    ) {
        override fun toString(): String = name
    }

//    private fun startButtonTriggeredListening() {
//        if (isButtonTriggeredListening) return
//
//        isButtonTriggeredListening = true
//        startListeningButton.text = "Listening..."
//        responseTextView.text = ""
//
//        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
//            putExtra(
//                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
//                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
//            )
//            putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguage)
//            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
//            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
//        }
//
//        try {
//            speechRecognizer.setRecognitionListener(object : RecognitionListener {
//                override fun onResults(results: Bundle?) {
//                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
//                    val spokenText = matches?.get(0)
//                    if (spokenText != null) {
//                        processInput(spokenText)
//                    }
//                    resetListeningButton()
//                }
//
//                override fun onError(error: Int) {
//                    Log.e(TAG, "Speech recognition error: $error")
//                    resetListeningButton()
//                }
//
//                // Required overrides
//                override fun onReadyForSpeech(params: Bundle?) {}
//                override fun onBeginningOfSpeech() {}
//                override fun onRmsChanged(rmsdB: Float) {}
//                override fun onBufferReceived(buffer: ByteArray?) {}
//                override fun onEndOfSpeech() {}
//                override fun onPartialResults(partialResults: Bundle?) {}
//                override fun onEvent(eventType: Int, params: Bundle?) {}
//            })
//
//            speechRecognizer.startListening(intent)
//        } catch (e: Exception) {
//            Log.e(TAG, "Error in startButtonTriggeredListening: ${e.message}", e)
//            resetListeningButton()
//        }
//    }
//
//    private fun resetListeningButton() {
//        runOnUiThread {
//            isButtonTriggeredListening = false
//            startListeningButton.text = "Start Speaking"
//        }
//    }

//    private fun startButtonTriggeredListening() {
//        if (isButtonTriggeredListening) return
//
//        isButtonTriggeredListening = true
//        startListeningButton.text = "Listening..."
//        responseTextView.text = ""
//
//        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
//            putExtra(
//                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
//                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
//            )
//            putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguage)
//            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
//            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
//        }
//
//        try {
//            speechRecognizer.setRecognitionListener(object : RecognitionListener {
//                override fun onResults(results: Bundle?) {
//                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
//                    val spokenText = matches?.get(0)
//                    if (spokenText != null) {
//                        // Check if the spoken text contains movement commands
//                        val command = spokenText.toLowerCase()
//                        if ("forward" in command || "ahead" in command || "front" in command) {
//                            sendCommand(0.5, 0.0)
//                            responseTextView.text = "Moving forward"
//                        }
//                        if ("back" in command || "backward" in command || "behind" in command) {
//                            sendCommand(-0.5, 0.0)
//                            responseTextView.text = "Moving backward"
//                        }
//                        if ("left" in command) {
//                            sendCommand(0.0, 1.0)
//                            responseTextView.text = "Turning left"
//                        }
//                        if ("right" in command) {
//                            sendCommand(0.0, -1.0)
//                            responseTextView.text = "Turning right"
//                        } else {
//                            // If no movement command is detected, process as normal chat input
//                            processInput(spokenText)
//                        }
//                    }
//                    resetListeningButton()
//                }
//
//                override fun onError(error: Int) {
//                    Log.e(TAG, "Speech recognition error: $error")
//                    resetListeningButton()
//                    Toast.makeText(
//                        this@MainActivity,
//                        "Speech recognition error: $error",
//                        Toast.LENGTH_SHORT
//                    ).show()
//                }
//
//                // Required overrides
//                override fun onReadyForSpeech(params: Bundle?) {}
//                override fun onBeginningOfSpeech() {}
//                override fun onRmsChanged(rmsdB: Float) {}
//                override fun onBufferReceived(buffer: ByteArray?) {}
//                override fun onEndOfSpeech() {}
//                override fun onPartialResults(partialResults: Bundle?) {}
//                override fun onEvent(eventType: Int, params: Bundle?) {}
//            })
//
//            speechRecognizer.startListening(intent)
//        } catch (e: Exception) {
//            Log.e(TAG, "Error in startButtonTriggeredListening: ${e.message}", e)
//            resetListeningButton()
//            Toast.makeText(
//                this,
//                "Error starting speech recognition: ${e.message}",
//                Toast.LENGTH_SHORT
//            ).show()
//        }
//    }
private fun startButtonTriggeredListening() {
    if (isButtonTriggeredListening) return

    // If the bot is currently speaking, stop it
    if (isSpeaking && textToSpeech.isSpeaking) {
        textToSpeech.stop()
        isSpeaking = false
        switchToIdleVideo()
    }

    isButtonTriggeredListening = true
    startListeningButton.text = "Listening..."
    responseTextView.text = ""

    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguage)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    try {
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spokenText = matches?.get(0)
                if (spokenText != null) {
                    // Check if the spoken text contains movement commands
                    val command = spokenText.toLowerCase()
                    if ("forward" in command || "ahead" in command || "front" in command) {
                        sendCommand(0.5, 0.0)
                        responseTextView.text = "Moving forward"
                    }
                    else if ("back" in command || "backward" in command || "behind" in command) {
                        sendCommand(-0.5, 0.0)
                        responseTextView.text = "Moving backward"
                    }
                    else if ("left" in command) {
                        sendCommand(0.0, 1.0)
                        responseTextView.text = "Turning left"
                    }
                    else if ("right" in command) {
                        sendCommand(0.0, -1.0)
                        responseTextView.text = "Turning right"
                    } else {
                        // If no movement command is detected, process as normal chat input
                        processInput(spokenText)
                    }
                }
                resetListeningButton()
            }

            override fun onError(error: Int) {
                Log.e(TAG, "Speech recognition error: $error")
                resetListeningButton()
                Toast.makeText(
                    this@MainActivity,
                    "Please Speak Clearly",
                    Toast.LENGTH_SHORT
                ).show()
            }

            // Required overrides
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer.startListening(intent)
    } catch (e: Exception) {
        Log.e(TAG, "Error in startButtonTriggeredListening: ${e.message}", e)
        resetListeningButton()
        Toast.makeText(
            this,
            "Error starting speech recognition: ${e.message}",
            Toast.LENGTH_SHORT
        ).show()
    }
}

    private fun resetListeningButton() {
        runOnUiThread {
            isButtonTriggeredListening = false
            startListeningButton.text = "Start Speaking"
        }
    }


    private fun setupPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PERMISSION_REQUEST_CODE
            )
        } else {
            initializeListening()
        }
    }

    private fun setupLanguageSpinner() {
        try {
            val adapter = object : ArrayAdapter<CharSequence>(
                this,
                android.R.layout.simple_spinner_item,
                resources.getStringArray(R.array.languages)
            ) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getView(position, convertView, parent)
                    (view as TextView).apply {
                        setTextColor(ContextCompat.getColor(context, android.R.color.white))
                        setTypeface(typeface, android.graphics.Typeface.BOLD) // Set text to bold
                    }
                    return view
                }

                override fun getDropDownView(
                    position: Int,
                    convertView: View?,
                    parent: ViewGroup
                ): View {
                    val view = super.getDropDownView(position, convertView, parent)
                    (view as TextView).apply {
                        setTextColor(ContextCompat.getColor(context, android.R.color.white))
                        setTypeface(typeface, android.graphics.Typeface.BOLD) // Set text to bold
                    }
                    return view
                }
            }
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.languageSpinner.adapter = adapter
            val spinnerIcon = binding.languageSpinner.background
            if (spinnerIcon != null) {
                spinnerIcon.setTint(ContextCompat.getColor(this, android.R.color.white))

            }

            binding.languageSpinner.onItemSelectedListener =
                object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long
                    ) {
                        (view as? TextView)?.setTextColor(
                            ContextCompat.getColor(
                                this@MainActivity,
                                android.R.color.white
                            )
                        )
                        currentLanguage = if (position == 0) "en-US" else "ta-IN"
                        updateTextToSpeechLanguage() // Update TTS language when spinner selection changes
                        //restartListening()
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error in setupLanguageSpinner: ${e.message}", e)
        }
    }

    private fun startVideoCall() {
        val intent = Intent(this, VideoCallActivity::class.java)
        startActivity(intent)
    }


    private fun setupVideoUris() {
        idleVideoUri = Uri.parse("android.resource://$packageName/raw/i12")
        speakVideoUri = Uri.parse("android.resource://$packageName/raw/s1")
    }

    private fun setupVideoView() {
        try {
            binding.videoView.setVideoURI(idleVideoUri)
            binding.videoView.setOnPreparedListener { mediaPlayer ->
                mediaPlayer.isLooping = true
                mediaPlayer.start()
            }
            binding.videoView.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "Video error: what=$what extra=$extra")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in setupVideoView: ${e.message}", e)
        }
    }


    private fun setupSpeechRecognizer() {

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        } catch (e: Exception) {
            Log.e(TAG, "Error in setupSpeechRecognizer: ${e.message}", e)
        }
    }


    private fun startListening(intent: Intent) {
        try {
            speechRecognizer.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error in startListening: ${e.message}", e)
        }
    }

    private fun setupTextToSpeech() {
        try {
            textToSpeech = TextToSpeech(this, this)
            textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    runOnUiThread {
                        isSpeaking = true
                        switchToSpeakVideo()
                    }
                }

                override fun onDone(utteranceId: String?) {
                    runOnUiThread {
                        isSpeaking = false
                        switchToIdleVideo()
                        // Clear the response text when speech is completed
                        responseTextView.text = ""
                    }
                }

                override fun onError(utteranceId: String?) {
                    runOnUiThread {
                        isSpeaking = false
                        switchToIdleVideo()
                        // Clear the response text in case of error too
                        responseTextView.text = ""
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error in setupTextToSpeech: ${e.message}", e)
        }
    }


    private fun setupGeminiAI() {
        try {
            generativeModel = GenerativeModel(
                modelName = "gemini-2.0-flash",
                apiKey = "" // Replace with your actual API key
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in setupGeminiAI: ${e.message}", e)
        }
    }

    private fun initializeListening() {
        Log.d(TAG, "Initializing listening")
        scope.launch {
            delay(1000)
            //startListening()
        }
    }

    private fun updateTextToSpeechLanguage() {
        val locale = if (currentLanguage == "ta-IN") {
            Locale("ta", "IN")
        } else {
            Locale.US
        }
        val result = textToSpeech.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.e(TAG, "Language not supported: $currentLanguage")
            Toast.makeText(this, "HELLO ", Toast.LENGTH_LONG).show()
        }
    }
    private fun checkCustomQueries(input: String): String? {
        val lowercaseInput = input.lowercase(Locale.getDefault())
        Log.d(TAG, "Checking custom queries in: $lowercaseInput")

        val queries = if (currentLanguage == "ta-IN") {
            // Check both Tamil and English queries for Tamil language
            tamilCustomQueries + customQueries
        } else {
            customQueries
        }

        val matchedEntry = queries.entries.firstOrNull { (keyword, _) ->
            lowercaseInput.contains(keyword.lowercase(Locale.getDefault()))
        }

        if (matchedEntry != null) {
            Log.d(TAG, "Found matching query: ${matchedEntry.key}")
        } else {
            Log.d(TAG, "No matching custom query found")
        }

        return matchedEntry?.value
    }
    private fun processInput(input: String) {
        Log.d(TAG, "Processing input: $input")
        val lowercaseInput = input.lowercase()

        // Check if survey is active and handle survey response


        // Start processing normal chatbot interaction
        scope.launch {
            try {
                if (isProcessingResponse) return@launch
                isProcessingResponse = true

                // Show loading message immediately
                withContext(Dispatchers.Main) {
                    responseTextView.text = if (currentLanguage == "ta-IN") {
                        "பதில் தயாராகிறது..."
                    } else {
                        "Processing your request..."
                    }
                }

                // Check for custom queries first
                val customResponse = checkCustomQueries(lowercaseInput)

                var outputText = if (customResponse != null) {
                    customResponse
                } else {
                    // Get response from Gemini API with language-specific prompt
                    val response: GenerateContentResponse = withContext(Dispatchers.IO) {
                        when (currentLanguage) {
//                            "ta-IN" -> {
//                                generativeModel.generateContent("""
//                                நான் டி-போட். அடாப்ட் ரோபோடிக்ஸால் உருவாக்கப்பட்டேன்.
//                                கேள்விக்கு 3 வாக்கியங்களில் தமிழில் நேரடியாக பதிலளிக்கவும்: $input
//                            """.trimIndent())
//                            }
//                            else -> {
//                                generativeModel.generateContent("""
//                                I am tBot from Adapt Robotics.
//                                Provide a direct response in 3 sentences: $input
//                            """.trimIndent())
//                            }
                            "ta-IN" -> {
                                generativeModel.generateContent("""
                                 நான் டி-போட், உங்கள் நண்பர். அடாப்ட் ரோபோடிக்ஸால் உருவாக்கப்பட்டேன்.
                                இந்த கேள்விக்கு ஒரு நெருக்கமான நண்பர் போல் உரையாடும் முறையில் பதிலளிக்கவும்: $input
                                பதில் சுருக்கமாகவும், உரையாடல் போன்றும் இருக்க வேண்டும். வார்த்தைகளை அதிகம் பயன்படுத்த வேண்டாம்.எமோஜிகளைப் பயன்படுத்த வேண்டாம்.
                            """.trimIndent())
                            }
                            else -> {
                                generativeModel.generateContent("""
                                I am tBot, a friendly assistant from Adapt Robotics.
                                Respond to this question in a warm, friendly manner as if talking to a close friend: if needed speak professionally also $input
                                Keep it brief and conversational for about 3 sentences is enough. Use contractions (like I'm, you're) and a casual tone. 
                                Add small personal touches when appropriate. Sound like a real person, not a formal robot. Dont't use emojis.
                            """.trimIndent())
                            }

                        }
                    }

                    response.text ?: if (currentLanguage == "ta-IN") {
                        "சொல்லப் போனால், எனக்கு சரியாக புரியவில்லை. மீண்டும் சொல்ல முடியுமா?"
                    } else {
                        "Hmm, I didn't quite catch that. Can you say it again?"
                    }
                }

                // Clean up the text
                outputText = outputText.replace(Regex("[*]"), "").trim()
                if (currentLanguage != "ta-IN" && !customResponse.isNullOrEmpty() && Random().nextInt(10) < 3) {
                    val fillers = listOf("Let's see... ", "Well, ", "Hmm, ", "You know, ")
                    outputText = fillers.random() + outputText
                }

                withContext(Dispatchers.Main) {
                    responseTextView.text = outputText
                    speakText(outputText)

                    // Increment question count after successful response
                    questionCount++

                    // Check if it's time to trigger the survey
                }

                isProcessingResponse = false

            } catch (e: Exception) {
                Log.e(TAG, "Error in processInput: ${e.message}", e)
                isProcessingResponse = false
                withContext(Dispatchers.Main) {
                    showToast(if (currentLanguage == "ta-IN") {
                        "உள்ளீட்டை செயலாக்குவதில் பிழை"
                    } else {
                        "Error processing input"
                    })

                    // Reset processing state
                    isProcessingResponse = false

                    // Show error message in response text
                    responseTextView.text = if (currentLanguage == "ta-IN") {
                        "மன்னிக்கவும், ஒரு பிழை ஏற்பட்டது. மீண்டும் முயற்சிக்கவும்."
                    } else {
                        "Sorry, an error occurred. Please try again."
                    }
                }
            }
        }
    }

//    private fun processInput(input: String) {
//        Log.d(TAG, "Processing input: $input")
//        val lowercaseInput = input.lowercase()
//
//        scope.launch {
//            try {
//                if (isProcessingResponse) return@launch
//                isProcessingResponse = true
//
//                // Show loading message immediately
//                withContext(Dispatchers.Main) {
//                    responseTextView.text = if (currentLanguage == "ta-IN") {
//                        "பதில் தயாராகிறது..."
//                    } else {
//                        "Processing your request..."
//                    }
//                }
//
//                // Get response from Gemini API with language-specific prompt
//                val response: GenerateContentResponse = withContext(Dispatchers.IO) {
//                    when (currentLanguage) {
//                        "ta-IN" -> {
//                            generativeModel.generateContent(
//                                """
//                            நான் டி-பாட். அடாப்ட் ரோபோடிக்ஸால் உருவாக்கப்பட்டேன். என் வழிகாட்டி திரு. சாம் ஆஸ்டின்
//                            கேள்விக்கு 3 வாக்கியங்களில் தமிழில் நேரடியாக பதிலளிக்கவும்: $input
//                            """.trimIndent()
//                            )
//                        }
//                        else -> {
//                            generativeModel.generateContent(
//                                """
//                            I am tBot from Adapt Robotics and your mentor is Mr.Sam Austin.
//                            Provide a direct response in 3 sentences: $input
//                            """.trimIndent()
//                            )
//                        }
//                    }
//                }
//
//                val outputText = response.text ?: if (currentLanguage == "ta-IN") {
//                    "மன்னிக்கவும், பதிலை உருவாக்க முடியவில்லை."
//                } else {
//                    "Sorry, I couldn't generate a response."
//                }
//
//                // Clean up the text and remove asterisks
//                val cleanedText = outputText.replace(Regex("[*]"), "").trim()
//
//                withContext(Dispatchers.Main) {
//                    responseTextView.text = cleanedText
//                    speakText(cleanedText)
//                    questionCount++
//                }
//
//                isProcessingResponse = false
//
//            } catch (e: Exception) {
//                Log.e(TAG, "Error in processInput: ${e.message}", e)
//                isProcessingResponse = false
//                withContext(Dispatchers.Main) {
//                    showToast(
//                        if (currentLanguage == "ta-IN") {
//                            "உள்ளீட்டை செயலாக்குவதில் பிழை"
//                        } else {
//                            "Error processing input"
//                        }
//                    )
//
//                    // Reset processing state
//                    isProcessingResponse = false
//
//                    // Show error message in response text
//                    responseTextView.text = if (currentLanguage == "ta-IN") {
//                        "மன்னிக்கவும், ஒரு பிழை ஏற்பட்டது. மீண்டும் முயற்சிக்கவும்."
//                    } else {
//                        "Sorry, an error occurred. Please try again."
//                    }
//                }
//            }
//        }
//    }


    private fun containsTriggerKeyword(input: String): Boolean {
        val lowercaseInput = input.lowercase(Locale.getDefault())
        Log.d(TAG, "Checking trigger keywords in: $lowercaseInput")

        val containsKeyword = triggerKeywords.any { keyword ->
            lowercaseInput.contains(keyword.lowercase(Locale.getDefault()))
        }

        if (containsKeyword) {
            Log.d(TAG, "Trigger keyword found")
        } else {
            Log.d(TAG, "No trigger keyword found")
        }

        return containsKeyword
    }

//    private fun speakText(text: String) {
//        if (textToSpeech?.isSpeaking == true) {
//            textToSpeech?.stop()
//        }
//
//        // Set speech rate slightly slower for Tamil
//        val speechRate = if (currentLanguage == "ta-IN") 0.85f else 1.0f
//        textToSpeech?.setSpeechRate(speechRate)
//
//        // Use proper utterance ID for callback handling
//        val utteranceId = "SPEECH_${System.currentTimeMillis()}"
//        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
//    }
private fun speakText(text: String) {
    if (textToSpeech?.isSpeaking == true) {
        textToSpeech?.stop()
    }

    // Add speech pauses using SSML-like commas and periods
    var processedText = text

    // Only add pauses if not already present
    if (!text.contains("<break")) {
        // Add natural pause markers
        processedText = processedText.replace(". ", "... ")
            .replace("? ", "... ")
            .replace("! ", "... ")
    }

    // Different speech rates for different languages
    val speechRate = when (currentLanguage) {
        "ta-IN" -> 0.85f
        else -> 0.95f  // Slightly slower than default for more natural English
    }

    // Add slight pitch variations for more human-like speech
    val pitch = 1.0f + (Random().nextFloat() * 0.1f - 0.05f)  // Random pitch variation ±5%

    textToSpeech?.setPitch(pitch)
    textToSpeech?.setSpeechRate(speechRate)

    // Use proper utterance ID for callback handling
    val utteranceId = "SPEECH_${System.currentTimeMillis()}"
    textToSpeech?.speak(processedText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
}


    private fun switchToSpeakVideo() {
        binding.videoView.setVideoURI(speakVideoUri)
        binding.videoView.start()
    }

    private fun switchToIdleVideo() {
        binding.videoView.setVideoURI(idleVideoUri)
        binding.videoView.start()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }


//    override fun onInit(status: Int) {
//        if (status == TextToSpeech.SUCCESS) {
//            // Set language based on current selection
//            updateTextToSpeechLanguage()
//        } else {
//            Log.e(TAG, "TextToSpeech initialization failed")
//        }
//    }
override fun onInit(status: Int) {
    if (status == TextToSpeech.SUCCESS) {
        // Set language based on current selection
        updateTextToSpeechLanguage()

        // Configure TTS for more natural speech
        textToSpeech.setPitch(2.0f)  // Natural pitch

        // Set up utterance progress listener for dynamic speech
        textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                runOnUiThread {
                    isSpeaking = true
                    switchToSpeakVideo()
                }
            }

            override fun onDone(utteranceId: String?) {
                runOnUiThread {
                    isSpeaking = false
                    switchToIdleVideo()
                    // Clear the response text when speech is completed
                    Handler(Looper.getMainLooper()).postDelayed({
                        responseTextView.text = ""
                    }, 500) // Small delay before clearing
                }
            }

            override fun onError(utteranceId: String?) {
                runOnUiThread {
                    isSpeaking = false
                    switchToIdleVideo()
                    // Clear the response text in case of error too
                    responseTextView.text = ""
                }
            }
        })
    } else {
        Log.e(TAG, "TextToSpeech initialization failed")
    }
}

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
        textToSpeech.shutdown()
        if (::webSocket.isInitialized && webSocket.isOpen) {
            webSocket.close()
            scope.cancel()
            //keywordTimer?.let { handler.removeCallbacks(it) }
        }


    }
}
