package com.firealert.firealertmap

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.room.Room
import com.firealert.firealertmap.data.FireDatabase
import com.firealert.firealertmap.data.FireReportLocal
import com.firealert.firealertmap.ui.theme.FireAlertMapTheme
import com.google.android.gms.location.LocationServices
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
    val db = remember {
        Room.databaseBuilder(context, FireDatabase::class.java, "fire_db").build()
    }
    val firestore = remember { FirebaseFirestore.getInstance() }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    var fireReports by remember { mutableStateOf<List<FireReportLocal>>(emptyList()) }
    var showReportScreen by remember { mutableStateOf(false) }
    var selectedPosition by remember { mutableStateOf<LatLng?>(null) }
    var hasLocationPermission by remember { mutableStateOf(false) }

    val defaultLocation = LatLng(46.603354, 1.888334)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLocation, 6f)
    }

    // Launcher permission
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasLocationPermission = granted
    }

    LaunchedEffect(Unit) {
        hasLocationPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasLocationPermission) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        fireReports = db.fireDao().getAll()
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    selectedPosition = cameraPositionState.position.target
                    showReportScreen = true
                },
                icon = {
                    Icon(
                        Icons.Filled.LocalFireDepartment,
                        contentDescription = null,
                        tint = Color.White
                    )
                },
                text = {
                    Text(
                        "Signaler un feu",
                        color = Color.White
                    )
                },
                containerColor = Color(0xFFD32F2F), // Rouge alerte
                contentColor = Color.White
            )
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = com.google.maps.android.compose.MapProperties(
                    isMyLocationEnabled = hasLocationPermission
                )
            ) {
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
                                    db.fireDao().insert(newReport)
                                    fireReports = db.fireDao().getAll()
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