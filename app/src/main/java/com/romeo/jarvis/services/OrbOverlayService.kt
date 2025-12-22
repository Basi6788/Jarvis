package com.romeo.jarvis.services

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import com.romeo.jarvis.R

class OrbOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var orbView: View
    private lateinit var orbCore: ImageView
    private lateinit var orbGlow: ImageView

    private lateinit var idleAnim: Animation
    private lateinit var listenAnim: Animation

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val inflater = LayoutInflater.from(this)
        orbView = inflater.inflate(R.layout.orb_overlay, null)

        orbCore = orbView.findViewById(R.id.orbCore)
        orbGlow = orbView.findViewById(R.id.orbGlow)

        idleAnim = AnimationUtils.loadAnimation(this, R.anim.orb_idle)
        listenAnim = AnimationUtils.loadAnimation(this, R.anim.orb_listening)

        startIdle()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.END
        params.x = 24
        params.y = 200

        // Drag to move
        orbView.setOnTouchListener(DragTouchListener(params, windowManager))

        windowManager.addView(orbView, params)
    }

    fun startIdle() {
        orbCore.clearAnimation()
        orbGlow.clearAnimation()
        orbCore.startAnimation(idleAnim)
        orbGlow.startAnimation(idleAnim)
    }

    fun startListening() {
        orbCore.clearAnimation()
        orbGlow.clearAnimation()
        orbCore.startAnimation(listenAnim)
        orbGlow.startAnimation(listenAnim)
    }

    override fun onDestroy() {
        if (::orbView.isInitialized) windowManager.removeView(orbView)
        super.onDestroy()
    }
// Call this frequently with 0..1 level
fun updateLevel(level: Float) {
    val scale = 1f + (level * 0.35f)
    orbCore.scaleX = scale
    orbCore.scaleY = scale
    orbGlow.alpha = (0.4f + level * 0.6f).coerceIn(0f, 1f)
}
}
