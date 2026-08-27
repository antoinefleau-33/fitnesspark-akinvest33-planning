package fr.freeplay.tv

import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController

/**
 * Vérifie que les écrans se construisent réellement sans planter : c'est ce qui
 * casse en premier sur un launcher (ressource manquante, layout invalide,
 * service qui refuse de démarrer).
 */
@RunWith(RobolectricTestRunner::class)
class LauncherTest {

    @Test
    fun `l'accueil se lance sans planter`() {
        val controller: ActivityController<MainActivity> =
            Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        assertNotNull("L'activité d'accueil doit exister", activity)
        assertTrue("L'accueil ne doit pas se fermer tout seul", !activity.isFinishing)

        controller.pause().resume()
        controller.destroy()
    }

    @Test
    fun `l'accueil affiche toutes les tuiles`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val grid = activity.findViewById<RecyclerView>(R.id.grid)

        assertNotNull("La grille de tuiles doit être présente", grid)
        assertEquals(
            "Toutes les tuiles doivent être affichées",
            Tiles.homeTiles().size,
            grid.adapter?.itemCount
        )
    }

    @Test
    fun `l'heure et la date sont renseignées`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        val clock = activity.findViewById<TextView>(R.id.clock)
        val date = activity.findViewById<TextView>(R.id.date)
        val greeting = activity.findViewById<TextView>(R.id.greeting)

        assertTrue("L'heure doit être au format HH:mm", clock.text.matches(Regex("\\d{2}:\\d{2}")))
        assertTrue("La date ne doit pas être vide", date.text.isNotBlank())
        assertTrue("La salutation ne doit pas être vide", greeting.text.isNotBlank())
    }

    @Test
    fun `le retour arriere ne quitte pas l'accueil`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        activity.onBackPressedDispatcher.onBackPressed()

        assertTrue(
            "Un écran d'accueil ne doit jamais se fermer sur le bouton retour",
            !activity.isFinishing
        )
    }

    @Test
    fun `la liste de toutes les applications s'ouvre`() {
        val controller = Robolectric.buildActivity(AllAppsActivity::class.java).setup()
        val activity = controller.get()

        val grid = activity.findViewById<RecyclerView>(R.id.app_grid)
        assertNotNull("La grille des applications doit être présente", grid)
        assertNotNull("Un adaptateur doit être branché", grid.adapter)

        controller.destroy()
    }

    @Test
    fun `le service manette demarre et s'arrete proprement`() {
        val controller = Robolectric.buildService(GamepadKeeperService::class.java)

        controller.create().startCommand(0, 0)
        assertNotNull("Le service doit être créé", controller.get())

        controller.destroy()
    }

    @Test
    fun `chaque tuile est correctement definie`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        for (tile in Tiles.homeTiles()) {
            assertTrue("Le titre d'une tuile ne doit jamais être vide", tile.title.isNotBlank())
            assertTrue("Le sous-titre d'une tuile ne doit jamais être vide", tile.subtitle.isNotBlank())

            // Une icône de secours doit exister quand l'app n'est pas installée.
            assertNotNull(
                "L'icône de secours de « ${tile.title} » est introuvable",
                context.resources.getDrawable(tile.fallbackIcon, null)
            )

            // Seules les tuiles "lancer une app" ont besoin d'un nom de paquet.
            if (tile.action == Tile.Action.LAUNCH_APP) {
                assertNotNull(
                    "La tuile « ${tile.title} » doit cibler une application",
                    tile.packageName
                )
            }
        }
    }

    @Test
    fun `une application absente est detectee comme telle`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        assertTrue(
            "Un paquet inexistant ne doit pas être considéré comme installé",
            !Tiles.isInstalled(context, "fr.exemple.application.inexistante")
        )
        assertTrue(
            "Une tuile sans paquet (Réglages…) est toujours disponible",
            Tiles.isInstalled(context, null)
        )
    }
}
