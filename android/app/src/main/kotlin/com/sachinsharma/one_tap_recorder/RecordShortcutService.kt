package com.sachinsharma.one_tap_recorder

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Event-driven hardware shortcut. It never enables Touch Exploration, never
 * retrieves window content, and returns key events so normal volume behavior
 * continues.
 */
class RecordShortcutService : AccessibilityService() {
    private var firstVolumeUpAt = 0L
    private var lastTriggerAt = 0L

    private val buttonCallback = object : AccessibilityButtonController.AccessibilityButtonCallback() {
        override fun onClicked(controller: AccessibilityButtonController) = triggerRecorder()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            flags = flags or
                AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
                AccessibilityServiceInfo.FLAG_REQUEST_ACCESSIBILITY_BUTTON
        }
        accessibilityButtonController.registerAccessibilityButtonCallback(
            buttonCallback,
            Handler(Looper.getMainLooper())
        )
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return false
        val now = SystemClock.elapsedRealtime()
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                val interval = now - firstVolumeUpAt
                if (interval in 60..600 && now - lastTriggerAt > 1_200) {
                    lastTriggerAt = now
                    firstVolumeUpAt = 0L
                    triggerRecorder()
                } else {
                    firstVolumeUpAt = now
                }
            }
            else -> firstVolumeUpAt = 0L
        }
        return false
    }

    private fun triggerRecorder() {
        startActivity(
            Intent(this, ConsentActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        accessibilityButtonController.unregisterAccessibilityButtonCallback(buttonCallback)
        super.onDestroy()
    }
}
