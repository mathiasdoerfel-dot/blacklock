package de.matze.blackbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.matze.blackbox.core.ChangelogData
import de.matze.blackbox.core.ChangelogDialog
import de.matze.blackbox.core.FormatUtils
import de.matze.blackbox.core.updater.UpdateDialog
import de.matze.blackbox.core.updater.UpdateInfo
import de.matze.blackbox.core.updater.UpdateManager
import de.matze.blackbox.tools.mediacleaner.CleanerRepository
import de.matze.blackbox.tools.screenlock.LockAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HubScreen(
    onNavigateToCleaner: () -> Unit,
    onNavigateToLock: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { CleanerRepository(context) }
    var showChangelog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var availableUpdateInfo by remember { mutableStateOf<UpdateInfo?>(null) }

    var cleanerPendingFiles by remember { mutableIntStateOf(0) }
    var cleanerPendingBytes by remember { mutableLongStateOf(0L) }
    var trashCount by remember { mutableIntStateOf(0) }
    var isLockActive by remember { mutableStateOf(LockAccessibilityService.isRunning) }
    var hasFilePermission by remember { mutableStateOf(repo.hasAllFilesAccess()) }

    LaunchedEffect(Unit) {
        hasFilePermission = repo.hasAllFilesAccess()
        isLockActive = LockAccessibilityService.isRunning
        val (stats, trash) = withContext(Dispatchers.IO) {
            Pair(repo.getFolderStats(), repo.getTrashItems())
        }
        cleanerPendingFiles = stats.filter { it.enabled }.sumOf { it.fileCount }
        cleanerPendingBytes = stats.filter { it.enabled }.sumOf { it.totalBytes }
        trashCount = trash.size

        // Silent background update check
        val updateRes = UpdateManager.checkForUpdate(context)
        updateRes.onSuccess { info ->
            if (info.isUpdateAvailable) {
                availableUpdateInfo = info
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0D))
    ) {
        // App Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF121319))
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF6366F1)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Apps,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "BlackBox",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Pixel Utility Toolbox",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF9CA3AF)
                    )
                }
            }

            // Actions in Header (Update Badge + Version Pill)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (availableUpdateInfo != null) {
                    Surface(
                        onClick = { showUpdateDialog = true },
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF3730A3)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF34D399))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Update v${availableUpdateInfo?.latestVersion}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                // Version Pill (Opens Changelog)
                Surface(
                    onClick = { showChangelog = true },
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF1E202B)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "v${ChangelogData.CURRENT_VERSION}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFA5B4FC)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Changelog",
                            tint = Color(0xFFA5B4FC),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Installierte Module",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )
            }

            // Tool 1: Media Cleaner Card
            item {
                ToolCard(
                    title = "Media Cleaner",
                    subtitle = "WhatsApp Private & Quick Share bereinigen",
                    statusText = if (!hasFilePermission) {
                        "⚠️ Berechtigung 'Zugriff auf alle Dateien' fehlt"
                    } else if (cleanerPendingFiles > 0) {
                        "$cleanerPendingFiles Dateien (${FormatUtils.formatBytes(cleanerPendingBytes)}) · $trashCount im Papierkorb"
                    } else {
                        "Alles sauber · $trashCount im Papierkorb"
                    },
                    statusColor = if (!hasFilePermission) Color(0xFFF87171) else if (cleanerPendingFiles > 0) Color(0xFF34D399) else Color(0xFF9CA3AF),
                    icon = Icons.Default.DeleteSweep,
                    iconBgColor = Color(0xFF4F46E5),
                    onClick = onNavigateToCleaner
                )
            }

            // Tool 2: BlackLock Card
            item {
                ToolCard(
                    title = "BlackLock",
                    subtitle = "Bildschirm per Tile & Widget sperren",
                    statusText = if (isLockActive) "Aktiv (Tile & Widget bereit)" else "Inaktiv (Bedienungshilfe einrichten)",
                    statusColor = if (isLockActive) Color(0xFF34D399) else Color(0xFFFBBF24),
                    icon = Icons.Default.Lock,
                    iconBgColor = Color(0xFF1E293B),
                    onClick = onNavigateToLock
                )
            }

            // Future Modules Teaser Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF12131A)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF262835)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = Color(0xFF9CA3AF)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Weitere Tools folgen...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFE5E7EB)
                            )
                            Text(
                                text = "Modulare Struktur für zukünftige Pixel-Funktionen",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF6B7280)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showChangelog) {
        ChangelogDialog(
            onDismiss = { showChangelog = false },
            onOpenUpdater = { showUpdateDialog = true }
        )
    }

    if (showUpdateDialog) {
        UpdateDialog(
            initialUpdateInfo = availableUpdateInfo,
            onDismiss = { showUpdateDialog = false }
        )
    }
}

@Composable
private fun ToolCard(
    title: String,
    subtitle: String,
    statusText: String,
    statusColor: Color,
    icon: ImageVector,
    iconBgColor: Color,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161822)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(iconBgColor.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFFA5B4FC),
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9CA3AF)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.Medium
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = Color(0xFF6B7280),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
