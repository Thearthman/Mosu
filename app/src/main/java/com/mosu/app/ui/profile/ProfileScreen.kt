package com.mosu.app.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mosu.app.data.SettingsManager
import com.mosu.app.data.TokenManager
import com.mosu.app.data.api.model.OsuUserCompact
import com.mosu.app.data.db.AppDatabase
import com.mosu.app.data.repository.OsuRepository
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    accessToken: String?,
    repository: OsuRepository,
    db: AppDatabase,
    tokenManager: TokenManager,
    settingsManager: SettingsManager,
    onLoginClick: () -> Unit,
    onLogout: () -> Unit
) {
    var userInfo by remember { mutableStateOf<OsuUserCompact?>(null) }
    var totalDownloaded by remember { mutableStateOf(0) }
    
    // Settings State
    var showSettingsDialog by remember { mutableStateOf(false) }
    val clientId by settingsManager.clientId.collectAsState(initial = "")
    val clientSecret by settingsManager.clientSecret.collectAsState(initial = "")
    
    val scope = rememberCoroutineScope()

    // Fetch user info on load
    LaunchedEffect(accessToken) {
        if (accessToken != null) {
            try {
                userInfo = repository.getMe(accessToken)
                val allMaps = db.beatmapDao().getAllBeatmaps()
                allMaps.collect { maps ->
                    totalDownloaded = maps.groupBy { it.beatmapSetId }.size
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Profile",
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (accessToken == null) {
            // Not logged in - Show Settings and Login
            
            // Settings Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("osu! OAuth Settings", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Client ID: ${if (clientId.isNotEmpty()) "Configured ✓" else "Not Set"}", style = MaterialTheme.typography.bodyMedium)
                    Text("Client Secret: ${if (clientSecret.isNotEmpty()) "Configured ✓" else "Not Set"}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { showSettingsDialog = true }) {
                        Text("Configure Credentials")
                    }
                }
            }
            
            // Login Button
            if (clientId.isNotEmpty() && clientSecret.isNotEmpty()) {
                Button(
                    onClick = onLoginClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Login with osu!")
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        "Please configure your OAuth credentials above to login",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        } else {
            // User Info Card
            userInfo?.let { user ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = user.avatarUrl,
                            contentDescription = "Avatar",
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Column(modifier = Modifier.padding(start = 16.dp)) {
                            Text(text = user.username, style = MaterialTheme.typography.titleLarge)
                            Text(text = "ID: ${user.id}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // Stats Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Statistics", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Downloaded Songs: $totalDownloaded", style = MaterialTheme.typography.bodyLarge)
                }
            }

            // Settings Card (Logged in version)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("osu! OAuth Settings", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Client ID: ${if (clientId.isNotEmpty()) clientId.take(8) + "..." else "Not Set"}", style = MaterialTheme.typography.bodyMedium)
                    Text("Client Secret: ${if (clientSecret.isNotEmpty()) "••••••••" else "Not Set"}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { showSettingsDialog = true }) {
                        Text("Update Credentials")
                    }
                }
            }

            // Placeholder Cards
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Equalizer", style = MaterialTheme.typography.titleMedium)
                    Text("Coming Soon", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Recent Activity Heatmap", style = MaterialTheme.typography.titleMedium)
                    Text("Coming Soon", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                }
            }

            // Logout Button
            Button(
                onClick = {
                    scope.launch {
                        tokenManager.clearToken()
                        onLogout()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.ExitToApp, contentDescription = "Logout")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Logout")
            }
        }
    }
    
    // Settings Dialog
    if (showSettingsDialog) {
        var inputClientId by remember { mutableStateOf(clientId) }
        var inputClientSecret by remember { mutableStateOf(clientSecret) }
        
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("OAuth Credentials") },
            text = {
                Column {
                    Text("Enter your osu! OAuth Application credentials:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputClientId,
                        onValueChange = { inputClientId = it },
                        label = { Text("Client ID") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputClientSecret,
                        onValueChange = { inputClientSecret = it },
                        label = { Text("Client Secret") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Get these from: osu.ppy.sh/home/account/edit", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            settingsManager.saveCredentials(inputClientId, inputClientSecret)
                            showSettingsDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

