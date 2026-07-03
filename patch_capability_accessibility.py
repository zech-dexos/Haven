content = open('app/src/main/java/com/dexos/haven/HavenCapabilityLayer.kt').read()

old = '''    private fun launchPackage(pkg: String): Boolean {
        return try {
            val i = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(i); true
        } catch (e: Exception) { false }
    }'''

new = '''    private fun launchPackage(pkg: String): Boolean {
        // Try AccessibilityService first — bypasses AppsFilter BLOCKED
        if (HavenAccessibilityService.openApp(pkg)) return true
        // Fallback to direct launch
        return try {
            val i = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(i); true
        } catch (e: Exception) { false }
    }'''

assert 'private fun launchPackage' in content, "launchPackage not found"
content = content.replace(old, new)
open('app/src/main/java/com/dexos/haven/HavenCapabilityLayer.kt', 'w').write(content)
print("Patched OK")
