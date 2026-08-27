package fr.freeplay.tv

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast

/**
 * Une tuile de l'écran d'accueil : une application vedette (TV, Netflix…),
 * ou une action interne du launcher (toutes les apps, réglages).
 */
data class Tile(
    val title: String,
    val subtitle: String,
    val packageName: String?,
    val colorStart: Int,
    val colorEnd: Int,
    val fallbackIcon: Int,
    val action: Action = Action.LAUNCH_APP
) {
    enum class Action { LAUNCH_APP, ALL_APPS, SETTINGS, BLUETOOTH_SETTINGS }
}

object Tiles {

    /** Les applis vedettes, dans l'ordre d'affichage sur l'accueil. */
    fun homeTiles(): List<Tile> = listOf(
        Tile(
            title = "TV",
            subtitle = "Chaînes Freebox, replay",
            packageName = "net.oqee.androidmobile",
            colorStart = 0xFFE30613.toInt(),
            colorEnd = 0xFF8B0000.toInt(),
            fallbackIcon = R.drawable.ic_tv
        ),
        Tile(
            title = "Netflix",
            subtitle = "Films et séries",
            packageName = "com.netflix.mediaclient",
            colorStart = 0xFFE50914.toInt(),
            colorEnd = 0xFF5C0009.toInt(),
            fallbackIcon = R.drawable.ic_movie
        ),
        Tile(
            title = "Disney+",
            subtitle = "Disney, Marvel, Star Wars",
            packageName = "com.disney.disneyplus",
            colorStart = 0xFF0C1B57.toInt(),
            colorEnd = 0xFF040A24.toInt(),
            fallbackIcon = R.drawable.ic_movie
        ),
        Tile(
            title = "Prime Video",
            subtitle = "Amazon Prime Video",
            packageName = "com.amazon.avod.thirdpartyclient",
            colorStart = 0xFF00A8E1.toInt(),
            colorEnd = 0xFF00456B.toInt(),
            fallbackIcon = R.drawable.ic_movie
        ),
        Tile(
            title = "Jeux",
            subtitle = "Steam Link — jeux du PC",
            packageName = "com.valvesoftware.steamlink",
            colorStart = 0xFF1B2838.toInt(),
            colorEnd = 0xFF0B0F14.toInt(),
            fallbackIcon = R.drawable.ic_gamepad
        ),
        Tile(
            title = "Vidéos",
            subtitle = "VLC — fichiers et clés USB",
            packageName = "org.videolan.vlc",
            colorStart = 0xFFFF8800.toInt(),
            colorEnd = 0xFF8A4000.toInt(),
            fallbackIcon = R.drawable.ic_movie
        ),
        Tile(
            title = "Manette",
            subtitle = "Appairage Bluetooth",
            packageName = null,
            colorStart = 0xFF2E7D32.toInt(),
            colorEnd = 0xFF13401A.toInt(),
            fallbackIcon = R.drawable.ic_gamepad,
            action = Tile.Action.BLUETOOTH_SETTINGS
        ),
        Tile(
            title = "Toutes les apps",
            subtitle = "Tout ce qui est installé",
            packageName = null,
            colorStart = 0xFF37474F.toInt(),
            colorEnd = 0xFF141B1F.toInt(),
            fallbackIcon = R.drawable.ic_apps,
            action = Tile.Action.ALL_APPS
        ),
        Tile(
            title = "Réglages",
            subtitle = "Wi-Fi, écran, système",
            packageName = null,
            colorStart = 0xFF455A64.toInt(),
            colorEnd = 0xFF1A2226.toInt(),
            fallbackIcon = R.drawable.ic_settings,
            action = Tile.Action.SETTINGS
        )
    )

    fun isInstalled(context: Context, packageName: String?): Boolean {
        if (packageName == null) return true
        return launchIntentFor(context, packageName) != null
    }

    /**
     * Intent de lancement d'une app : on privilégie l'interface "TV" (leanback)
     * quand l'application en propose une, sinon son interface normale.
     */
    fun launchIntentFor(context: Context, packageName: String): Intent? {
        val pm = context.packageManager
        return pm.getLeanbackLaunchIntentForPackageCompat(packageName)
            ?: pm.getLaunchIntentForPackage(packageName)
    }

    private fun PackageManager.getLeanbackLaunchIntentForPackageCompat(pkg: String): Intent? =
        try {
            getLeanbackLaunchIntentForPackage(pkg)
        } catch (e: Exception) {
            null
        }

    /** Ouvre la fiche Play Store d'une app absente (ou le site web en secours). */
    fun openStore(context: Context, packageName: String) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val web = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (!safeStart(context, market) && !safeStart(context, web)) {
            Toast.makeText(context, R.string.store_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    fun safeStart(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        false
    }
}
