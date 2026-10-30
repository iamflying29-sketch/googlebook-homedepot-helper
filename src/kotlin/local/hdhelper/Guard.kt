package local.hdhelper

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.util.Log

/**
 * Keeps the helper's accessibility service switched on. Android turns an accessibility service off
 * whenever its app is updated or reinstalled. With the WRITE_SECURE_SETTINGS permission (granted once
 * over adb) the helper puts itself back into the enabled list, leaving every other service untouched.
 */
object Guard {
    private const val ENABLED = "enabled_accessibility_services"
    private const val ON = "accessibility_enabled"

    fun component(c: Context): String = ComponentName(c, HdHelperService::class.java).flattenToString()

    fun isEnabled(c: Context): Boolean {
        val me = component(c)
        val cur = Settings.Secure.getString(c.contentResolver, ENABLED) ?: return false
        return cur.split(':').any { it == me }
    }

    fun canWrite(c: Context): Boolean =
        c.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun ensure(c: Context) {
        try {
            if (isEnabled(c)) return
            val me = component(c)
            val cur = Settings.Secure.getString(c.contentResolver, ENABLED)
            val next = if (cur.isNullOrEmpty()) me else "$cur:$me"
            Settings.Secure.putString(c.contentResolver, ENABLED, next)
            Settings.Secure.putInt(c.contentResolver, ON, 1)
            Log.i(HdHelperService.TAG, "guard: the helper service was off, turned it back on")
        } catch (e: SecurityException) {
            Log.w(HdHelperService.TAG, "guard: no WRITE_SECURE_SETTINGS permission, so the helper cannot turn itself on")
        } catch (e: RuntimeException) {
            Log.w(HdHelperService.TAG, "guard: could not check the accessibility setting", e)
        }
    }
}
