package com.pulsehud

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class ClickAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) {
            MonitorState.recordClick(this)
        }
    }

    override fun onInterrupt() = Unit
}
