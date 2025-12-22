package com.romeo.jarvis.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class JarvisAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        // Register this instance so JarvisService can call it
        JarvisAccessibilityHolder.service = this

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 50
            flags =
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don’t need to react to events right now.
        // JarvisService actively calls actions.
    }

    override fun onInterrupt() {}

    // ========================= GLOBAL ACTIONS =========================

    fun back() {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun home() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun recents() {
        performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    /**
     * Close current app:
     * Open recents. (User can say "close app" again to tap)
     * Advanced swipe-to-dismiss can be added device-wise.
     */
    fun closeCurrentApp() {
        recents()
    }

    // ========================= GESTURES =========================

    /**
     * Tap anywhere on screen by coordinates
     * Example voice: "tap center", "tap top right" (map coords in JarvisService)
     */
    fun tap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        dispatchGesture(gesture, null, null)
    }

    /**
     * Scroll down the current focused window
     */
    fun scrollDown() {
        rootInActiveWindow?.performAction(
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        )
    }

    /**
     * Scroll up the current focused window
     */
    fun scrollUp() {
        rootInActiveWindow?.performAction(
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        )
    }

    // ========================= SCREEN READING =========================

    /**
     * Read visible screen text (best-effort).
     * Returns concatenated text of visible nodes.
     */
    fun readScreen(): String {
        val root = rootInActiveWindow ?: return ""
        return traverse(root).trim()
    }

    private fun traverse(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val sb = StringBuilder()

        node.text?.let {
            if (it.isNotBlank()) sb.append(it).append(" ")
        }
        node.contentDescription?.let {
            if (it.isNotBlank()) sb.append(it).append(" ")
        }

        for (i in 0 until node.childCount) {
            sb.append(traverse(node.getChild(i)))
        }
        return sb.toString()
    }

    // ========================= NODE CLICK (ADVANCED) =========================

    /**
     * Click first node containing given text.
     * Example: "tap OK", "press allow"
     */
    fun clickByText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(text)
        if (!nodes.isNullOrEmpty()) {
            for (n in nodes) {
                if (n.isClickable) {
                    n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return true
                }
                // bubble up to clickable parent
                var p = n.parent
                while (p != null) {
                    if (p.isClickable) {
                        p.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        return true
                    }
                    p = p.parent
                }
            }
        }
        return false
    }

    // ========================= CLEANUP =========================

    override fun onDestroy() {
        if (JarvisAccessibilityHolder.service === this) {
            JarvisAccessibilityHolder.service = null
        }
        super.onDestroy()
    }
}

/**
 * Simple holder so JarvisService can call Accessibility actions.
 * (Yes, Android makes us do this dance.)
 */
object JarvisAccessibilityHolder {
    var service: JarvisAccessibilityService? = null
}