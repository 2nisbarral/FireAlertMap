package com.firealert.firealertmap

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.room.Room
import com.firealert.firealertmap.data.FireDatabase
import com.firealert.firealertmap.data.FireReportLocal
import com.firealert.firealertmap.ui.theme.FireAlertMapTheme
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.FirebaseFirestore
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FireAlertMapTheme {
                FireAlertMapApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FireAlertMapApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Base de données locale
    val db = remember {
        Room.databaseBuilder(context, FireDatabase::class.java, "fire_db").build()
    }
    val firestore = remember { FirebaseFirestore.getInstance() }

    var fireReports by remember { mutableStateOf<List<FireReportLocal>>(emptyList()) }
    var showReportScreen by remember { mutableStateOf(false) }
    var selectedPosition by remember { mutableStateOf<LatLng?>(null) }

    // Position par défaut : France (centre)
    val defaultLocation = LatLng(46.603354, 1.888334)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLocation, 6f)
    }

    // Permission localisation
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        // Charger les signalements locaux
        fireReports = db.fireDao().getAll()
        // Charger depuis Firebase aussi
        firestore.collection("fire_reports").get().addOnSuccessListener { result ->
            // Tu pourras fusionner ici si besoin
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = {
                selectedPosition = cameraPositionState.position.target
                showReportScreen = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Signaler")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState
            ) {
                // Afficher tous les feux signalés
                fireReports.forEach { report ->
                    Marker(
                        state = MarkerState(position = LatLng(report.latitude, report.longitude)),
                        title = report.description,
                        snippet = "Signalé"
                    )
                }
            }

            if (showReportScreen && selectedPosition != null) {
                AlertDialog(
                    onDismissRequest = { showReportScreen = false },
                    title = { Text("Signaler un incendie") },
                    text = {
                        ReportFireScreen(
                            latLng = selectedPosition!!,
                            onConfirm = { description ->
                                scope.launch {
                                    val newReport = FireReportLocal(
                                        latitude = selectedPosition!!.latitude,
                                        longitude = selectedPosition!!.longitude,
                                        description = description
                                    )
                                    // 1. Local
                                    db.fireDao().insert(newReport)
                                    fireReports = db.fireDao().getAll()

                                    // 2. Firebase
                                    firestore.collection("fire_reports").add(
                                        mapOf(
                                            "latitude" to newReport.latitude,
                                            "longitude" to newReport.longitude,
                                            "description" to newReport.description,
                                            "timestamp" to newReport.timestamp
                                        )
                                    )
                                    showReportScreen = false
                                }
                            },
                            onCancel = { showReportScreen = false }
                        )
                    },
                    confirmButton = {},
                    dismissButton = {}
                )
            }
        }
    }
}