package de.matze.blackbox.tools.pocketlock

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable
fun PocketLockScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val config = remember { BlackoutConfig(context) }

    var hasOverlayPermission by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }
    var isBlackoutRunning by remember {
        mutableStateOf(BlackoutService.isRunning)
    }
    var showHintOnTap by remember {
        mutableStateOf(config.showHintOnTap)
    }
    var showSubtleClock by remember {
        mutableStateOf(config.showSubtleClock)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = Settings.canDrawOverlays(context)
                isBlackoutRunning = BlackoutService.isRunning
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0D))
            .navigationBarsPadding()
    ) {
        // Top Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF121319))
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
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
                            text = "OLED Blackout",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "0W Display · Taschensperre",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF9CA3AF)
                        )
                    }
                }
                IconButton(onClick = {
                    hasOverlayPermission = Settings.canDrawOverlays(context)
                    isBlackoutRunning = BlackoutService.isRunning
                }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Aktualisieren",
                        tint = Color(0xFFA5B4FC)
                    )
                }
            }
        }

        // Scrollable Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Permission Banner
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (hasOverlayPermission) Color(0xFF151722) else Color(0xFF261916)
                ),
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
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (hasOverlayPermission) Color(0xFF065F46) else Color(0xFF991B1B)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (hasOverlayPermission) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (hasOverlayPermission) "Berechtigung erteilt" else "Überlagerungs-Berechtigung fehlt",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (hasOverlayPermission) {
                                "Blackout kann über jeder App gestartet werden."
                            } else {
                                "Erlaube 'Über anderen Apps anzeigen', um das stromlose Overlay zu aktivieren."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hasOverlayPermission) Color(0xFF9CA3AF) else Color(0xFFFCA5A5)
                        )
                    }
                    if (!hasOverlayPermission) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Erteilen", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // Test Action Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF151722)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF312E81)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DarkMode,
                                contentDescription = null,
                                tint = Color(0xFFA5B4FC),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Blackout Schnellstart",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Sofort testen oder stoppen",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF9CA3AF)
                            )
                        }
                    }

                    Text(
                        text = "Aktiviert den 100% schwarzen Bildschirm. Doppeltippen auf das Display beendet den Blackout sofort wieder.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFD1D5DB)
                    )

                    Button(
                        onClick = {
                            if (!hasOverlayPermission) {
                                Toast.makeText(
                                    context,
                                    "Bitte zuerst Überlagerungs-Berechtigung erteilen",
                                    Toast.LENGTH_SHORT
                                ).show()
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            } else {
                                if (isBlackoutRunning) {
                                    BlackoutService.stop(context)
                                    isBlackoutRunning = false
                                } else {
                                    BlackoutService.start(context)
                                    isBlackoutRunning = true
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isBlackoutRunning) Color(0xFFDC2626) else Color(0xFF6366F1)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = if (isBlackoutRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isBlackoutRunning) "Blackout beenden" else "Blackout jetzt starten",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Quick Settings Tile Guide
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF151722)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E293B)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.TouchApp,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Schnellzugriff (< 1 Sekunde)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Text(
                        text = "1. Wische zweimal von ganz oben nach unten, um die Schnelleinstellungen zu öffnen.\n" +
                            "2. Tippe auf das Stift-Symbol (Bearbeiten).\n" +
                            "3. Ziehe die Kachel 'Blackout' nach oben in deine aktive Leiste.\n\n" +
                            "Sobald platziert, genügt 1 Wisch und 1 Fingertipp in YouTube, Browser oder Podcast, um das Display sofort abzudunkeln.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF9CA3AF)
                    )
                }
            }

            // Settings & Customization
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF151722)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Optionen",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Hinweis bei Antippen",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = "Blendet 'Doppeltippen zum Entsperren' bei einfacher Berührung kurz ein",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF9CA3AF)
                            )
                        }
                        Switch(
                            checked = showHintOnTap,
                            onCheckedChange = {
                                showHintOnTap = it
                                config.showHintOnTap = it
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF6366F1)
                            )
                        )
                    }

                    HorizontalDivider(color = Color(0xFF232635))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Dezente Uhrzeit einblenden",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = "Zeigt eine schwach leuchtende Uhrzeit in der Bildschirmmitte",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF9CA3AF)
                            )
                        }
                        Switch(
                            checked = showSubtleClock,
                            onCheckedChange = {
                                showSubtleClock = it
                                config.showSubtleClock = it
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF6366F1)
                            )
                        )
                    }
                }
            }

            // Benefits Grid
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Vorteile von Blackout",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF6B7280)
                )
                InfoRow(
                    icon = Icons.Default.Bolt,
                    title = "0 Watt Displayverbrauch",
                    desc = "Auf OLED-Displays sind reine schwarze Pixel physikalisch abgeschaltet."
                )
                InfoRow(
                    icon = Icons.Default.Lock,
                    title = "Vollständige Taschensperre",
                    desc = "Schützt zuverlässig vor versehentlichen Berührungen in der Hosentasche."
                )
                InfoRow(
                    icon = Icons.Default.BatteryChargingFull,
                    title = "0% Hintergrundprozesse",
                    desc = "Sobald entsperrt, läuft absolut kein Dienst oder Worker im Hintergrund."
                )
            }
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, desc: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF12141D)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E2235)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFFA5B4FC),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9CA3AF)
                )
            }
        }
    }
}
