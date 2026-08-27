package fr.freeplay.tv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

/**
 * Garde la manette utilisable en permanence.
 *
 * Ce qu'un service d'application peut réellement faire sous Android :
 *  - empêcher la tablette de couper le Bluetooth ou de s'endormir (les deux
 *    causes habituelles de déconnexion) ;
 *  - rester en vie grâce à une notification permanente, donc échapper au mode
 *    "Doze" qui gèle les applications en arrière-plan ;
 *  - rallumer le Bluetooth s'il a été coupé (possible jusqu'à Android 12 ;
 *    Android 13+ réserve cette action à l'utilisateur).
 *
 * Ce qu'aucune application ne peut faire : forcer une manette éteinte à se
 * rallumer. La reconnexion est toujours déclenchée par la manette elle-même
 * (bouton Xbox / PS). Ce service fait en sorte qu'elle aboutisse instantanément.
 */
class GamepadKeeperService : android.app.Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(
                        BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.STATE_OFF
                    )
                    if (state == BluetoothAdapter.STATE_OFF) ensureBluetoothOn()
                    updateNotification()
                }
                BluetoothDevice.ACTION_ACL_CONNECTED,
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> updateNotification()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        registerReceiver(bluetoothReceiver, IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        })

        acquireWakeLock()
        ensureBluetoothOn()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureBluetoothOn()
        updateNotification()
        // START_STICKY : si Android tue le service, il le relance.
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(bluetoothReceiver) }
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
        super.onDestroy()
    }

    /**
     * WakeLock partiel : maintient le processeur assez actif pour que la pile
     * Bluetooth réponde immédiatement. L'écran, lui, peut s'éteindre normalement.
     */
    private fun acquireWakeLock() {
        val power = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = runCatching {
            power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
                setReferenceCounted(false)
                acquire()
            }
        }.getOrNull()
    }

    private fun adapter(): BluetoothAdapter? =
        (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            ?: BluetoothAdapter.getDefaultAdapter()

    /** Rallume le Bluetooth s'il s'est coupé (sans effet sur Android 13+). */
    private fun ensureBluetoothOn() {
        val adapter = adapter() ?: return
        if (adapter.isEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
        runCatching {
            @Suppress("DEPRECATION")
            adapter.enable()
        }
    }

    /** Manettes déjà appairées et actuellement reliées à la tablette. */
    private fun connectedGamepads(): List<String> {
        val adapter = adapter() ?: return emptyList()
        return runCatching {
            adapter.bondedDevices
                .filter { device ->
                    val cls = device.bluetoothClass?.deviceClass ?: 0
                    cls == PERIPHERAL_GAMEPAD || cls == PERIPHERAL_JOYSTICK
                }
                .map { it.name ?: getString(R.string.gamepad_unknown) }
        }.getOrDefault(emptyList())
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_gamepad),
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = getString(R.string.channel_gamepad_desc)
            setShowBadge(false)
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val paired = connectedGamepads()
        val text = when {
            adapter()?.isEnabled != true -> getString(R.string.keeper_bt_off)
            paired.isEmpty() -> getString(R.string.keeper_no_pad)
            else -> getString(R.string.keeper_ready, paired.first())
        }

        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else
                PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.keeper_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_gamepad)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(open)
            .build()
    }

    private fun updateNotification() {
        runCatching {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIFICATION_ID, buildNotification())
        }
    }

    companion object {
        private const val CHANNEL_ID = "freeplay_gamepad"
        private const val NOTIFICATION_ID = 42
        private const val WAKELOCK_TAG = "freeplay:gamepad"

        // Codes de classe Bluetooth (BluetoothClass.Device.PERIPHERAL_*)
        private const val PERIPHERAL_GAMEPAD = 0x0508
        private const val PERIPHERAL_JOYSTICK = 0x0504

        fun start(context: Context) {
            val intent = Intent(context, GamepadKeeperService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }
    }
}
