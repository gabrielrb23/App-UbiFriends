package com.example.taller3

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.taller3.databinding.ActivityMapsBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Looper
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

    private lateinit var binding: ActivityMapsBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var myRef: DatabaseReference

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var userMarker: Marker? = null
    private var primeraUbicacion = true


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        solicitarPermisos()

        Configuration.getInstance().load(applicationContext, getSharedPreferences("osm_prefs", MODE_PRIVATE))
        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        auth = FirebaseAuth.getInstance()
        myRef = FirebaseDatabase.getInstance().getReference("Users")

        //Crea el canal de notificaciones
        val channel = NotificationChannel(
            "disponibilidad_canal",
            "Notificaciones de Disponibilidad",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifica cuando un usuario esta disponible"
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)

        //Actualizar el switch apenas inicia la actividad
        val userId = auth.currentUser!!.uid
        myRef.child(userId).child("disponible").get().addOnSuccessListener { dataSnapshot ->
            val disponible = dataSnapshot.getValue(Boolean::class.java) ?: false
            binding.statusBtn.isChecked = disponible
        }.addOnFailureListener {
            Toast.makeText(this, "No se pudo obtener el estado de disponibilidad", Toast.LENGTH_SHORT).show()
        }

        //Inicia el servicio para escuchar estados de otros usuarios
        val serviceIntent = Intent(this, DisponibilidadService::class.java)
        DisponibilidadService.enqueueWork(this, serviceIntent)

        val map = binding.osmMap
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        addLocations(map, this)

        binding.logoutBtn.setOnClickListener {
            locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
            auth.signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        binding.usersBtn.setOnClickListener {
            val intent = Intent(this, UsersActivity::class.java)
            startActivity(intent)
        }

        binding.statusBtn.setOnCheckedChangeListener { _, isChecked ->
            val estado = isChecked
            myRef.child(auth.currentUser!!.uid)
                .child("disponible")
                .setValue(estado)
        }
    }

    private fun configurarActualizaciones() {
        //Configura las solictudes de ubicacion
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000L)
            .setMinUpdateIntervalMillis(5000L)
            .setWaitForAccurateLocation(true)
            .build()

        //Configura que hacer cuando recibe una actualizacion
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation ?: return
                val geoPoint = GeoPoint(location.latitude, location.longitude)
                actualizarMarker(geoPoint)
            }
        }

        //Si tiene el permiso empieza a solicitar actualizaciones
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

        //Para la primera vez que se abre el mapa, mostrar los puntos del archivo y la pocision del usuario
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

        //Actualizar su latitud y longitud
        myRef.child(auth.currentUser!!.uid)
            .child("latitud")
            .setValue(geoPoint.latitude)
        myRef.child(auth.currentUser!!.uid)
            .child("longitud")
            .setValue(geoPoint.longitude)
    }

    //Metodo que lee el archivo y crea objetos de Location
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

    //Metodo que añade los lugares del archivo al mapa
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

    private fun solicitarPermisos() {
        val permisosNecesarios = mutableListOf<String>()

        // Verifica si falta el permiso de ubicación
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permisosNecesarios.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        // Verifica si falta el permiso de notificaciones (solo si Android 13 o superior)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            permisosNecesarios.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Si hay permisos faltantes, pedirlos
        if (permisosNecesarios.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permisosNecesarios.toTypedArray(), 1000)
        } else {
            configurarActualizaciones()
        }
    }


    //Metodo que pide los permisos de notificacion y ubicacion
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 1000) {
            var permisoUbicacionConcedido = false

            permissions.forEachIndexed { index, permiso ->
                if (permiso == Manifest.permission.ACCESS_FINE_LOCATION) {
                    if (grantResults[index] == PackageManager.PERMISSION_GRANTED) {
                        permisoUbicacionConcedido = true
                    } else {
                        Toast.makeText(this, "Se necesita el permiso de ubicación para continuar", Toast.LENGTH_SHORT).show()
                    }
                }

                if (permiso == Manifest.permission.POST_NOTIFICATIONS) {
                    if (grantResults[index] != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(this, "No se podrán mostrar notificaciones", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            if (permisoUbicacionConcedido) {
                configurarActualizaciones()
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
    }
}
