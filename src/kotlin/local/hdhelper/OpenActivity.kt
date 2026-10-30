package local.hdhelper

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast

/**
 * The invisible entry point a Home Depot notification opens. It works out where the email points
 * (Target), opens the Home Depot app there, and finishes at once.
 *
 * Only Notification Keeper (and adb, for testing) may hand it an email's subject and text. Any other
 * caller just opens the Home Depot app, so nobody can make this helper copy a code or open a page.
 */
class OpenActivity : Activity() {

    companion object {
        const val EXTRA_SUBJECT = "subject"
        const val EXTRA_TEXT = "text"
        /** Test only (adb): "clear" empties the focused field in the Home Depot app again. */
        const val EXTRA_OP = "op"
        private val TRUSTED = setOf("local.notifkeeper", "com.android.shell", "local.hdhelper")
        private const val CODE_DEADLINE_MS = 45_000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Guard.ensure(this)
        // Who opened us. Android tells an activity its launching package two ways: the newer call
        // (hidden when the launcher is not visible to us, so the adb shell from another user shows as
        // nothing) and the referrer, which the system fills with the launching package unless the
        // caller put its own referrer into the intent, which only an impostor would do.
        val spoofed = intent.hasExtra(Intent.EXTRA_REFERRER) || intent.hasExtra(Intent.EXTRA_REFERRER_NAME)
        val launched = if (Build.VERSION.SDK_INT >= 34) launchedFromPackage else null
        val ref = referrer?.toString()
        val from = launched ?: ref?.takeIf { it.startsWith("android-app://") }?.removePrefix("android-app://")
        val trusted = !spoofed && from != null && from in TRUSTED
        if (!trusted) Log.w(HdHelperService.TAG, "opened by " + from + " (referrer " + ref + ", spoofed " + spoofed
                + "), which is not trusted: only opening the Home Depot app")
        val subject = intent.getCharSequenceExtra(EXTRA_SUBJECT)
        val text = intent.getCharSequenceExtra(EXTRA_TEXT)
        val op = intent.getStringExtra(EXTRA_OP)
        try {
            if (trusted && op == "clear") {
                HdHelperService.request(HdHelperService.Job.ClearField(System.currentTimeMillis() + 20_000L))
                openApp()
            } else {
                go(if (trusted) Target.classify(subject, text) else Target.App)
            }
        } catch (e: ActivityNotFoundException) {
            Log.w(HdHelperService.TAG, "could not open the Home Depot app", e)
            openApp()
        } catch (e: SecurityException) {
            Log.w(HdHelperService.TAG, "could not open the Home Depot app", e)
        }
        finish()
    }

    private fun go(target: Target) {
        when (target) {
            is Target.Code -> {
                getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("Home Depot code", target.code))
                val typing = HdHelperService.isRunning()
                if (typing) HdHelperService.request(
                    HdHelperService.Job.FillCode(target.code, System.currentTimeMillis() + CODE_DEADLINE_MS))
                Status.set(this, getString(R.string.status_code, target.code))
                Log.i(HdHelperService.TAG, "verification code: copied it" + (if (typing) " and asked the helper to type it" else ""))
                Toast.makeText(this, getString(
                    if (typing) R.string.toast_code_typing else R.string.toast_code_copied, target.code), Toast.LENGTH_LONG).show()
                openApp()
            }
            is Target.Order -> {
                Status.set(this, getString(R.string.status_order, target.number))
                Log.i(HdHelperService.TAG, "opening order " + target.number)
                view(Target.ORDER_DETAILS + target.number)
            }
            Target.PurchaseHistory -> {
                Status.set(this, getString(R.string.status_history))
                Log.i(HdHelperService.TAG, "no order number in the notification: opening the purchase history")
                view(Target.PURCHASE_HISTORY)
            }
            Target.App -> {
                Status.set(this, getString(R.string.status_app))
                Log.i(HdHelperService.TAG, "not an order or a code: opening the Home Depot app")
                openApp()
            }
        }
    }

    /** Opens a homedepot.com page inside the Home Depot app (it handles these links natively). */
    private fun view(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .setPackage(Target.HD_PACKAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Opens the Home Depot app the way its launcher icon does (reusing its window), else homedepot.com. */
    private fun openApp() {
        val launch = packageManager.getLaunchIntentForPackage(Target.HD_PACKAGE)
        if (launch != null) {
            startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Target.HOME_PAGE)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
