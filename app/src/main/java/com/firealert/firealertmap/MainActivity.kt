package com.firealert.firealertmap

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.firealert.firealertmap.data.FireReportLocal
import com.firealert.firealertmap.ui.theme.FireAlertMapTheme
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.FirebaseFirestore
import com.google.maps.android.compose.*
import com.google.android.gms.location.LocationServices

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
    var hasLocationPermission by remember { mutableStateOf(false) }
    var allFiresFromFirebase by remember { mutableStateOf(listOf<FireReportLocal>()) }
    var showReportScreen by remember { mutableStateOf(false) }
    var selectedPosition by remember { mutableStateOf<LatLng?>(null) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(44.0, 0.5), 6f)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted -> hasLocationPermission = isGranted }

    LaunchedEffect(Unit) {
        FirebaseFirestore.getInstance().collection("fire_reports")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val fires = snapshot.documents.mapNotNull { doc ->
                        try {
                            FireReportLocal(
                                id = doc.id.hashCode(),
                                latitude = doc.getDouble("latitude") ?: 0.0,
                                longitude = doc.getDouble("longitude") ?: 0.0,
                                description = doc.getString("description") ?: "",
                                timestamp = doc.getLong("timestamp") ?: 0L
                            )
                        } catch (e: Exception) { null }
                    }
                    allFiresFromFirebase = fires
                }
            }
        hasLocationPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasLocationPermission) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    Scaffold(
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    // On prend ta vraie position GPS
                    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                            if (location != null) {
                                selectedPosition = LatLng(location.latitude, location.longitude)
                                showReportScreen = true
                            } else {
                                // Si GPS pas prêt, on prend le centre de la carte en secours
                                selectedPosition = cameraPositionState.position.target
                                showReportScreen = true
                            }
                        }
                    } else {
                        selectedPosition = cameraPositionState.position.target
                        showReportScreen = true
                    }
                },
                icon = { Icon(Icons.Filled.LocalFireDepartment, contentDescription = null) },
                text = { Text("Signaler un feu") },
                containerColor = Color.Red,
                contentColor = Color.White
            )
        }
    ) { padding ->
        GoogleMap(
            modifier = Modifier.fillMaxSize().padding(padding),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
            uiSettings = MapUiSettings(myLocationButtonEnabled = true)
        ) {
            allFiresFromFirebase.forEach { fire ->
                Marker(
                    state = MarkerState(position = LatLng(fire.latitude, fire.longitude)),
                    title = "Feu signalé",
                    snippet = fire.description
                )
            }
        }
        if (showReportScreen && selectedPosition != null) {
            AlertDialog(
                onDismissRequest = { showReportScreen = false },
                title = { Text("Signaler un feu") },
                text = {
                    ReportFireScreen(
                        latLng = selectedPosition!!,
                        onCancel = { showReportScreen = false },
                        onConfirm = { desc ->
                            // Ici tu gardes ton code qui envoie sur Firebase
                            // que tu avais déjà dans l'ancien ReportFireScreen
                            val data = hashMapOf(
                                "latitude" to selectedPosition!!.latitude,
                                "longitude" to selectedPosition!!.longitude,
                                "description" to desc,
                                "timestamp" to System.currentTimeMillis()
                            )
                            FirebaseFirestore.getInstance().collection("fire_reports").add(data)
                            showReportScreen = false
                        }
                    )
                },
                confirmButton = {},
                dismissButton = {}
            )
        }
    }
}