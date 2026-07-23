package com.dexos.haven

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class HavenAccessibilityService : AccessibilityService() {

    companion object {
        var instance: HavenAccessibilityService? = null
        
        fun readScreen(): String {
            val root = instance?.rootInActiveWindow ?: return "Cannot read screen"
            return extractText(root)
        }
        
        fun extractText(node: AccessibilityNodeInfo?): String {
            if (node == null) return ""
            // Never read password fields -- critical for elderly users especially,
            // who may have banking/account apps open with saved credentials visible.
            if (node.isPassword) return ""
            val sb = StringBuilder()
            if (node.text != null) sb.append(node.text).append(" ")
            if (node.contentDescription != null) sb.append(node.contentDescription).append(" ")
            for (i in 0 until node.childCount) {
                sb.append(extractText(node.getChild(i)))
            }
            return sb.toString().trim()
        }

        // Current screen's app package + visible text, updated live as the screen changes.
        // Kalimi's conversation loop reads this alongside whatever the user just said.
        @Volatile var currentScreenPackage: String = ""
        @Volatile var currentScreenText: String = ""
        
        fun openApp(packageName: String): Boolean {
            val ctx = instance ?: return false
            return try {
                // Try direct launch first
                val intent = ctx.packageManager.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(intent)
                    return true
                }
                // Fallback: ACTION_MAIN with CATEGORY_LAUNCHER
                val fallback = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setPackage(packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(fallback)
                true
            } catch (e: Exception) {
                // Last resort: open via market URI
                try {
                    val market = Intent(Intent.ACTION_VIEW).apply {
                        data = android.net.Uri.parse("market://details?id=$packageName")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    ctx.startActivity(market)
                    true
                } catch (e2: Exception) {
                    false
                }
            }
        }
        
        fun performBack(): Boolean {
            return instance?.performGlobalAction(GLOBAL_ACTION_BACK) ?: false
        }
        
        fun performHome(): Boolean {
            return instance?.performGlobalAction(GLOBAL_ACTION_HOME) ?: false
        }
        
        fun clickNode(text: String): Boolean {
            val root = instance?.rootInActiveWindow ?: return false
            return findAndClick(root, text)
        }
        
        fun findAndClick(node: AccessibilityNodeInfo?, text: String): Boolean {
            if (node == null) return false
            if (node.text?.toString()?.contains(text, ignoreCase = true) == true ||
                node.contentDescription?.toString()?.contains(text, ignoreCase = true) == true) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
            for (i in 0 until node.childCount) {
                if (findAndClick(node.getChild(i), text)) return true
            }
            return false
        }
    }

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val relevant = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        if (!relevant) return

        // Don't bother re-reading Haven's own screen -- only care about OTHER apps
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        val root = rootInActiveWindow ?: return
        currentScreenPackage = pkg
        currentScreenText = extractText(root).take(2000) // cap length, screens can be huge
    }

    override fun onInterrupt() {
        instance = null
    }
    
    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
