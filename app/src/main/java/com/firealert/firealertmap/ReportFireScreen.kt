package com.firealert.firealertmap

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLng

@Composable
fun ReportFireScreen(
    latLng: LatLng,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit
) {
    var description by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Text(
            "Position: ${"%.5f".format(latLng.latitude)}, ${"%.5f".format(latLng.longitude)}",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description du feu") },
            placeholder = { Text("Ex: Fumée épaisse, forêt...") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onCancel) { Text("Annuler") }
            Button(
                onClick = { onConfirm(description) },
                enabled = description.isNotBlank()
            ) {
                Text("Signaler")
            }
        }
    }
}