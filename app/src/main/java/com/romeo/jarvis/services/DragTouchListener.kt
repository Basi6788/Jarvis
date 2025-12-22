package com.romeo.jarvis.services

import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

class DragTouchListener(
    private val params: WindowManager.LayoutParams,
    private val windowManager: WindowManager
) : View.OnTouchListener {

    private var lastX = 0f
    private var lastY = 0f

    override fun onTouch(v: View, e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = e.rawX
                lastY = e.rawY
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                params.x += (lastX - e.rawX).toInt()
                params.y += (e.rawY - lastY).toInt()
                windowManager.updateViewLayout(v, params)
                lastX = e.rawX
                lastY = e.rawY
                return true
            }
        }
        return false
    }
}