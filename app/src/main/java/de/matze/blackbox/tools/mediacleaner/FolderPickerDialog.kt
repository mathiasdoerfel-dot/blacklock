package de.matze.blackbox.tools.mediacleaner

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.io.File

@Composable
fun FolderPickerDialog(
    initialRelativePath: String,
    onFolderSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val rootStorage = remember { Environment.getExternalStorageDirectory() }
    var currentDir by remember {
        val initialDir = File(rootStorage, initialRelativePath)
        val start = if (initialDir.exists() && initialDir.isDirectory) initialDir else rootStorage
        mutableStateOf(start)
    }

    val subFolders by remember(currentDir) {
        derivedStateOf {
            val list = currentDir.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") } ?: emptyList()
            list.sortedBy { it.name.lowercase() }
        }
    }

    val currentRelativePath by remember(currentDir) {
        derivedStateOf {
            CleanerConfig.normalizeRelativePath(currentDir.absolutePath)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(540.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF14151D),
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Fixed Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1C1E28))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = Color(0xFF818CF8)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Ordner auswählen",
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

                    Spacer(modifier = Modifier.height(8.dp))

                    // Breadcrumb / Current Path Display with UP button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF13141C))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isRoot = currentDir.absolutePath == rootStorage.absolutePath
                        IconButton(
                            onClick = {
                                val parent = currentDir.parentFile
                                if (parent != null && parent.absolutePath.startsWith(rootStorage.absolutePath)) {
                                    currentDir = parent
                                }
                            },
                            enabled = !isRoot,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Übergeordneter Ordner",
                                tint = if (!isRoot) Color(0xFFA5B4FC) else Color(0xFF4B5563),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Text(
                            text = if (currentRelativePath.isEmpty()) "Interner Speicher (Root)" else currentRelativePath,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE5E7EB),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Scrollable Folder List
                if (subFolders.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = Color(0xFF4B5563),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Keine Unterordner vorhanden",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF9CA3AF)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(subFolders, key = { it.absolutePath }) { folder ->
                            val childCount = folder.listFiles()?.count { it.isDirectory && !it.name.startsWith(".") } ?: 0
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { currentDir = folder }
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = Color(0xFF6366F1),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = folder.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (childCount > 0) {
                                    Text(
                                        text = "$childCount Ordner",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF6B7280)
                                    )
                                }
                            }
                        }
                    }
                }

                // Fixed Footer with Action Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1C1E28))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Abbrechen", color = Color(0xFF9CA3AF))
                    }
                    Button(
                        onClick = {
                            onFolderSelected(currentRelativePath)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Diesen Ordner wählen")
                    }
                }
            }
        }
    }
}

@Composable
fun ManualPathDialog(
    initialPath: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var textValue by remember { mutableStateOf(initialPath) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Pfad manuell eingeben", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    text = "Gib den relativen Pfad ab internem Speicher ein (z. B. 'Download/Quick Share'):",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFD1D5DB)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = Color(0xFF4B5563)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(textValue) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
            ) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen", color = Color(0xFF9CA3AF))
            }
        },
        containerColor = Color(0xFF1E1F29),
        shape = RoundedCornerShape(16.dp)
    )
}
