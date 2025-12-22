package com.romeo.jarvis.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class JarvisAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    // -------- TAP ANYWHERE --------
    fun tap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        dispatchGesture(gesture, null, null)
    }

    // -------- BACK / HOME / RECENTS --------
    fun back() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun home() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun recents() = performGlobalAction(GLOBAL_ACTION_RECENTS)

    // -------- CLOSE CURRENT APP --------
    fun closeCurrentApp() {
        recents()
        // user can say "close app" again → tap center
    }

    // -------- SCROLL --------
    fun scrollDown() {
        rootInActiveWindow?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    fun scrollUp() {
        rootInActiveWindow?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }

    // -------- READ SCREEN --------
    fun readScreen(): String {
        val root = rootInActiveWindow ?: return ""
        return traverse(root)
    }

    private fun traverse(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val sb = StringBuilder()
        node.text?.let { sb.append(it).append(" ") }
        for (i in 0 until node.childCount) {
            sb.append(traverse(node.getChild(i)))
        }
        return sb.toString()
    }
}