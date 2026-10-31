package de.matze.blackbox.core

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

data class ChangelogRelease(
    val version: String,
    val date: String,
    val changes: List<String>
)

object ChangelogData {
    const val CURRENT_VERSION = "0.0.4"

    val releases = listOf(
        ChangelogRelease(
            version = "0.0.4",
            date = "31.10.2026",
            changes = listOf(
                "Statusleisten-Abstand (Window Insets): Header sauber unterhalb der System-Statusleiste (Kamera-Punch-Hole, Uhrzeit, Akku) platziert.",
                "Android Gestennavigation: Volle Unterstützung der Zurück-Wischgeste (Predictive Back) inklusive hierarchischer Navigation im Media Cleaner.",
                "Gestenleisten-Schutz: Unterer Abstand (navigationBarsPadding) verhindert Überlappung mit dem Home-Gestenbalken.",
                "Sofortige Berechtigungserkennung: Bedienungshilfe, All-Files-Access und Installationen werden beim Zurückkehren zur App sofort aktiv.",
                "Widget- & Launcher-Härtung: Bereinigung von RemoteViews-Attributen für stabilen Halt im Pixel Launcher und Deaktivierung unpassender Skalierung.",
                "Quick-Settings-Tile: Automatische Synchronisation des Kachel-Zustands (Aktiv / Inaktiv)."
            )
        ),
        ChangelogRelease(
            version = "0.0.2",
            date = "30.10.2026",
            changes = listOf(
                "In-App Updater: Prüfung und One-Click-Download & Installation von neuen Versionen direkt über GitHub Releases.",
                "GitHub Actions Release Automation: Automatisches Bereitstellen neuer APKs als GitHub Releases.",
                "Fester Signaturschlüssel: Nahtlose Updates ohne 'Signaturkonflikt' und ohne Datenverlust.",
                "Update-Dialog mit Download-Fortschrittsanzeige und automatischer Berechtigungsprüfung."
            )
        ),
        ChangelogRelease(
            version = "0.0.1",
            date = "29.10.2026",
            changes = listOf(
                "Initiales Release von BlackBox als modulare Android-Toolbox für das Pixel 11 Pro XL.",
                "Modul 1 – Media Cleaner: Überwacht WhatsApp Images/Private, WhatsApp Video/Private und Download/Quick Share.",
                "In-App-Verzeichnis-Browser & Pfad-Anpassung: Direkte Erkennung, ob Ordner existieren, und interaktive Ordner-Auswahl über das Dateisystem.",
                "App-eigener Papierkorb mit 30 Tagen Aufbewahrung, Bild-/Video-Vorschau, Einzel- und Komplett-Wiederherstellung.",
                "Flexibler Reinigungs-Zeitplan (Wöchentlich als Standard, Täglich, Alle 3/14 Tage oder Nur manuell) sowie Altersfilter.",
                "Modul 2 – BlackLock: Integration von Quick-Settings-Tile, unsichtbarem Homescreen-Widget und Bedienungshilfen-Sperrfunktion."
            )
        )
    )
}

@Composable
fun ChangelogDialog(
    onDismiss: () -> Unit,
    onOpenUpdater: () -> Unit = {}
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(480.dp),
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
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = Color(0xFF6366F1)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Changelog",
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
                    ChangelogData.releases.forEach { release ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1D1F28)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "v${release.version}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF818CF8)
                                    )
                                    Text(
                                        text = release.date,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF9CA3AF)
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                release.changes.forEach { line ->
                                    Row(
                                        modifier = Modifier.padding(vertical = 3.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = "• ",
                                            color = Color(0xFF6366F1),
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = line,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFFE5E7EB)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Fixed Footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1C1E26))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        onDismiss()
                        onOpenUpdater()
                    }) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = Color(0xFFA5B4FC),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Nach Updates suchen", color = Color(0xFFA5B4FC))
                    }

                    TextButton(onClick = onDismiss) {
                        Text("Schließen", color = Color(0xFF818CF8))
                    }
                }
            }
        }
    }
}
