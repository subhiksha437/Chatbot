package com.example.app7


import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Button
import android.widget.TextView
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI
import android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH
import android.widget.Toast

class BotMove : AppCompatActivity() {

    private lateinit var webSocket: WebSocketClient
    private val serverUrl = "ws://172.16.66.142:9090" // Update with your server URL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_botmove)

        connectWebSocket()

        val buttonUp = findViewById<Button>(R.id.buttonUp)
        val buttonDown = findViewById<Button>(R.id.buttonDown)
        val buttonLeft = findViewById<Button>(R.id.buttonLeft)
        val buttonRight = findViewById<Button>(R.id.buttonRight)
        val voiceCommandButton = findViewById<Button>(R.id.speechButton) // Add this button for voice input

        // Button listeners to send movement commands
        buttonUp.setOnClickListener { sendCommand(0.5, 0.0) }
        buttonDown.setOnClickListener { sendCommand(-0.5, 0.0) }
        buttonLeft.setOnClickListener { sendCommand(0.0, 1.0) }
        buttonRight.setOnClickListener { sendCommand(0.0, -1.0) }

        // Voice command listener
        voiceCommandButton.setOnClickListener {
            // Start speech-to-text when the button is clicked
            startVoiceInput()
        }
    }

    private fun connectWebSocket() {
        webSocket = object : WebSocketClient(URI(serverUrl)) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                println("WebSocket connected")
                getPosition()  // Subscribe to the position updates
            }

            override fun onMessage(message: String?) {
                println("Received message: $message")
                // Handle incoming messages, update UI as needed (same as previous example)
                message?.let {
                    runOnUiThread {
                        val displayText = findViewById<TextView>(R.id.user_text)
                        val botPose = findViewById<TextView>(R.id.bot_pose)
                        //botPose.movementMethod = ScrollingMovementMethod()
                        val jsonObject = JSONObject(it)

                        if (jsonObject.optString("topic") == "/pose") {
                            val data = jsonObject.optJSONObject("msg") //?: return@runOnUiThread
                            val pose = data?.optJSONObject("pose")?.optJSONObject("pose")
                            // Get Position Data (x, y, z)
                            val position = pose?.optJSONObject("position")
                            val x = position?.optDouble("x", 0.0) ?: 0.0
                            val y = position?.optDouble("y", 0.0) ?: 0.0
                            val z = position?.optDouble("z", 0.0) ?: 0.0

                            // Get Orientation Data (x, y, z, w)
                            val orientation = pose?.optJSONObject("orientation")
                            val ox = orientation?.optDouble("x", 0.0) ?: 0.0
                            val oy = orientation?.optDouble("y", 0.0) ?: 0.0
                            val oz = orientation?.optDouble("z", 0.0) ?: 0.0
                            val ow = orientation?.optDouble("w", 1.0) ?: 1.0

                            // Format the Pose data as a string to display
                            val poseText = "Position: (x: $x, y: $y, z: $z)\n" +
                                    "Orientation: (x: $ox, y: $oy, z: $oz, w: $ow)\n"
                            botPose.text = botPose.text.toString() + poseText
                        }
                        if (jsonObject.optString("topic") == "/message") {

                            val msg = JSONObject(jsonObject.getString("msg"))
                            val data = msg.getString("data")

                            displayText.text = data
                        }
                        if (jsonObject.optString("topic") == "/cmd_vel"){
                            val data = jsonObject.optJSONObject("msg") //?: return@runOnUiThread
                            //val pose = data?.optJSONObject("pose")?.optJSONObject("pose")

                            val linearVel = data?.optJSONObject("linear")
                            val lx = linearVel?.optDouble("x", 0.0) ?: 0.0
                            val ly = linearVel?.optDouble("y", 0.0) ?: 0.0
                            val lz = linearVel?.optDouble("z", 0.0) ?: 0.0

                            // Get Orientation Data (x, y, z, w)
                            val angularVel = data?.optJSONObject("angular")
                            val ax = angularVel?.optDouble("x", 0.0) ?: 0.0
                            val ay = angularVel?.optDouble("y", 0.0) ?: 0.0
                            val az = angularVel?.optDouble("z", 0.0) ?: 0.0

                            val poseText = "Linear Velocity: (x: $lx, y: $ly, z: $lz)\n" +
                                    "Angular Velocity: (x: $ax, y: $ay, z: $az)\n"
                            botPose.text = botPose.text.toString() + poseText
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
        webSocket.connect() // Initiate the connection
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
        webSocket.send(message.toString())  // Send the command to move the bot
    }

    private fun getPosition() {
        // Send a subscription message to WebSocket server for position updates
        val subscribeMsg1 = JSONObject().apply {
            put("op", "subscribe")
            put("topic", "/pose")
            put("type", "geometry_msgs/msg/PoseWithCovarianceStamped")
        }
        val subscribeMsg2 = JSONObject().apply {
            put("op", "subscribe")
            put("topic", "/cmd_vel")
            put("type", "geometry_msgs/msg/Twist")
        }
        val subscribeMsg3 = JSONObject().apply {
            put("op", "subscribe")
            put("topic", "/message")
            put("type", "std_msgs/msg/String")
        }
        webSocket.send(subscribeMsg1.toString())
        webSocket.send(subscribeMsg2.toString())
        webSocket.send(subscribeMsg3.toString())
    }

    // Start the speech-to-text intent to capture the voice command
    private fun startVoiceInput() {
        val intent = Intent(ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a command, e.g., 'move forward'")
        startActivityForResult(intent, 100) // Request code 100 for speech input
    }

    // Handle the result from the speech-to-text activity
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            val spokenText = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
            spokenText?.let {
                processVoiceCommand(it)
            }
        }
    }

    // Process the voice command to perform movement operations
    private fun processVoiceCommand(command: String) {
        val command_word = command.toLowerCase()
        if ("forward" in command_word || "ahead" in command_word || "front" in command_word) sendCommand(0.5, 0.0)
        else if("back" in command_word || "backward" in command_word || "behind" in command_word) sendCommand(-0.5, 0.0)
        else if("left" in command_word) sendCommand(0.0, 1.0)
        else if("right" in command_word) sendCommand(0.0, -1.0)
        else Toast.makeText(this, "Command not recognized", Toast.LENGTH_SHORT).show()

//        when (command.toLowerCase()) {
//            "move forward" -> sendCommand(0.5, 0.0)
//            "move backward" -> sendCommand(-0.5, 0.0)
//            "turn left" -> sendCommand(0.0, 1.0)
//            "turn right" -> sendCommand(0.0, -1.0)
//            else -> Toast.makeText(this, "Command not recognized", Toast.LENGTH_SHORT).show()
//        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Close the WebSocket connection to avoid leaks
        if (webSocket.isOpen) {
            webSocket.close()
        }
    }
}
//-------------------------------------------------------------------
/*package com.example.botmove

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI

class MainActivity : AppCompatActivity() {

    private lateinit var webSocket: WebSocketClient
    private val serverUrl = "ws://192.168.250.178:9090" // Update with your server URL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        connectWebSocket()

        val buttonUp = findViewById<Button>(R.id.buttonUp)
        val buttonDown = findViewById<Button>(R.id.buttonDown)
        val buttonLeft = findViewById<Button>(R.id.buttonLeft)
        val buttonRight = findViewById<Button>(R.id.buttonRight)

        buttonUp.setOnClickListener { sendCommand(0.5, 0.0) }
        buttonDown.setOnClickListener { sendCommand(-0.5, 0.0) }
        buttonLeft.setOnClickListener { sendCommand(0.0, 1.0) }
        buttonRight.setOnClickListener { sendCommand(0.0, -1.0) }
    }

    private fun connectWebSocket() {
        webSocket = object : WebSocketClient(URI(serverUrl)) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                println("WebSocket connected")
                getPosition()
                receiveText()
            }

            override fun onMessage(message: String?) {
                println("Received message: $message")

                message?.let {
                    runOnUiThread {
                        val displayText = findViewById<TextView>(R.id.user_text)
                        val botPose = findViewById<TextView>(R.id.bot_pose)
                        //botPose.movementMethod = ScrollingMovementMethod()
                        val jsonObject = JSONObject(it)

                        if (jsonObject.optString("topic") == "/pose") {
                            val data = jsonObject.optJSONObject("msg") //?: return@runOnUiThread
                            val pose = data?.optJSONObject("pose")?.optJSONObject("pose")
                            // Get Position Data (x, y, z)
                            val position = pose?.optJSONObject("position")
                            val x = position?.optDouble("x", 0.0) ?: 0.0
                            val y = position?.optDouble("y", 0.0) ?: 0.0
                            val z = position?.optDouble("z", 0.0) ?: 0.0

                            // Get Orientation Data (x, y, z, w)
                            val orientation = pose?.optJSONObject("orientation")
                            val ox = orientation?.optDouble("x", 0.0) ?: 0.0
                            val oy = orientation?.optDouble("y", 0.0) ?: 0.0
                            val oz = orientation?.optDouble("z", 0.0) ?: 0.0
                            val ow = orientation?.optDouble("w", 1.0) ?: 1.0

                            // Format the Pose data as a string to display
                            val poseText = "Position: (x: $x, y: $y, z: $z)\n" +
                                    "Orientation: (x: $ox, y: $oy, z: $oz, w: $ow)\n"
                            botPose.text = botPose.text.toString() + poseText
                        }
                        if (jsonObject.optString("topic") == "/message") {

                            val msg = JSONObject(jsonObject.getString("msg"))
                            val data = msg.getString("data")

                            displayText.text = data
                        }
                        if (jsonObject.optString("topic") == "/cmd_vel"){
                            val data = jsonObject.optJSONObject("msg") //?: return@runOnUiThread
                            //val pose = data?.optJSONObject("pose")?.optJSONObject("pose")

                            val linearVel = data?.optJSONObject("linear")
                            val lx = linearVel?.optDouble("x", 0.0) ?: 0.0
                            val ly = linearVel?.optDouble("y", 0.0) ?: 0.0
                            val lz = linearVel?.optDouble("z", 0.0) ?: 0.0

                            // Get Orientation Data (x, y, z, w)
                            val angularVel = data?.optJSONObject("angular")
                            val ax = angularVel?.optDouble("x", 0.0) ?: 0.0
                            val ay = angularVel?.optDouble("y", 0.0) ?: 0.0
                            val az = angularVel?.optDouble("z", 0.0) ?: 0.0

                            val poseText = "Linear Velocity: (x: $lx, y: $ly, z: $lz)\n" +
                                    "Angular Velocity: (x: $ax, y: $ay, z: $az)\n"
                            botPose.text = botPose.text.toString() + poseText
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
        webSocket.connect() // Initiate the connection
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

    private fun receiveText() {
        val displayText = findViewById<TextView>(R.id.user_text)

        // Send a subscription message to the WebSocket server
        val subscribeMsg = JSONObject().apply {
            put("op", "subscribe")
            put("topic", "/message")
            put("type", "std_msgs/String")
        }
        webSocket.send(subscribeMsg.toString())

        // Handle messages received from the WebSocket
//        webSocket.addOnMessageListener { message ->
//            message?.let {
//                runOnUiThread {
//                    // Parse the received message and update the TextView
//                    val jsonObject = JSONObject(it)
//                    val receivedText = jsonObject.optString("data", "No message")
//                    displayText.text = receivedText
//                }
//            }
//        }
    }

    private fun getPosition(){
        val displayPose = findViewById<TextView>(R.id.bot_pose)

        // Send a subscription message to the WebSocket server
        val subscribeMsg = JSONObject().apply {
            put("op", "subscribe")
            put("topic", "/pose")
            put("type", "geometry_msgs/msg/PoseWithCovarianceStamped")
        }
        webSocket.send(subscribeMsg.toString())

        val velMsg = JSONObject().apply {
            put("op", "subscribe")
            put("topic", "/cmd_vel")
            put("type", "geometry_msgs/msg/Twist")
        }
        webSocket.send(velMsg.toString())
    }

    override fun onDestroy() {
        super.onDestroy()
        // Close the WebSocket connection to avoid leaks
        if (webSocket.isOpen) {
            webSocket.close()
        }
    }
}
*/
