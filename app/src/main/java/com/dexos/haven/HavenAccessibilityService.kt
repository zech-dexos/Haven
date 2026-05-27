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
            val sb = StringBuilder()
            if (node.text != null) sb.append(node.text).append(" ")
            if (node.contentDescription != null) sb.append(node.contentDescription).append(" ")
            for (i in 0 until node.childCount) {
                sb.append(extractText(node.getChild(i)))
            }
            return sb.toString().trim()
        }
        
        fun openApp(packageName: String): Boolean {
            val context = instance ?: return false
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            return if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else false
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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        instance = null
    }
    
    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
