package com.sachinsharma.one_tap_recorder

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent

/**
 * Event-driven global gesture trigger. It deliberately does not request
 * window-content access, inspect UI nodes, poll sensors, or hold a wake lock.
 */
class GestureAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        if (Build.VERSION.SDK_INT >= 30) {
            serviceInfo = serviceInfo.apply {
                flags = flags or
                    AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE or
                    AccessibilityServiceInfo.FLAG_REQUEST_MULTI_FINGER_GESTURES
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onGesture(gestureId: Int): Boolean {
        if (Build.VERSION.SDK_INT >= 30 && gestureId == GESTURE_2_FINGER_DOUBLE_TAP) {
            startActivity(
                Intent(this, ConsentActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            return true
        }
        return super.onGesture(gestureId)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit
}
