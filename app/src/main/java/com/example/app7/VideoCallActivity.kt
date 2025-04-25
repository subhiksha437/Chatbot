package com.example.app7

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.webrtc.*
import android.os.Handler
import android.os.Looper
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import android.util.Log
import android.view.View
import com.example.app7.databinding.ActivityCallBinding
import com.example.app7.databinding.ActivityMainBinding
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import org.json.JSONObject
import java.net.URISyntaxException

class VideoCallActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1234
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
        )
    }

    private lateinit var mSocket: Socket
    private lateinit var webView: WebView
    private lateinit var binding: ActivityCallBinding
    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private lateinit var peerConnection: PeerConnection
    private lateinit var eglBase: EglBase
    private var permissionsGranted = false

    private var lastKeywordResponseTime: Long = 0
    private val keywordTimeout = 15000L // 15 seconds in milliseconds
    private val handler = Handler(Looper.getMainLooper())
    private var keywordRequired = true

    private val onConnect = Emitter.Listener {
        runOnUiThread {
            Log.d("SocketIO", "Connected to signaling server")
            val data = JSONObject().apply {
                put("message", "Hello, server!")
            }
            mSocket.emit("message", data)
        }
    }

    private val onOfferReceived = Emitter.Listener { args ->
        runOnUiThread {
            val data = args[0] as JSONObject
            Log.d("SocketIO", "Received offer: $data")
            val sdp = data.getString("sdp")
            val type = SessionDescription.Type.OFFER
            val sessionDescription = SessionDescription(type, sdp)
            handleRemoteOffer(sessionDescription)
        }
    }

    private val onAnswerReceived = Emitter.Listener { args ->
        runOnUiThread {
            val data = args[0] as JSONObject
            Log.d("SocketIO", "Received answer: $data")
            val sdp = data.getString("sdp")
            val type = SessionDescription.Type.ANSWER
            val sessionDescription = SessionDescription(type, sdp)
            peerConnection.setRemoteDescription(object : SdpObserver {
                override fun onCreateSuccess(p0: SessionDescription?) {}
                override fun onSetSuccess() {}
                override fun onCreateFailure(p0: String?) {}
                override fun onSetFailure(p0: String?) {}
            }, sessionDescription)
        }
    }

    private val onIceCandidateReceived = Emitter.Listener { args ->
        runOnUiThread {
            val data = args[0] as JSONObject
            Log.d("SocketIO", "Received ICE candidate: $data")
            val sdpMid = data.getString("sdpMid")
            val sdpMLineIndex = data.getInt("sdpMLineIndex")
            val sdp = data.getString("candidate")
            val candidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
            peerConnection.addIceCandidate(candidate)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set the background color
        window.decorView.setBackgroundColor(Color.argb(0, 0, 0, 0))
        setContentView(R.layout.activity_call)


        // Initialize EglBase context
        eglBase = EglBase.create()

        if (!checkPermissions()) {
            requestPermissions()
        } else {
            permissionsGranted = true
            initializeVideoCall()
        }
    }


    private fun initializeVideoCall() {
        setupWebView()
        initializeWebRTC()
        setupSocketConnection()
        loadVideoCallUrl()
    }

    private fun setupWebView() {
        webView = findViewById(R.id.webView)
        webView.settings.apply {
            javaScriptEnabled = true
            mediaPlaybackRequiresUserGesture = false
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            databaseEnabled = true
            setGeolocationEnabled(true)
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    request.grant(request.resources)
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                Log.e("WebView", "Error loading page: ${error?.description}")
            }
        }

        WebView.setWebContentsDebuggingEnabled(true)
    }

    private fun initializeWebRTC() {
        val options = PeerConnectionFactory.InitializationOptions.builder(this)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        // Create encoder/decoder factories using EglBase context
        val encoderFactory = DefaultVideoEncoderFactory(
            eglBase.eglBaseContext,
            true,
            true
        )
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        val builder = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .setOptions(PeerConnectionFactory.Options().apply {
                disableEncryption = false
                disableNetworkMonitor = false
            })

        peerConnectionFactory = builder.createPeerConnectionFactory()

        setupPeerConnection()
    }
    private fun loadVideoCallUrl() {
        if (permissionsGranted) {
            // Replace this URL with your actual video call server URL
            webView.loadUrl("https://e154-103-177-155-34.ngrok-free.app")

            // Optional: Add a loading indicator
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // Hide loading indicator if you have one
                    Log.d("WebView", "Page loaded successfully")
                }
            }
        } else {
            Log.e("VideoCall", "Cannot load URL - permissions not granted")
        }
    }


    private fun setupSocketConnection() {
        try {
            mSocket = IO.socket("http://10.0.2.2:3000")
            mSocket.connect()

            mSocket.on(Socket.EVENT_CONNECT, onConnect)
            mSocket.on("offer", onOfferReceived)
            mSocket.on("answer", onAnswerReceived)
            mSocket.on("ice-candidate", onIceCandidateReceived)

        } catch (e: URISyntaxException) {
            Log.e("SocketIO", "Socket connection error: ${e.message}")
            Toast.makeText(this, "Failed to connect to signaling server", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupPeerConnection() {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            // Add TURN servers if needed
            // PeerConnection.IceServer.builder("turn:your-turn-server")
            //     .setUsername("username")
            //     .setPassword("password")
            //     .createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            keyType = PeerConnection.KeyType.ECDSA

            // Optional: Add these configurations for better connection handling
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
        }
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(newState: PeerConnection.SignalingState) {
                Log.d("WebRTC", "onSignalingChange: $newState")
            }

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
                Log.d("WebRTC", "onIceConnectionChange: $newState")
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {
                Log.d("WebRTC", "onIceConnectionReceivingChange: $receiving")
            }

            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) {
                Log.d("WebRTC", "onIceGatheringChange: $newState")
            }

            override fun onIceCandidate(candidate: IceCandidate) {
                val json = JSONObject().apply {
                    put("sdpMid", candidate.sdpMid)
                    put("sdpMLineIndex", candidate.sdpMLineIndex)
                    put("candidate", candidate.sdp)
                }
                mSocket.emit("ice-candidate", json)
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {
                Log.d("WebRTC", "onIceCandidatesRemoved")
            }

            override fun onAddStream(mediaStream: MediaStream) {
                Log.d("WebRTC", "onAddStream")
                runOnUiThread {
                    val js = """
                        var video = document.getElementById('remoteVideo');
                        if (video) {
                            video.srcObject = new MediaStream();
                            video.play().catch(error => console.error('Error playing video:', error));
                        }
                    """.trimIndent()
                    webView.evaluateJavascript(js, null)
                }
            }

            override fun onRemoveStream(mediaStream: MediaStream) {
                Log.d("WebRTC", "onRemoveStream")
            }

            override fun onDataChannel(dataChannel: DataChannel) {
                Log.d("WebRTC", "onDataChannel")
            }

            override fun onRenegotiationNeeded() {
                Log.d("WebRTC", "onRenegotiationNeeded")
            }

            override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {
                Log.d("WebRTC", "onAddTrack")
                runOnUiThread {
                    val js = """
                        var video = document.getElementById('remoteVideo');
                        if (video) {
                            video.srcObject = new MediaStream([receiver.track]);
                            video.play().catch(error => console.error('Error playing video:', error));
                        }
                    """.trimIndent()
                    webView.evaluateJavascript(js, null)
                }
            }
        }) ?: throw IllegalStateException("Failed to create peer connection")
    }

    private fun handleRemoteOffer(sessionDescription: SessionDescription) {
        peerConnection.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                createAndSendAnswer()
            }
            override fun onSetFailure(error: String?) {
                Log.e("WebRTC", "Failed to set remote description: $error")
            }
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, sessionDescription)
    }

    private fun createAndSendAnswer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }

        peerConnection.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(answer: SessionDescription) {
                peerConnection.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        val json = JSONObject().apply {
                            put("type", "answer")
                            put("sdp", answer.description)
                        }
                        mSocket.emit("answer", json)
                    }
                    override fun onSetFailure(error: String?) {
                        Log.e("WebRTC", "Failed to set local description: $error")
                    }
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                }, answer)
            }
            override fun onCreateFailure(error: String?) {
                Log.e("WebRTC", "Failed to create answer: $error")
            }
            override fun onSetSuccess() {}
            override fun onSetFailure(p0: String?) {}
        }, constraints)
    }

    private fun checkPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                permissionsGranted = true
                initializeVideoCall()
            } else {
                Toast.makeText(this, "Permissions required for video call", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        mSocket.disconnect()
        mSocket.off(Socket.EVENT_CONNECT, onConnect)
        mSocket.off("offer", onOfferReceived)
        mSocket.off("answer", onAnswerReceived)
        mSocket.off("ice-candidate", onIceCandidateReceived)
        peerConnection.dispose()
        peerConnectionFactory.dispose()
        eglBase.release()
        super.onDestroy()
    }


}