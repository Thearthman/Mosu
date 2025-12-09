package com.mosu.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mosu.app.data.db.AppDatabase
import com.mosu.app.data.repository.OsuRepository
import com.mosu.app.player.MusicController
import com.mosu.app.ui.library.LibraryScreen
import com.mosu.app.ui.search.SearchScreen

class MainActivity : ComponentActivity() {
    private val repository = OsuRepository()
    private val clientId = "46495"
    private val redirectUri = "mosu://callback"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Handle Deep Link
        val data: Uri? = intent?.data
        val code = data?.getQueryParameter("code")

        val db = AppDatabase.getDatabase(this)

        setContent {
            MaterialTheme {
                MainScreen(
                    initialAuthCode = code,
                    onLoginClick = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://osu.ppy.sh/oauth/authorize?client_id=$clientId&redirect_uri=$redirectUri&response_type=code&scope=public+identify")
                        )
                        startActivity(intent)
                    },
                    repository = repository,
                    db = db
                )
            }
        }
    }
}

@Composable
fun MainScreen(
    initialAuthCode: String?,
    onLoginClick: () -> Unit,
    repository: OsuRepository,
    db: AppDatabase
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    
    // Music Controller stays alive at MainScreen level
    val musicController = remember { MusicController(context) }
    
    DisposableEffect(Unit) {
        onDispose { musicController.release() }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination?.route

                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Library") },
                    label = { Text("Library") },
                    selected = currentDestination == "library",
                    onClick = {
                        navController.navigate("library") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    label = { Text("Search") },
                    selected = currentDestination == "search",
                    onClick = {
                        navController.navigate("search") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "library",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("library") {
                LibraryScreen(db, musicController)
            }
            composable("search") {
                SearchScreen(
                    authCode = initialAuthCode,
                    onLoginClick = onLoginClick,
                    repository = repository,
                    db = db
                )
            }
        }
    }
}
