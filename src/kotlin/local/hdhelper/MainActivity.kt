package local.hdhelper

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateFormat
import android.util.TypedValue
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** The status window: whether the helper is on, whether it can heal itself, and the last action. */
class MainActivity : Activity() {
    private lateinit var service: TextView
    private lateinit var fix: Button
    private lateinit var perm: TextView
    private lateinit var app: TextView
    private lateinit var last: TextView
    private val main = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            refresh()
            main.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(pad, pad, pad, pad)

        root.addView(text(getString(R.string.about), 15f))
        service = text("", 16f); root.addView(service)
        fix = Button(this)
        fix.text = getString(R.string.button_fix)
        fix.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        root.addView(fix)
        perm = text("", 15f); root.addView(perm)
        app = text("", 15f); root.addView(app)
        last = text("", 15f); root.addView(last)

        val history = Button(this)
        history.text = getString(R.string.button_history)
        history.setOnClickListener {
            startActivity(Intent(this, OpenActivity::class.java)
                .putExtra(OpenActivity.EXTRA_SUBJECT, "Update on your order"))
        }
        root.addView(history)
        val open = Button(this)
        open.text = getString(R.string.button_open)
        open.setOnClickListener { startActivity(Intent(this, OpenActivity::class.java)) }
        root.addView(open)

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
        Guard.ensure(this)
    }

    private fun text(s: String, size: Float): TextView {
        val t = TextView(this)
        t.text = s
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        val pad = (8 * resources.displayMetrics.density).toInt()
        t.setPadding(0, pad, 0, pad)
        return t
    }

    override fun onResume() {
        super.onResume()
        main.post(tick)
    }

    override fun onPause() {
        main.removeCallbacks(tick)
        super.onPause()
    }

    private fun refresh() {
        val on = Guard.isEnabled(this) && HdHelperService.isRunning()
        service.text = getString(if (on) R.string.service_on else R.string.service_off)
        fix.visibility = if (on) android.view.View.GONE else android.view.View.VISIBLE
        perm.text = getString(if (Guard.canWrite(this)) R.string.perm_on else R.string.perm_off)
        val installed = try {
            packageManager.getPackageInfo(Target.HD_PACKAGE, 0); true
        } catch (e: PackageManager.NameNotFoundException) { false }
        app.text = getString(if (installed) R.string.hd_installed else R.string.hd_missing)
        val l = Status.last(this)
        last.text = if (l == null) getString(R.string.last_none)
            else getString(R.string.last_action, l, DateFormat.getTimeFormat(this).format(Status.time(this)))
    }
}
