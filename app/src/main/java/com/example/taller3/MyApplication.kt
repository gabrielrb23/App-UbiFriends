package com.example.taller3

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log

class MyApplication : Application() {

    companion object {
        private const val TAG = "MyApplication"
    }

    override fun onCreate() {
        super.onCreate()

        // Crear canales de notificación
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Canal para notificaciones de disponibilidad
            val disponibilidadChannel = NotificationChannel(
                "disponibilidad_canal",
                "Notificaciones de Disponibilidad",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifica cuando un usuario está disponible"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }

            // Obtenemos el NotificationManager
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Registramos el canal
            notificationManager.createNotificationChannel(disponibilidadChannel)

            Log.d(TAG, "Canal de notificaciones 'disponibilidad_canal' creado")
        }
    }
}