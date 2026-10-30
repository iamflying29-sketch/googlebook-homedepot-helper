package local.hdhelper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** After a restart or an update of this app: make sure the helper service is still on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Guard.ensure(context)
    }
}
