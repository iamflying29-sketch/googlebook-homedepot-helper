package local.hdhelper

import android.content.Context

/** One line about the last thing the helper did, for the status window. */
object Status {
    private const val PREFS = "status"

    fun set(c: Context, message: String) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("last", message).putLong("when", System.currentTimeMillis()).apply()
    }

    fun last(c: Context): String? = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("last", null)

    fun time(c: Context): Long = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("when", 0L)
}
