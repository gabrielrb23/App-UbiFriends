package com.example.taller3

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.taller3.databinding.ActivityMapsBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import android.Manifest
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class MapsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MapsActivity"
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1002
    }

    private lateinit var binding: ActivityMapsBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var myRef: DatabaseReference

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var userMarker: Marker? = null
    private var primeraUbicacion = true
    private var isAvailable = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(applicationContext, getSharedPreferences("osm_prefs", MODE_PRIVATE))
        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Verificar permisos de notificación explícitamente
        verificarPermisoNotificaciones()

        // Iniciar servicio de notificaciones
        iniciarServicioDisponibilidad()

        // Asegúrate de que hay ActionBar o Toolbar
        if (supportActionBar == null) {
            // Si no hay ActionBar, verifica si el tema tiene ActionBar
            Toast.makeText(this, "La ActionBar no está disponible", Toast.LENGTH_SHORT).show()
        }

        // Set up action bar
        supportActionBar?.title = "Mapa"
        supportActionBar?.subtitle = "Ubicación actual"
        supportActionBar?.setDisplayHomeAsUpEnabled(false) // Para mostrar que tenemos opciones

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        verificarYSolicitarPermisos()

        // Create notification channel
        val channel = NotificationChannel(
            "disponibilidad_canal",
            "Notificaciones de Disponibilidad",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifica cuando un usuario esta disponible"
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)

        auth = FirebaseAuth.getInstance()
        myRef = FirebaseDatabase.getInstance().getReference("Users")

        // Update availability status when activity starts
        val userId = auth.currentUser!!.uid
        myRef.child(userId).child("disponible").get().addOnSuccessListener { dataSnapshot ->
            isAvailable = dataSnapshot.getValue(Boolean::class.java) ?: false
            // Invalidate options menu to reflect current availability
            invalidateOptionsMenu()
        }.addOnFailureListener {
            Toast.makeText(this, "No se pudo obtener el estado de disponibilidad", Toast.LENGTH_SHORT).show()
        }

        // Start service to listen to other users' states
        val serviceIntent = Intent(this, DisponibilidadService::class.java)
        DisponibilidadService.enqueueWork(this, serviceIntent)

        val map = binding.osmMap
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        addLocations(map, this)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.maps_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        // Update the availability menu item text based on current state
        menu.findItem(R.id.action_toggle_availability)?.title =
            if (isAvailable) "Desconectarse" else "Conectarse"
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_toggle_availability -> {
                // Toggle availability status
                isAvailable = !isAvailable
                myRef.child(auth.currentUser!!.uid)
                    .child("disponible")
                    .setValue(isAvailable)

                // Show toast to indicate status change
                Toast.makeText(
                    this,
                    if (isAvailable) "Estás ahora disponible" else "Estás desconectado",
                    Toast.LENGTH_SHORT
                ).show()

                // Refresh menu to update availability text
                invalidateOptionsMenu()
                true
            }
            R.id.action_users_list -> {
                // Launch Users Activity
                val intent = Intent(this, UsersActivity::class.java)
                startActivity(intent)
                true
            }
            R.id.action_logout -> {
                // Logout logic
                locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
                auth.signOut()
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // Rest of the methods remain the same as in the previous implementation...
    // (verificarYSolicitarPermisos(), configurarActualizaciones(),
    // actualizarMarker(), leerArchivo(), addLocations(),
    // onRequestPermissionsResult(), onDestroy())

    // The methods below are copied from the previous implementation
    private fun verificarYSolicitarPermisos() {
        val ubicacionPermitida = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!ubicacionPermitida) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1001)
        } else {
            configurarActualizaciones()
        }

        // Verifica permiso de notificaciones (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notificacionesPermitidas = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!notificacionesPermitidas) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1002)
            }
        }
    }

    private fun configurarActualizaciones() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000L)
            .setMinUpdateIntervalMillis(5000L)
            .setWaitForAccurateLocation(true)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation ?: return
                val geoPoint = GeoPoint(location.latitude, location.longitude)
                actualizarMarker(geoPoint)
            }
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
        }
    }

    private fun actualizarMarker(geoPoint: GeoPoint) {
        if (userMarker == null) {
            userMarker = Marker(binding.osmMap).apply {
                title = "Ubicación Actual"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = ContextCompat.getDrawable(this@MapsActivity, R.drawable.ic_mylocation)
            }
            binding.osmMap.overlays.add(userMarker)
        }

        userMarker?.position = geoPoint

        if (primeraUbicacion) {
            val locations = leerArchivo(applicationContext)
            var minLat = geoPoint.latitude
            var maxLat = geoPoint.latitude
            var minLon = geoPoint.longitude
            var maxLon = geoPoint.longitude

            for (location in locations) {
                minLat = minOf(minLat, location.latitude)
                maxLat = maxOf(maxLat, location.latitude)
                minLon = minOf(minLon, location.longitude)
                maxLon = maxOf(maxLon, location.longitude)
            }

            val margenLat = (maxLat - minLat) * 0.1
            val margenLon = (maxLon - minLon) * 0.1

            val boundingBox = org.osmdroid.util.BoundingBox(
                maxLat + margenLat,
                maxLon + margenLon,
                minLat - margenLat,
                minLon - margenLon
            )
            binding.osmMap.zoomToBoundingBox(boundingBox, true)
            primeraUbicacion = false
        }

        val currentUser = auth.currentUser ?: return
        val userId = currentUser.uid

        myRef.child(userId).child("latitud").setValue(geoPoint.latitude)
        myRef.child(userId).child("longitud").setValue(geoPoint.longitude)
    }

    private fun leerArchivo(context: Context): List<Location> {
        val inputStream = context.assets.open("locations.json")
        val jsonString = inputStream.bufferedReader().use { it.readText() }

        val jsonObject = JSONObject(jsonString)
        val locationsArray = jsonObject.getJSONArray("locationsArray")

        val locations = mutableListOf<Location>()
        for (i in 0 until locationsArray.length()) {
            val item = locationsArray.getJSONObject(i)
            val name = item.getString("name")
            val lat = item.getDouble("latitude")
            val lon = item.getDouble("longitude")
            locations.add(Location(name, lat, lon))
        }

        return locations
    }

    private fun addLocations(map: MapView, context: Context) {
        val locations = leerArchivo(context)
        val overlays = map.overlays

        for (location in locations) {
            val marker = Marker(map)
            marker.position = GeoPoint(location.latitude, location.longitude)
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.title = location.name
            marker.icon = ContextCompat.getDrawable(context, R.drawable.ic_locations)
            overlays.add(marker)
        }

        map.invalidate()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            1001 -> { // Ubicación
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    configurarActualizaciones()
                } else {
                    Toast.makeText(this, "Se necesita el permiso de ubicación para continuar", Toast.LENGTH_SHORT).show()
                }
            }
            1002 -> { // Notificaciones
                if (grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "No se podrán mostrar notificaciones", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
    }

    private fun verificarPermisoNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notificacionesPermitidas = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!notificacionesPermitidas) {
                // Verificar si debemos mostrar una explicación
                if (ActivityCompat.shouldShowRequestPermissionRationale(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                ) {
                    // Mostrar diálogo explicando por qué necesitamos el permiso
                    AlertDialog.Builder(this)
                        .setTitle("Permiso de notificaciones")
                        .setMessage("Necesitamos enviar notificaciones para avisarte cuando otros usuarios estén disponibles.")
                        .setPositiveButton("Aceptar") { _, _ ->
                            // Solicitar permiso
                            ActivityCompat.requestPermissions(
                                this,
                                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                                NOTIFICATION_PERMISSION_REQUEST_CODE
                            )
                        }
                        .setNegativeButton("Cancelar") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .create()
                        .show()
                } else {
                    // Solicitar permiso directamente
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        NOTIFICATION_PERMISSION_REQUEST_CODE
                    )
                }
            } else {
                Log.d(TAG, "Permiso de notificaciones ya concedido")
            }
        }
    }

    private fun iniciarServicioDisponibilidad() {
        Log.d(TAG, "Iniciando servicio de disponibilidad")
        val serviceIntent = Intent(this, DisponibilidadService::class.java)
        DisponibilidadService.enqueueWork(this, serviceIntent)
    }
}