package com.example.leanangletracker.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.leanangletracker.UiState
import com.example.leanangletracker.ui.components.InfoChip

@Composable
internal fun SettingsScreen(
    state: UiState,
    onBack: () -> Unit,
    onToggleInvertLean: (Boolean) -> Unit,
    onSetHistoryWindow: (Int) -> Unit,
    onResetExtrema: () -> Unit,
    onStartCalibration: () -> Unit
) {
    var showInfo by remember { mutableStateOf(false) }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            confirmButton = { Button(onClick = { showInfo = false }) { Text("OK") } },
            title = { Text("Info") },
            text = { Text("Weniger ist mehr: Diese Seite enthält nur die wichtigsten Optionen.") }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                InfoChip { showInfo = true }
            }
            Button(onClick = onBack) { Text("Zurück") }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Lean invertieren", fontSize = 18.sp)
                    Switch(checked = state.invertLeanAngle, onCheckedChange = onToggleInvertLean)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("History", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(onClick = { onSetHistoryWindow(state.historyWindowSeconds - 5) }) { Text("-") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${state.historyWindowSeconds}s", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onSetHistoryWindow(state.historyWindowSeconds + 5) }) { Text("+") }
                }
            }
        }

        Button(onClick = onResetExtrema, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Max-Werte zurücksetzen", fontSize = 18.sp)
        }

        Button(onClick = onStartCalibration, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Kalibrierung neu starten", fontSize = 18.sp)
        }
    }
}
