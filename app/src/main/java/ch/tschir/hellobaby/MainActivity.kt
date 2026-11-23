package ch.tschir.hellobaby

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import ch.tschir.hellobaby.data.ApiService
import ch.tschir.hellobaby.data.AppSettings
import ch.tschir.hellobaby.data.MedienClient
import coil3.SingletonImageLoader
import ch.tschir.hellobaby.ui.CreateScreen
import ch.tschir.hellobaby.ui.GalleryScreen
import ch.tschir.hellobaby.ui.HauptScreen
import ch.tschir.hellobaby.ui.MhTheme
import ch.tschir.hellobaby.ui.PapierkorbScreen
import ch.tschir.hellobaby.ui.SettingsScreen
import ch.tschir.hellobaby.ui.VideoPlayerScreen

/** Reiter der Hauptansicht – wie im Web-HelloBaby. */
enum class Reiter(val titel: String) {
    START("Start"),
    TAG("Tag"),
    MONAT("Monat"),
    FAVORITEN("Favoriten"),
    GALERIE("Galerie"),
}

/** Ziele der einfachen, eigenen Navigation (Back-Stack als Liste). */
sealed interface Ziel {
    /** Hauptansicht mit Reitern; [tag] wählt in „Tag“ ein Datum vor. */
    data class Haupt(val reiter: Reiter = Reiter.START, val tag: String? = null) : Ziel
    data class Create(val initialDate: String? = null) : Ziel
    data class Gallery(val folder: String) : Ziel
    data class Video(val url: String) : Ziel
    data object Settings : Ziel
    data object Trash : Ziel
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Coil laedt Bilder sonst ueber einen eigenen Client, also ohne
        // Auth-Kopfzeilen und ohne Client-Zertifikat — hinter Cloudflare
        // Access blockiert der Rand diese Anfragen.
        SingletonImageLoader.setSafe { MedienClient.bildLader(it) }
        enableEdgeToEdge()
        setContent {
            MhTheme {
                val settings = remember { AppSettings(applicationContext) }
                val api = remember { ApiService(applicationContext) }
                Navigation(settings, api)
            }
        }
    }
}

@Composable
private fun Navigation(settings: AppSettings, api: ApiService) {
    val stack = remember { mutableStateListOf<Ziel>(Ziel.Haupt()) }
    fun push(ziel: Ziel) {
        // Reiterwechsel ersetzt die Hauptansicht, statt den Stapel zu füllen.
        if (ziel is Ziel.Haupt && stack.last() is Ziel.Haupt) stack[stack.lastIndex] = ziel
        else stack.add(ziel)
    }
    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    val oben = stack.last()
    // Zurück aus einem Reiter führt erst zum Start, dann aus der App.
    val zumStart = stack.size == 1 && oben is Ziel.Haupt && oben.reiter != Reiter.START
    BackHandler(enabled = stack.size > 1 || zumStart) {
        if (zumStart) stack[0] = Ziel.Haupt() else pop()
    }

    when (val ziel = oben) {
        is Ziel.Haupt -> HauptScreen(
            settings = settings,
            api = api,
            reiter = ziel.reiter,
            tag = ziel.tag,
            onNavigate = ::push,
        )

        is Ziel.Create -> CreateScreen(
            settings = settings,
            api = api,
            initialDate = ziel.initialDate,
            onZurueck = ::pop,
            onEinstellungen = { push(Ziel.Settings) },
        )

        is Ziel.Gallery -> GalleryScreen(
            api = api,
            folder = ziel.folder,
            onZurueck = ::pop,
            onNavigate = ::push,
        )

        is Ziel.Video -> VideoPlayerScreen(
            url = ziel.url,
            onZurueck = ::pop,
        )

        Ziel.Settings -> SettingsScreen(
            settings = settings,
            api = api,
            onZurueck = ::pop,
        )

        Ziel.Trash -> PapierkorbScreen(
            settings = settings,
            api = api,
            onZurueck = ::pop,
        )
    }
}
