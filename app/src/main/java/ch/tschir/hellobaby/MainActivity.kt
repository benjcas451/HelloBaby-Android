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
import ch.tschir.hellobaby.ui.CreateScreen
import ch.tschir.hellobaby.ui.DayViewScreen
import ch.tschir.hellobaby.ui.FavoritesScreen
import ch.tschir.hellobaby.ui.GalleryScreen
import ch.tschir.hellobaby.ui.HelloBabyTheme
import ch.tschir.hellobaby.ui.HomeScreen
import ch.tschir.hellobaby.ui.ImageFeedScreen
import ch.tschir.hellobaby.ui.MonthViewScreen
import ch.tschir.hellobaby.ui.SettingsScreen
import ch.tschir.hellobaby.ui.VideoPlayerScreen

/** Ziele der einfachen, eigenen Navigation (Back-Stack als Liste). */
sealed interface Ziel {
    data object Home : Ziel
    data class Create(val initialDate: String? = null) : Ziel
    data class Day(val initialDate: String? = null) : Ziel
    data object Month : Ziel
    data object Favorites : Ziel
    data object ImageFeed : Ziel
    data class Gallery(val folder: String) : Ziel
    data class Video(val url: String) : Ziel
    data object Settings : Ziel
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HelloBabyTheme {
                val settings = remember { AppSettings(applicationContext) }
                val api = remember { ApiService(applicationContext) }
                Navigation(settings, api)
            }
        }
    }
}

@Composable
private fun Navigation(settings: AppSettings, api: ApiService) {
    val stack = remember { mutableStateListOf<Ziel>(Ziel.Home) }
    fun push(ziel: Ziel) = stack.add(ziel)
    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    BackHandler(enabled = stack.size > 1) { pop() }

    when (val ziel = stack.last()) {
        Ziel.Home -> HomeScreen(
            settings = settings,
            api = api,
            onNavigate = ::push,
        )

        is Ziel.Create -> CreateScreen(
            settings = settings,
            api = api,
            initialDate = ziel.initialDate,
            onZurueck = ::pop,
            onEinstellungen = { push(Ziel.Settings) },
        )

        is Ziel.Day -> DayViewScreen(
            settings = settings,
            api = api,
            initialDate = ziel.initialDate,
            onZurueck = ::pop,
            onNavigate = ::push,
        )

        Ziel.Month -> MonthViewScreen(
            settings = settings,
            api = api,
            onZurueck = ::pop,
            onNavigate = ::push,
        )

        Ziel.Favorites -> FavoritesScreen(
            settings = settings,
            api = api,
            onZurueck = ::pop,
            onNavigate = ::push,
        )

        Ziel.ImageFeed -> ImageFeedScreen(
            settings = settings,
            api = api,
            onZurueck = ::pop,
            onNavigate = ::push,
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
    }
}
