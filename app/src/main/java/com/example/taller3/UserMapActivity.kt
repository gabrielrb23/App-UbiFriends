package com.example.taller3

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.taller3.databinding.ActivityUserMapBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import android.Manifest
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class UserMapActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserMapBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var myRef: DatabaseReference

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var userMarker: Marker? = null
    private var otherUserMarker: Marker? = null
    private var primeraUbicacion = true
    private var lineaEntreUsuarios: org.osmdroid.views.overlay.Polyline? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(applicationContext, getSharedPreferences("osm_prefs", MODE_PRIVATE))
        binding = ActivityUserMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        //Si tiene permiso, inicien las actualizaciones de ubicacion
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1001)
        } else {
            configurarActualizaciones()
        }

        auth = FirebaseAuth.getInstance()
        //Si no hay usuario logueado se envia a la pantalla de Login
        if (auth.currentUser == null) {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        myRef = FirebaseDatabase.getInstance().getReference("Users")

        val map = binding.osmMap
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)

        val otherUserId = intent.getStringExtra("uid") ?: ""
        binding.TVNombre.text = intent.getStringExtra("nombre")

        //Se escucha la ubicacion del otro usuario
        myRef.child(otherUserId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lat = snapshot.child("latitud").getValue(Double::class.java) ?: 0.0
                val lon = snapshot.child("longitud").getValue(Double::class.java) ?: 0.0
                val geoPoint = GeoPoint(lat, lon)
                actualizarMarkerOther(geoPoint)
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(
                    this@UserMapActivity,
                    "Error al escuchar usuario",
                    Toast.LENGTH_SHORT
                ).show()
            }

        })

        binding.backBtn.setOnClickListener {
            val intent = Intent(this, UsersActivity::class.java)
            startActivity(intent)
        }
    }

    //Metodo que configura las actualizaciones de ubicacion del usuario logueado
    private fun configurarActualizaciones() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000L)
            .setMinUpdateIntervalMillis(10000L)
            .setMinUpdateDistanceMeters(10f)
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

    //Metodo que actualiza el marker del usuario
    private fun actualizarMarker(geoPoint: GeoPoint) {
        if (userMarker == null) {
            userMarker = Marker(binding.osmMap).apply {
                title = " Tu Ubicación Actual"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = ContextCompat.getDrawable(this@UserMapActivity, R.drawable.ic_mylocation)
            }
            binding.osmMap.overlays.add(userMarker)
        }

        userMarker?.position = geoPoint

        if (primeraUbicacion) {
            binding.osmMap.controller.setZoom(15.0)
            binding.osmMap.controller.setCenter(geoPoint)
            primeraUbicacion = false
        }

        myRef.child(auth.currentUser!!.uid)
            .child("latitud")
            .setValue(geoPoint.latitude)
        myRef.child(auth.currentUser!!.uid)
            .child("longitud")
            .setValue(geoPoint.longitude)
        trazarLinea()
    }

    //Metodo que actualiza el marker del otro usuario
    private fun actualizarMarkerOther(geoPoint: GeoPoint) {
        if (otherUserMarker == null) {
            otherUserMarker = Marker(binding.osmMap).apply {
                title = "Ubicación Actual de " + binding.TVNombre.text
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = ContextCompat.getDrawable(this@UserMapActivity, R.drawable.ic_otheruser)
            }
            binding.osmMap.overlays.add(otherUserMarker)
        }

        otherUserMarker?.position = geoPoint
        binding.osmMap.invalidate()
        trazarLinea()
    }

    //Metodo que traza la linea entre los dos usuarios
    private fun trazarLinea() {
        val punto1 = userMarker?.position
        val punto2 = otherUserMarker?.position

        if (punto1 != null && punto2 != null) {
            // Calcular la distancia entre los dos puntos
            val distancia = calcularDistancia(punto1, punto2)

            // Mostrar la distancia en un Toast
            Toast.makeText(
                this,
                "Distancia: ${String.format("%.2f", distancia)} km",
                Toast.LENGTH_SHORT
            ).show()

            if (lineaEntreUsuarios == null) {
                lineaEntreUsuarios = org.osmdroid.views.overlay.Polyline().apply {
                    outlinePaint.color = android.graphics.Color.BLUE
                    outlinePaint.strokeWidth = 5f
                }
                binding.osmMap.overlays.add(lineaEntreUsuarios)
            }

            lineaEntreUsuarios?.setPoints(listOf(punto1, punto2))
            binding.osmMap.invalidate()

            val norte = maxOf(punto1.latitude, punto2.latitude)
            val sur = minOf(punto1.latitude, punto2.latitude)
            val este = maxOf(punto1.longitude, punto2.longitude)
            val oeste = minOf(punto1.longitude, punto2.longitude)

            val margenLat = (norte - sur) * 0.2
            val margenLon = (este - oeste) * 0.2

            val boundingBox = org.osmdroid.util.BoundingBox(
                norte + margenLat,
                este + margenLon,
                sur - margenLat,
                oeste - margenLon
            )

            binding.osmMap.zoomToBoundingBox(boundingBox, true)
        }
    }

    // Metodo para calcular la distancia entre dos puntos usando la fórmula de Haversine
    private fun calcularDistancia(punto1: GeoPoint, punto2: GeoPoint): Double {
        val radioTierra = 6371.0 // Radio de la Tierra en kilómetros

        // Convertir latitudes y longitudes de grados a radianes
        val lat1Rad = Math.toRadians(punto1.latitude)
        val lon1Rad = Math.toRadians(punto1.longitude)
        val lat2Rad = Math.toRadians(punto2.latitude)
        val lon2Rad = Math.toRadians(punto2.longitude)

        // Diferencias de latitud y longitud
        val dLat = lat2Rad - lat1Rad
        val dLon = lon2Rad - lon1Rad

        // Fórmula de Haversine
        val a = sin(dLat / 2).pow(2.0) +
                cos(lat1Rad) * cos(lat2Rad) *
                sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        // Distancia en kilómetros
        return radioTierra * c
    }

    override fun onDestroy() {
        super.onDestroy()
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
    }
}