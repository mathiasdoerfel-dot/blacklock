package de.matze.blackbox.tools.screenlock

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager

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

        val isRunning: Boolean
            get() = instance != null

        fun isServiceOrSettingActive(context: Context): Boolean {
            if (instance != null) return true
            return try {
                val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                    ?: return false
                val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                val expectedComponent = ComponentName(context, LockAccessibilityService::class.java)
                enabledServices.any {
                    val serviceInfo = it.resolveInfo?.serviceInfo
                    serviceInfo != null &&
                        serviceInfo.packageName == expectedComponent.packageName &&
                        serviceInfo.name == expectedComponent.className
                }
            } catch (e: Exception) {
                false
            }
        }

        fun lockScreen(): Boolean {
            return instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) ?: false
        }
    }
}
