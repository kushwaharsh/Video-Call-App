package com.example.agoravcdemo

import android.content.pm.PackageManager
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.agoravcdemo.databinding.ActivityMainBinding
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.video.VideoCanvas

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val appId = "6d7c1ef96dde4339b93a842ac226dff3"
    private val channelName = "AgoraDemo"
    private val uid = 1
    private val token = "007eJxTYIg+wHFwwoTv6jySoUb8ZW/jGpcarY7sObUzdF7ljbdlyYIKDGYp5smGqWmWZikpqSbGxpZJlsaJFiZGiclGRmYpaWnGZh+PpTUEMjIU32tmYIRCEJ+TwTE9vyjRJTU3n4EBAOpsIdk="

    private var isJoinded = false
    private var agoraEngine: RtcEngine? = null
    private var localSurfaceView: SurfaceView? = null
    private var remoteSurfaceView: SurfaceView? = null
    private var isMuted = false // Variable to track mute state
    private var isCameraEnabled = true // Variable to track camera state
    private var isUsingFrontCamera = true // Variable to track the current camera

    private val PERMISSION_ID = 22
    private val REQUESTED_PERMISSION = arrayOf(
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.CAMERA
    )

    private fun checkSelfPermission(): Boolean {
        return REQUESTED_PERMISSION.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun showMessages(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setUpVideoSdkEngine() {
        try {
            val config = RtcEngineConfig()
            config.mContext = baseContext
            config.mAppId = appId
            config.mEventHandler = mRtcEventHandler
            agoraEngine = RtcEngine.create(config)
            agoraEngine?.enableVideo()
        } catch (e: Exception) {
            showMessages(e.message ?: "Error initializing Agora SDK")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!checkSelfPermission()) {
            ActivityCompat.requestPermissions(this, REQUESTED_PERMISSION, PERMISSION_ID)
        } else {
            setUpVideoSdkEngine()
        }

        binding.joinButton.setOnClickListener {
            joinCall()
        }

        binding.endCall.setOnClickListener {
            leaveCall()
        }

        binding.muteAudio.setOnClickListener {
            toggleMute()
        }

        binding.cameraAccess.setOnClickListener {
            toggleCamera()
        }

        binding.reverseCamera.setOnClickListener {
            switchCamera()
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        agoraEngine?.let {
            it.stopPreview()
            it.leaveChannel()
            RtcEngine.destroy()
            agoraEngine = null
        }
    }

    private fun joinCall() {
        if (checkSelfPermission()) {
            val option = ChannelMediaOptions().apply {
                channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
                clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
            }
            setUpLocalVideo()
            localSurfaceView?.visibility = View.VISIBLE
            agoraEngine?.startPreview()
            agoraEngine?.joinChannel(token, channelName, uid, option)
        } else {
            Toast.makeText(this, "Permission Not Granted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun leaveCall() {
        if (!isJoinded) {
            showMessages("Join the channel First")
        } else {
            agoraEngine?.leaveChannel()
            showMessages("You left the channel")
            remoteSurfaceView?.visibility = View.GONE
            localSurfaceView?.visibility = View.GONE
            isJoinded = false
        }
    }

    private fun toggleMute() {
        isMuted = !isMuted
        agoraEngine?.muteLocalAudioStream(isMuted)

        val iconTint = if (isMuted) {
            ContextCompat.getColor(this, R.color.red)
        } else {
            ContextCompat.getColor(this, R.color.white)
        }
        binding.muteAudio.setColorFilter(iconTint, PorterDuff.Mode.SRC_IN)
    }

    private fun toggleCamera() {
        isCameraEnabled = !isCameraEnabled

        if (isCameraEnabled) {
            agoraEngine?.enableVideo()
            showMessages("Camera Enabled")

            val iconTint = ContextCompat.getColor(this, R.color.white)
            binding.cameraAccess.setColorFilter(iconTint, PorterDuff.Mode.SRC_IN)
        } else {
            agoraEngine?.disableVideo()
            showMessages("Camera Disabled")

            val iconTint = ContextCompat.getColor(this, R.color.red)
            binding.cameraAccess.setColorFilter(iconTint, PorterDuff.Mode.SRC_IN)
        }
    }

    private fun switchCamera() {
        if (isCameraEnabled) {
            isUsingFrontCamera = !isUsingFrontCamera
            agoraEngine?.switchCamera()

            val iconTint = if (isUsingFrontCamera) {
                ContextCompat.getColor(this, R.color.white)
            } else {
                ContextCompat.getColor(this, R.color.red)
            }
            binding.reverseCamera.setColorFilter(iconTint, PorterDuff.Mode.SRC_IN)
        } else {
            showMessages("Camera is disabled. Please enable the camera first.")
        }
    }



    private val mRtcEventHandler: IRtcEngineEventHandler = object : IRtcEngineEventHandler() {

        override fun onUserJoined(uid: Int, elapsed: Int) {
            showMessages("Remote User Joined $uid")
            runOnUiThread { setUpRemoteVideo(uid) }
        }

        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            isJoinded = true
            showMessages("Joined Channel $channel")
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            showMessages("User Offline")
            runOnUiThread {
                remoteSurfaceView?.visibility = View.GONE
            }
        }
    }

    private fun setUpRemoteVideo(uid: Int) {
        remoteSurfaceView = SurfaceView(baseContext).apply {
          // setZOrderMediaOverlay(true)
        }
        remoteSurfaceView?.let {
            binding.vedioRemoteUser.addView(it)
            agoraEngine?.setupRemoteVideo(
                VideoCanvas(
                    it,
                    VideoCanvas.RENDER_MODE_FIT,
                    uid
                )
            )
            it.visibility = View.VISIBLE
        } ?: showMessages("Failed to initialize remoteSurfaceView")
    }

    private fun setUpLocalVideo() {
        localSurfaceView = SurfaceView(baseContext).apply {
            setZOrderMediaOverlay(true)
        }
        localSurfaceView?.let { surfaceView ->
            binding.videoLocalUser.addView(surfaceView)
            agoraEngine?.setupLocalVideo(
                VideoCanvas(
                    surfaceView,
                    VideoCanvas.RENDER_MODE_FIT,
                    uid
                )
            )
            surfaceView.visibility = View.VISIBLE

            // Add touch listener to make the videoLocalUser draggable
            binding.videoLocalUser.setOnTouchListener(object : View.OnTouchListener {
                private var dX = 0f
                private var dY = 0f
                private var lastAction = 0

                override fun onTouch(view: View, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            dX = view.x - event.rawX
                            dY = view.y - event.rawY
                            lastAction = MotionEvent.ACTION_DOWN
                        }
                        MotionEvent.ACTION_MOVE -> {
                            view.animate()
                                .x(event.rawX + dX)
                                .y(event.rawY + dY)
                                .setDuration(0)
                                .start()
                            lastAction = MotionEvent.ACTION_MOVE
                        }
                        MotionEvent.ACTION_UP -> {
                            // If the action was a tap, handle click event (optional)
                            if (lastAction == MotionEvent.ACTION_DOWN) {
                                view.performClick()
                            }
                        }
                        else -> return false
                    }
                    return true
                }
            })
        } ?: showMessages("Failed to initialize localSurfaceView")
    }


}
