package com.romeo.jarvis.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class JarvisAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        val info = AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.notificationTimeout = 50
        info.flags =
            AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS

        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Screen read ke liye
    }

    override fun onInterrupt() {}

    // 🔥 READ CURRENT SCREEN TEXT
    fun readScreen(): String {
        val root = rootInActiveWindow ?: return ""
        return traverseNode(root)
    }

    private fun traverseNode(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val sb = StringBuilder()

        if (!node.text.isNullOrEmpty()) {
            sb.append(node.text).append(" ")
        }

        for (i in 0 until node.childCount) {
            sb.append(traverseNode(node.getChild(i)))
        }
        return sb.toString()
    }
}