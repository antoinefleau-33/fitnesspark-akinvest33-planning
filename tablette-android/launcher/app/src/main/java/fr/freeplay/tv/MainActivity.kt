package fr.freeplay.tv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.input.InputManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.InputDevice
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Écran d'accueil façon Android TV : grandes tuiles, navigation à la manette. */
class MainActivity : AppCompatActivity(), InputManager.InputDeviceListener {

    private lateinit var clock: TextView
    private lateinit var dateLabel: TextView
    private lateinit var greeting: TextView
    private lateinit var gamepadStatus: TextView
    private lateinit var grid: RecyclerView
    private lateinit var adapter: TileAdapter

    private val handler = Handler(Looper.getMainLooper())
    private var inputManager: InputManager? = null

    /** Rafraîchit l'heure à chaque minute qui change (et non toutes les secondes). */
    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = updateClock()
    }

    /** Une app installée ou désinstallée change les tuiles disponibles. */
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refreshTiles()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        clock = findViewById(R.id.clock)
        dateLabel = findViewById(R.id.date)
        greeting = findViewById(R.id.greeting)
        gamepadStatus = findViewById(R.id.gamepad_status)
        grid = findViewById(R.id.grid)

        adapter = TileAdapter(this) { tile -> onTileClicked(tile) }
        grid.layoutManager = GridLayoutManager(this, columnCount())
        grid.adapter = adapter
        grid.setHasFixedSize(true)

        inputManager = getSystemService(Context.INPUT_SERVICE) as? InputManager

        // Un écran d'accueil ne se quitte pas : le retour arrière ne fait rien.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        GamepadKeeperService.start(this)
    }

    /** 3 colonnes en petit écran, 4 dès que la largeur le permet. */
    private fun columnCount(): Int {
        val widthDp = resources.configuration.screenWidthDp
        return if (widthDp >= 900) 4 else 3
    }

    override fun onResume() {
        super.onResume()
        updateClock()
        refreshTiles()
        updateGamepadStatus()

        registerReceiver(timeReceiver, IntentFilter(Intent.ACTION_TIME_TICK))
        val pkgFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        registerReceiver(packageReceiver, pkgFilter)
        inputManager?.registerInputDeviceListener(this, handler)
    }

    override fun onPause() {
        super.onPause()
        runCatching { unregisterReceiver(timeReceiver) }
        runCatching { unregisterReceiver(packageReceiver) }
        inputManager?.unregisterInputDeviceListener(this)
    }

    private fun updateClock() {
        val now = Date()
        clock.text = SimpleDateFormat("HH:mm", Locale.FRANCE).format(now)
        dateLabel.text = SimpleDateFormat("EEEE d MMMM", Locale.FRANCE)
            .format(now)
            .replaceFirstChar { it.uppercase(Locale.FRANCE) }
        greeting.setText(greetingRes())
    }

    private fun greetingRes(): Int = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> R.string.greeting_morning
        in 12..17 -> R.string.greeting_afternoon
        else -> R.string.greeting_evening
    }

    private fun refreshTiles() {
        adapter.submit(Tiles.homeTiles())
    }

    // ----- État de la manette -------------------------------------------------

    private fun updateGamepadStatus() {
        val pad = connectedGamepadName()
        if (pad != null) {
            gamepadStatus.text = getString(R.string.gamepad_connected, pad)
            gamepadStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
        } else {
            gamepadStatus.setText(R.string.gamepad_absent)
            gamepadStatus.setTextColor(ContextCompat.getColor(this, R.color.text_dim))
        }
    }

    /** Nom de la première manette détectée, ou null s'il n'y en a aucune. */
    private fun connectedGamepadName(): String? {
        for (id in InputDevice.getDeviceIds()) {
            val device = InputDevice.getDevice(id) ?: continue
            val sources = device.sources
            val isGamepad = sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD
            val isJoystick = sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
            if ((isGamepad || isJoystick) && !device.isVirtual) {
                return device.name
            }
        }
        return null
    }

    override fun onInputDeviceAdded(deviceId: Int) = updateGamepadStatus()
    override fun onInputDeviceRemoved(deviceId: Int) = updateGamepadStatus()
    override fun onInputDeviceChanged(deviceId: Int) = updateGamepadStatus()

    // ----- Actions ------------------------------------------------------------

    private fun onTileClicked(tile: Tile) {
        when (tile.action) {
            Tile.Action.ALL_APPS ->
                startActivity(Intent(this, AllAppsActivity::class.java))

            Tile.Action.SETTINGS ->
                openSystemScreen(Settings.ACTION_SETTINGS)

            Tile.Action.BLUETOOTH_SETTINGS ->
                openSystemScreen(Settings.ACTION_BLUETOOTH_SETTINGS, Settings.ACTION_SETTINGS)

            Tile.Action.LAUNCH_APP -> {
                val pkg = tile.packageName ?: return
                val intent = Tiles.launchIntentFor(this, pkg)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    Tiles.safeStart(this, intent)
                } else {
                    // App absente : on propose de l'installer.
                    Tiles.openStore(this, pkg)
                }
            }
        }
    }

    private fun openSystemScreen(vararg actions: String) {
        for (action in actions) {
            val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Tiles.safeStart(this, intent)) return
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
            updateGamepadStatus()
        }
    }

    /** Plein écran immersif : rendu "box TV", sans barres Android. */
    private fun hideSystemBars() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams
                        .LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }
}
