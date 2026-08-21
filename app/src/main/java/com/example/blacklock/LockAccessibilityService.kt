package com.example.blacklock
import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class LockAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }
    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
    }
    companion object {
        var instance: LockAccessibilityService? = null
            private set
        fun lockScreen(): Boolean {
            return instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) ?: false
        }
    }
}
