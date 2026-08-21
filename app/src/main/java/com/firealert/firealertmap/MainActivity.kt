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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.maps.android.compose.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.BitmapDescriptorFactory

// --- NASA EONET ---
data class NasaFire(val lat: Double, val lng: Double, val title: String)


suspend fun fetchNasaFires(): List<NasaFire> = withContext(Dispatchers.IO) {
    try {
        // On prend les 30 derniers jours, ouverts ET fermés, pour avoir Grèce/Espagne/Canada
        val url = java.net.URL("https://eonet.gsfc.nasa.gov/api/v3/events/geojson?category=wildfires&status=all&days=120&limit=500")
        val conn = url.openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.setRequestProperty("User-Agent", "FireAlertMap")
        val text = conn.inputStream.bufferedReader().readText()
        conn.disconnect()

        val json = org.json.JSONObject(text)
        // L'API geojson met tout dans "features", pas "events"
        val arr = json.optJSONArray("features") ?: json.optJSONArray("events") ?: org.json.JSONArray()
        val fires = mutableListOf<NasaFire>()

        for (i in 0 until arr.length()) {
            try {
                val obj = arr.getJSONObject(i)
                val props = obj.optJSONObject("properties") ?: obj
                val title = props.optString("title", "Feu NASA")

                val geom = obj.optJSONObject("geometry")
                if (geom == null) continue
                val coords = geom.optJSONArray("coordinates") ?: continue
                if (coords.length() == 0) continue

                var lon = 0.0; var lat = 0.0
                // Point = [lon, lat]
                if (coords.opt(0) is Number) {
                    lon = coords.getDouble(0); lat = coords.getDouble(1)
                } else {
                    // Polygon = [[[lon, lat], ...]]
                    val ring = coords.getJSONArray(0)
                    val point = ring.getJSONArray(0)
                    lon = point.getDouble(0); lat = point.getDouble(1)
                }
                if (lat != 0.0 && lon != 0.0) {
                    fires.add(NasaFire(lat, lon, title))
                }
            } catch (_: Exception) {}
        }
        android.util.Log.d("NASA", "NASA OK: ${fires.size} / ${arr.length()}")
        fires
    } catch (e: Exception) {
        android.util.Log.e("NASA", "Erreur totale: ${e.message}")
        e.printStackTrace()
        emptyList()
    }
}

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
    var nasaFires by remember { mutableStateOf(listOf<NasaFire>()) }

    LaunchedEffect(Unit) {
        nasaFires = fetchNasaFires()
    }
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
            // --- LES FEUX NASA ORANGE ---
            nasaFires.forEach { fire ->
                Marker(
                    state = MarkerState(position = LatLng(fire.lat, fire.lng)),
                    title = fire.title,
                    snippet = "Satellite NASA",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)
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