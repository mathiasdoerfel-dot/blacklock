package de.matze.blackbox.core.updater

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import de.matze.blackbox.core.FormatUtils
import kotlinx.coroutines.launch
import java.io.File

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner

@Composable
fun UpdateDialog(
    initialUpdateInfo: UpdateInfo? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var state by remember {
        mutableStateOf<UpdateState>(
            if (initialUpdateInfo != null) {
                if (initialUpdateInfo.isUpdateAvailable) {
                    UpdateState.Available(initialUpdateInfo)
                } else {
                    UpdateState.UpToDate(initialUpdateInfo.latestVersion)
                }
            } else {
                UpdateState.Idle
            }
        )
    }

    var downloadedApkFile by remember { mutableStateOf<File?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val currentState = state
                if (currentState is UpdateState.PermissionRequired && UpdateManager.canInstallPackages(context)) {
                    state = UpdateState.ReadyToInstall(currentState.apkFile)
                    UpdateManager.installApk(context, currentState.apkFile)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun triggerCheck() {
        state = UpdateState.Checking
        coroutineScope.launch {
            val result = UpdateManager.checkForUpdate(context)
            result.fold(
                onSuccess = { info ->
                    state = if (info.isUpdateAvailable) {
                        UpdateState.Available(info)
                    } else {
                        UpdateState.UpToDate(info.latestVersion)
                    }
                },
                onFailure = { error ->
                    state = UpdateState.Error(error.localizedMessage ?: "Verbindungsfehler zur GitHub API")
                }
            )
        }
    }

    fun startDownload(info: UpdateInfo) {
        val url = info.downloadUrl ?: return
        state = UpdateState.Downloading(0f, 0L, info.fileSize)

        coroutineScope.launch {
            val result = UpdateManager.downloadApk(
                context = context,
                downloadUrl = url,
                targetVersion = info.latestVersion
            ) { progress, downloaded, total ->
                state = UpdateState.Downloading(progress, downloaded, total)
            }

            result.fold(
                onSuccess = { file ->
                    downloadedApkFile = file
                    if (UpdateManager.canInstallPackages(context)) {
                        state = UpdateState.ReadyToInstall(file)
                        UpdateManager.installApk(context, file)
                    } else {
                        state = UpdateState.PermissionRequired(file)
                    }
                },
                onFailure = { error ->
                    state = UpdateState.Error("Download fehlgeschlagen: ${error.localizedMessage}")
                }
            )
        }
    }

    // Auto-check on first open if no initial info was passed
    LaunchedEffect(Unit) {
        if (state is UpdateState.Idle) {
            triggerCheck()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(520.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF14151B),
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Fixed Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1C1E26))
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF6366F1).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = Color(0xFFA5B4FC),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "In-App Updater",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Schließen",
                            tint = Color(0xFF9CA3AF)
                        )
                    }
                }

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (val current = state) {
                        is UpdateState.Idle, is UpdateState.Checking -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(
                                        color = Color(0xFF6366F1),
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Prüfe GitHub auf neue Versionen...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFF9CA3AF)
                                    )
                                }
                            }
                        }

                        is UpdateState.UpToDate -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF18221D)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF34D399),
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Alles auf dem neuesten Stand",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Version ${current.version} ist die aktuellste Version.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFFA7F3D0)
                                        )
                                    }
                                }
                            }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1D1F28)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Information",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFA5B4FC)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Updates werden direkt über GitHub Releases synchronisiert. Sobald eine neuere Version gebaut wird, kannst du sie hier sofort installieren.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF9CA3AF)
                                    )
                                }
                            }
                        }

                        is UpdateState.Available -> {
                            val info = current.info
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF231F3A)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF6366F1)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.NewReleases,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Neue Version v${info.latestVersion} verfügbar!",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Größe: ${FormatUtils.formatBytes(info.fileSize)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFFA5B4FC)
                                        )
                                    }
                                }
                            }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1D1F28)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Änderungen in dieser Version:",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = info.releaseNotes,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFD1D5DB)
                                    )
                                }
                            }
                        }

                        is UpdateState.Downloading -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1D1F28)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Lade Update herunter...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "${(current.progress * 100).toInt()}%",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFA5B4FC)
                                        )
                                    }

                                    LinearProgressIndicator(
                                        progress = current.progress,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = Color(0xFF6366F1),
                                        trackColor = Color(0xFF2B2D3C)
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${FormatUtils.formatBytes(current.downloadedBytes)} von ${FormatUtils.formatBytes(current.totalBytes)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF9CA3AF)
                                        )
                                        Text(
                                            text = "Bitte warten...",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF9CA3AF)
                                        )
                                    }
                                }
                            }
                        }

                        is UpdateState.PermissionRequired -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2319)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Security,
                                            contentDescription = null,
                                            tint = Color(0xFFFBBF24),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "Berechtigung erforderlich",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                    Text(
                                        text = "Damit BlackBox sich selbst aktualisieren kann, muss Android die Berechtigung 'Unbekannte Apps installieren' für BlackBox erteilen.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFFDE68A)
                                    )
                                    Button(
                                        onClick = {
                                            UpdateManager.openInstallPermissionSettings(context)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Berechtigung in Einstellungen aktivieren", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        is UpdateState.ReadyToInstall -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF18221D)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DownloadDone,
                                        contentDescription = null,
                                        tint = Color(0xFF34D399),
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Download abgeschlossen",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Bereit zur Installation.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFFA7F3D0)
                                        )
                                    }
                                }
                            }
                        }

                        is UpdateState.Error -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B181A)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = Color(0xFFF87171),
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Fehler aufgetreten",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = current.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFFFCA5A5)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Fixed Footer
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1C1E26))
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    color = Color.Transparent
                ) {
                    when (val current = state) {
                        is UpdateState.Available -> {
                            Button(
                                onClick = { startDownload(current.info) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Jetzt herunterladen & aktualisieren", fontWeight = FontWeight.Bold)
                            }
                        }

                        is UpdateState.ReadyToInstall -> {
                            Button(
                                onClick = { UpdateManager.installApk(context, current.apkFile) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("App jetzt installieren", fontWeight = FontWeight.Bold)
                            }
                        }

                        is UpdateState.PermissionRequired -> {
                            Button(
                                onClick = {
                                    if (UpdateManager.canInstallPackages(context)) {
                                        UpdateManager.installApk(context, current.apkFile)
                                    } else {
                                        UpdateManager.openInstallPermissionSettings(context)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (UpdateManager.canInstallPackages(context)) "Berechtigung erteilt – Jetzt installieren"
                                    else "Einstellungen erneut öffnen",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        is UpdateState.Downloading -> {
                            OutlinedButton(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Im Hintergrund laden", color = Color(0xFF9CA3AF))
                            }
                        }

                        else -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onDismiss,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Schließen", color = Color(0xFF9CA3AF))
                                }
                                Button(
                                    onClick = { triggerCheck() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                                    shape = RoundedCornerShape(10.dp),
                                    enabled = state !is UpdateState.Checking,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Prüfen", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
