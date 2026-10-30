package local.hdhelper

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.atomic.AtomicReference

/**
 * The accessibility helper. It only ever receives events from the Home Depot app, and it only acts
 * while a job is pending: typing a sign-in verification code into the app's code field. A job has a
 * deadline, after which it is dropped quietly (the code stays on the clipboard). No gestures are
 * performed, and nothing is stored beyond a one-line status for the status window.
 */
class HdHelperService : AccessibilityService() {

    sealed class Job(val deadline: Long) {
        class FillCode(val code: String, deadline: Long) : Job(deadline)
        /** Test only (adb): empties the focused field again after a typing test. */
        class ClearField(deadline: Long) : Job(deadline)
    }

    companion object {
        const val TAG = "HdHelper"
        private const val HD = Target.HD_PACKAGE
        private const val MAX_NODES = 1500

        @Volatile private var instance: HdHelperService? = null
        private val pending = AtomicReference<Job?>(null)
        private val main = Handler(Looper.getMainLooper())

        fun isRunning(): Boolean = instance != null

        /** Hands the service a job. Tries at once and a few more times, because the field may already
         *  be on screen with no further event coming; events from the Home Depot app also trigger a try. */
        fun request(job: Job) {
            pending.set(job)
            val s = instance
            if (s == null) {
                Log.w(TAG, "the helper service is off, so the job cannot be carried out")
                return
            }
            for (delay in longArrayOf(300, 1500, 4000, 8000, 15000, 30000)) main.postDelayed({ s.tryNow() }, delay)
        }
    }

    override fun onServiceConnected() {
        instance = this
        Guard.ensure(this)
        Log.i(TAG, "helper service connected")
        tryNow()
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName == HD && pending.get() != null) tryNow()
    }

    private fun tryNow() {
        val job = pending.get() ?: return
        if (System.currentTimeMillis() > job.deadline) {
            pending.compareAndSet(job, null)
            Status.set(this, getString(R.string.status_gave_up))
            Log.w(TAG, "gave up: no suitable field appeared in the Home Depot app in time")
            return
        }
        val root = hdRoot() ?: return
        try {
            val done = when (job) {
                is Job.FillCode -> fill(root, job.code)
                is Job.ClearField -> clear(root)
            }
            if (done) pending.compareAndSet(job, null)
        } catch (e: RuntimeException) {
            Log.w(TAG, "the job failed on this try", e)
        }
    }

    /** The Home Depot app's window root: the active one if it is in front, else any of its windows. */
    private fun hdRoot(): AccessibilityNodeInfo? {
        val wins = try { windows } catch (e: RuntimeException) { null } ?: emptyList()
        var any: AccessibilityNodeInfo? = null
        for (w in wins) {
            val r = w.root ?: continue
            if (r.packageName == HD) {
                if (w.isActive || w.isFocused) return r
                if (any == null) any = r
            }
        }
        if (any != null) return any
        val r = rootInActiveWindow
        return if (r != null && r.packageName == HD) r else null
    }

    /** Every editable field on the screen, including fields inside the app's web pages. */
    private fun editables(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        var seen = 0
        while (stack.isNotEmpty() && seen < MAX_NODES) {
            val n = stack.removeLast()
            seen++
            val cls = n.className?.toString() ?: ""
            if ((n.isEditable || cls.contains("EditText")) && n.isVisibleToUser) out.add(n)
            for (i in 0 until n.childCount) n.getChild(i)?.let { stack.add(it) }
        }
        return out
    }

    private fun labels(n: AccessibilityNodeInfo): String =
        listOfNotNull(n.hintText, n.contentDescription, n.viewIdResourceName, n.text)
            .joinToString(" ").lowercase()

    private fun mentionsCode(n: AccessibilityNodeInfo): Boolean {
        val words = listOf("code", "otp", "passcode", "verification", "one-time", "one time")
        val parts = labels(n)
        return words.any { parts.contains(it) }
    }

    /** A search box, an email or a password field is never the place for a code. */
    private fun unsuitable(n: AccessibilityNodeInfo): Boolean {
        val parts = labels(n)
        return n.isPassword || listOf("search", "email", "password", "zip").any { parts.contains(it) }
    }

    private fun setText(n: AccessibilityNodeInfo, s: String): Boolean {
        n.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, s)
        if (!n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return false
        n.refresh()
        // Web fields may reformat what was typed (a gift card field shows "1234 56"), so compare the
        // letters and digits only.
        val now = (n.text?.toString() ?: "").filter { it.isLetterOrDigit() }
        return if (s.isEmpty()) now.isEmpty() else now.contains(s.filter { it.isLetterOrDigit() })
    }

    /** Types the code: one digit per box when the screen shows one box per digit, else into the code
     *  field, else into the focused field, else into the only field there is. Returns true when done. */
    private fun fill(root: AccessibilityNodeInfo, code: String): Boolean {
        val fields = editables(root)
        if (fields.isEmpty()) return false
        // One box per digit: as many empty boxes as digits, each a one-character box or named as a code box.
        if (fields.size == code.length && fields.all {
                (it.text?.toString() ?: "").isBlank() && (it.maxTextLength == 1 || mentionsCode(it)) }) {
            var all = true
            fields.forEachIndexed { i, f -> if (!setText(f, code[i].toString())) all = false }
            if (all) {
                Status.set(this, getString(R.string.status_filled, code))
                Log.i(TAG, "typed the code into ${fields.size} boxes")
                return true
            }
        }
        // The code field by name, else the field the app itself focused for the code. Never a search
        // box or a sign-in field, and never "the only field there is": a code goes nowhere rather
        // than into the wrong place.
        val target = fields.firstOrNull { mentionsCode(it) && !unsuitable(it) }
            ?: fields.firstOrNull { it.isFocused && !unsuitable(it) }
            ?: return false
        if (!setText(target, code)) return false
        Status.set(this, getString(R.string.status_filled, code))
        Log.i(TAG, "typed the code into the field")
        return true
    }

    private fun clear(root: AccessibilityNodeInfo): Boolean {
        val fields = editables(root)
        val f = fields.firstOrNull { it.isFocused } ?: fields.singleOrNull() ?: return false
        val ok = setText(f, "")
        if (ok) Log.i(TAG, "test: cleared the field again")
        return ok
    }
}
