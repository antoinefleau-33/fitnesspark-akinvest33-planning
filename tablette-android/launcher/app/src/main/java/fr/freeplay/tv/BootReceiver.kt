package fr.freeplay.tv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Relance le service manette dès le démarrage de la tablette. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            GamepadKeeperService.start(context)
        }
    }
}
