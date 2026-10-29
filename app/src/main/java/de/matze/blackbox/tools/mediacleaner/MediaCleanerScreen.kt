package de.matze.blackbox.tools.mediacleaner

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import de.matze.blackbox.core.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun MediaCleanerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { CleanerRepository(context) }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Übersicht", "Papierkorb", "Zeitplan & Regeln")

    var hasPermission by remember { mutableStateOf(repo.hasAllFilesAccess()) }
    var folderStats by remember { mutableStateOf<List<FolderStat>>(emptyList()) }
    var trashItems by remember { mutableStateOf<List<TrashItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var previewItem by remember { mutableStateOf<TrashItem?>(null) }
    var showEmptyTrashDialog by remember { mutableStateOf(false) }

    // Dialog state for folder path editing
    var folderToEdit by remember { mutableStateOf<FolderStat?>(null) }
    var showFolderPickerFor by remember { mutableStateOf<FolderStat?>(null) }
    var showManualPathFor by remember { mutableStateOf<FolderStat?>(null) }

    fun refreshData() {
        scope.launch {
            isLoading = true
            hasPermission = repo.hasAllFilesAccess()
            val (stats, trash) = withContext(Dispatchers.IO) {
                Pair(repo.getFolderStats(), repo.getTrashItems())
            }
            folderStats = stats
            trashItems = trash
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0D))
    ) {
        // Top Bar (Fixed)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF121319))
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Zurück",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "Media Cleaner",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "WhatsApp Private & Quick Share",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF9CA3AF)
                    )
                }
            }
            IconButton(onClick = { refreshData() }) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Aktualisieren",
                    tint = Color(0xFF9CA3AF)
                )
            }
        }

        // Tab Row (Fixed)
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color(0xFF14151D),
            contentColor = Color(0xFF818CF8),
            modifier = Modifier.fillMaxWidth()
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = if (index == 1 && trashItems.isNotEmpty()) "$title (${trashItems.size})" else title,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == index) Color(0xFF818CF8) else Color(0xFF9CA3AF)
                        )
                    }
                )
            }
        }

        // Tab Content
        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            when (selectedTab) {
                0 -> OverviewTab(
                    hasPermission = hasPermission,
                    folderStats = folderStats,
                    lastRunSummary = repo.config.lastRunSummary,
                    lastRunTimestamp = repo.config.lastRunTimestamp,
                    onToggleFolder = { defId, enabled ->
                        repo.config.setFolderEnabled(defId, enabled)
                        refreshData()
                    },
                    onEditFolder = { stat ->
                        folderToEdit = stat
                    },
                    onCleanNow = {
                        scope.launch {
                            isLoading = true
                            val result = withContext(Dispatchers.IO) {
                                repo.runCleanup(ignoreMinAge = (repo.config.minFileAgeDays == 0))
                            }
                            refreshData()
                            val msg = "${result.movedCount} Dateien (${FormatUtils.formatBytes(result.movedBytes)}) in den Papierkorb verschoben"
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    },
                    onRequestPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            try {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                context.startActivity(intent)
                            }
                        }
                    }
                )
                1 -> TrashTab(
                    trashItems = trashItems,
                    retentionDays = repo.config.trashRetentionDays,
                    onPreview = { previewItem = it },
                    onRestore = { item ->
                        scope.launch {
                            val res = withContext(Dispatchers.IO) { repo.restoreItem(item.id) }
                            if (res.isSuccess) {
                                Toast.makeText(context, "${item.fileName} wiederhergestellt", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Fehler: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                            }
                            refreshData()
                        }
                    },
                    onDeletePermanently = { item ->
                        scope.launch {
                            withContext(Dispatchers.IO) { repo.deletePermanently(item.id) }
                            Toast.makeText(context, "${item.fileName} endgültig gelöscht", Toast.LENGTH_SHORT).show()
                            refreshData()
                        }
                    },
                    onRestoreAll = {
                        scope.launch {
                            isLoading = true
                            val count = withContext(Dispatchers.IO) { repo.restoreAll() }
                            refreshData()
                            Toast.makeText(context, "$count Dateien wiederhergestellt", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onEmptyTrash = { showEmptyTrashDialog = true }
                )
                2 -> ScheduleTab(
                    config = repo.config,
                    onConfigChanged = {
                        CleanupScheduler.syncSchedule(context)
                        refreshData()
                    }
                )
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF6366F1))
                }
            }
        }
    }

    // Options Modal when clicking Edit on a folder
    folderToEdit?.let { stat ->
        AlertDialog(
            onDismissRequest = { folderToEdit = null },
            title = {
                Text(text = stat.def.title, color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Aktueller Pfad: ${stat.currentRelativePath}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF9CA3AF)
                    )
                    Text(
                        text = if (stat.exists) "Status: ✓ Ordner existiert (${stat.fileCount} Dateien)" else "Status: ⚠️ Ordner existiert auf dem Gerät nicht",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (stat.exists) Color(0xFF34D399) else Color(0xFFF87171)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            val target = stat
                            folderToEdit = null
                            showFolderPickerFor = target
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color(0xFF818CF8))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Im Dateimanager auswählen", color = Color.White)
                    }

                    OutlinedButton(
                        onClick = {
                            val target = stat
                            folderToEdit = null
                            showManualPathFor = target
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF818CF8))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pfad manuell eingeben", color = Color.White)
                    }

                    if (stat.isCustom) {
                        TextButton(
                            onClick = {
                                repo.config.resetFolderPath(stat.def.id)
                                folderToEdit = null
                                refreshData()
                                Toast.makeText(context, "Pfad auf Standard zurückgesetzt", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Auf Standardpfad zurücksetzen", color = Color(0xFFF87171))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { folderToEdit = null }) {
                    Text("Schließen", color = Color(0xFF9CA3AF))
                }
            },
            containerColor = Color(0xFF1E1F29),
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Folder Picker Dialog
    showFolderPickerFor?.let { stat ->
        FolderPickerDialog(
            initialRelativePath = stat.currentRelativePath,
            onFolderSelected = { selectedPath ->
                repo.config.setFolderPath(stat.def.id, selectedPath)
                showFolderPickerFor = null
                refreshData()
                Toast.makeText(context, "Pfad aktualisiert: $selectedPath", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showFolderPickerFor = null }
        )
    }

    // Manual Path Dialog
    showManualPathFor?.let { stat ->
        ManualPathDialog(
            initialPath = stat.currentRelativePath,
            onSave = { newPath ->
                repo.config.setFolderPath(stat.def.id, newPath)
                showManualPathFor = null
                refreshData()
                Toast.makeText(context, "Pfad gespeichert: $newPath", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showManualPathFor = null }
        )
    }

    // Detail/Preview Dialog
    previewItem?.let { item ->
        MediaPreviewDialog(item = item, onDismiss = { previewItem = null })
    }

    // Confirm Empty Trash Dialog
    if (showEmptyTrashDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashDialog = false },
            title = { Text("Papierkorb wirklich leeren?", color = Color.White) },
            text = {
                Text(
                    "Alle ${trashItems.size} Dateien (${FormatUtils.formatBytes(trashItems.sumOf { it.sizeBytes })}) werden unwiderruflich von deinem Gerät gelöscht.",
                    color = Color(0xFFD1D5DB)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEmptyTrashDialog = false
                        scope.launch {
                            isLoading = true
                            val deleted = withContext(Dispatchers.IO) { repo.emptyTrash() }
                            refreshData()
                            Toast.makeText(context, "$deleted Dateien endgültig gelöscht", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Endgültig löschen")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyTrashDialog = false }) {
                    Text("Abbrechen", color = Color(0xFF9CA3AF))
                }
            },
            containerColor = Color(0xFF1E1F29),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun OverviewTab(
    hasPermission: Boolean,
    folderStats: List<FolderStat>,
    lastRunSummary: String,
    lastRunTimestamp: Long,
    onToggleFolder: (String, Boolean) -> Unit,
    onEditFolder: (FolderStat) -> Unit,
    onCleanNow: () -> Unit,
    onRequestPermission: () -> Unit
) {
    val totalPendingFiles = folderStats.filter { it.enabled && it.exists }.sumOf { it.fileCount }
    val totalPendingBytes = folderStats.filter { it.enabled && it.exists }.sumOf { it.totalBytes }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (!hasPermission) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3B1D1D)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFF87171))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Berechtigung erforderlich",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(6.tx))
                        Text(
                            text = "Um die WhatsApp- und Quick-Share-Ordner aufzuräumen, benötigt BlackBox die Android-Berechtigung 'Zugriff auf alle Dateien'.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE5E7EB)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onRequestPermission,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Berechtigung erteilen")
                        }
                    }
                }
            }
        }

        // Summary Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF151722)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Bereit zum Aufräumen",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF6366F1).copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "$totalPendingFiles Dateien · ${FormatUtils.formatBytes(totalPendingBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFA5B4FC)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onCleanNow,
                        enabled = hasPermission && totalPendingFiles > 0,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Jetzt alle in Papierkorb verschieben", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Letzter Lauf: ${FormatUtils.formatDateTime(lastRunTimestamp)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF9CA3AF)
                    )
                    Text(
                        text = "Status: $lastRunSummary",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF9CA3AF)
                    )
                }
            }
        }

        // Folder List Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Überwachte Ordner",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Tippen zum Anpassen",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF9CA3AF)
                )
            }
        }

        items(folderStats) { stat ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (stat.enabled) Color(0xFF1B1D28) else Color(0xFF15161D)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEditFolder(stat) }
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (stat.enabled) Color(0xFF4F46E5).copy(alpha = 0.25f) else Color(0xFF374151)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (stat.def.id.contains("video")) Icons.Default.VideoLibrary else if (stat.def.id.contains("quick")) Icons.Default.Share else Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                tint = if (stat.enabled) Color(0xFFA5B4FC) else Color(0xFF9CA3AF)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stat.def.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                if (stat.isCustom) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF6366F1).copy(alpha = 0.25f))
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text("Angepasst", style = MaterialTheme.typography.labelSmall, color = Color(0xFFA5B4FC))
                                    }
                                }
                            }
                            Text(
                                text = stat.currentRelativePath,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF9CA3AF),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Switch(
                            checked = stat.enabled,
                            onCheckedChange = { onToggleFolder(stat.def.id, it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF6366F1),
                                uncheckedThumbColor = Color(0xFF9CA3AF),
                                uncheckedTrackColor = Color(0xFF2B2D38)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (stat.exists) "${stat.fileCount} Dateien (${FormatUtils.formatBytes(stat.totalBytes)})" else "⚠️ Ordner existiert nicht",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (!stat.exists) FontWeight.Bold else FontWeight.Normal,
                            color = if (!stat.exists) Color(0xFFF87171) else if (stat.fileCount > 0) Color(0xFF34D399) else Color(0xFF9CA3AF)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onEditFolder(stat) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Pfad bearbeiten",
                                tint = Color(0xFF818CF8),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Pfad anpassen",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF818CF8)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrashTab(
    trashItems: List<TrashItem>,
    retentionDays: Int,
    onPreview: (TrashItem) -> Unit,
    onRestore: (TrashItem) -> Unit,
    onDeletePermanently: (TrashItem) -> Unit,
    onRestoreAll: () -> Unit,
    onEmptyTrash: () -> Unit
) {
    if (trashItems.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = Color(0xFF4B5563),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Papierkorb ist leer",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Verschobene WhatsApp- und Quick-Share-Dateien landen hier für 30 Tage, bevor sie endgültig gelöscht werden.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9CA3AF),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF151722)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${trashItems.size} Dateien im Papierkorb",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Gesamtgröße: ${FormatUtils.formatBytes(trashItems.sumOf { it.sizeBytes })}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF9CA3AF)
                        )
                    }
                    Row {
                        OutlinedButton(
                            onClick = onRestoreAll,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("Alle retten", style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onEmptyTrash,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("Leeren", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        items(trashItems, key = { it.id }) { item ->
            TrashItemRow(
                item = item,
                retentionDays = retentionDays,
                onPreview = { onPreview(item) },
                onRestore = { onRestore(item) },
                onDelete = { onDeletePermanently(item) }
            )
        }
    }
}

@Composable
private fun TrashItemRow(
    item: TrashItem,
    retentionDays: Int,
    onPreview: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val daysLeft = item.daysRemaining(retentionDays)

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1D28)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable { onPreview() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2A2D3A)),
                contentAlignment = Alignment.Center
            ) {
                val file = File(item.trashFilePath)
                if (file.exists()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(file)
                            .decoderFactory { result, options, _ -> VideoFrameDecoder(result.source, options) }
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (item.isVideo) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.sourceFolderTitle} · ${FormatUtils.formatBytes(item.sizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF9CA3AF)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Noch $daysLeft Tage bis zur Löschung",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (daysLeft <= 3) Color(0xFFF87171) else Color(0xFF34D399)
                )
            }

            Row {
                IconButton(onClick = onRestore, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = "Wiederherstellen",
                        tint = Color(0xFF818CF8)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = "Endgültig löschen",
                        tint = Color(0xFFF87171)
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleTab(
    config: CleanerConfig,
    onConfigChanged: () -> Unit
) {
    var selectedMode by remember { mutableStateOf(config.scheduleMode) }
    var minFileAge by remember { mutableIntStateOf(config.minFileAgeDays) }
    var retentionDays by remember { mutableIntStateOf(config.trashRetentionDays) }
    var requireCharging by remember { mutableStateOf(config.requireCharging) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Automatisches Aufräumen",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF151722)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Intervall",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ScheduleMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedMode = mode
                                    config.scheduleMode = mode
                                    onConfigChanged()
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedMode == mode,
                                onClick = {
                                    selectedMode = mode
                                    config.scheduleMode = mode
                                    onConfigChanged()
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Color(0xFF6366F1),
                                    unselectedColor = Color(0xFF6B7280)
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (selectedMode == mode) Color.White else Color(0xFFD1D5DB)
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF151722)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Mindestalter der Dateien",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = "Nur Dateien verschieben, die älter sind als:",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF9CA3AF)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    val ageOptions = listOf(
                        0 to "Sofort / Alle vorhandenen (0 Tage)",
                        1 to "Älter als 1 Tag",
                        3 to "Älter als 3 Tage",
                        7 to "Älter als 7 Tage"
                    )
                    ageOptions.forEach { (days, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    minFileAge = days
                                    config.minFileAgeDays = days
                                    onConfigChanged()
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = minFileAge == days,
                                onClick = {
                                    minFileAge = days
                                    config.minFileAgeDays = days
                                    onConfigChanged()
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Color(0xFF6366F1),
                                    unselectedColor = Color(0xFF6B7280)
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (minFileAge == days) Color.White else Color(0xFFD1D5DB)
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF151722)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Papierkorb-Aufbewahrungsdauer",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = "Nach wie vielen Tagen sollen Dateien im Papierkorb endgültig gelöscht werden?",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF9CA3AF)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    val retentionOptions = listOf(
                        14 to "14 Tage",
                        30 to "30 Tage (Standard)",
                        60 to "60 Tage",
                        90 to "90 Tage"
                    )
                    retentionOptions.forEach { (days, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    retentionDays = days
                                    config.trashRetentionDays = days
                                    onConfigChanged()
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = retentionDays == days,
                                onClick = {
                                    retentionDays = days
                                    config.trashRetentionDays = days
                                    onConfigChanged()
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Color(0xFF6366F1),
                                    unselectedColor = Color(0xFF6B7280)
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (retentionDays == days) Color.White else Color(0xFFD1D5DB)
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF151722)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Nur beim Laden ausführen",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "Automatischer Lauf wartet, bis das Pixel am Stromnetz hängt",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF9CA3AF)
                        )
                    }
                    Switch(
                        checked = requireCharging,
                        onCheckedChange = {
                            requireCharging = it
                            config.requireCharging = it
                            onConfigChanged()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF6366F1)
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaPreviewDialog(
    item: TrashItem,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF12131A)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    val file = File(item.trashFilePath)
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(file)
                            .decoderFactory { result, options, _ -> VideoFrameDecoder(result.source, options) }
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Herkunft: ${item.sourceFolderTitle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9CA3AF)
                )
                Text(
                    text = "Größe: ${FormatUtils.formatBytes(item.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9CA3AF)
                )
                Text(
                    text = "Gelöscht am: ${FormatUtils.formatDateTime(item.trashedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9CA3AF)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    TextButton(onClick = onDismiss) {
                        Text("Schließen", color = Color(0xFF818CF8))
                    }
                }
            }
        }
    }
}
