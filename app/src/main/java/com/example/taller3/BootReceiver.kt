package com.example.taller3

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Receptor de broadcast para reiniciar el servicio cuando el dispositivo se inicia
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Dispositivo iniciado, iniciando servicio de disponibilidad")

            // Verificar si hay un usuario autenticado antes de iniciar el servicio
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            if (auth.currentUser != null) {
                Log.d(TAG, "Usuario autenticado, iniciando servicio")

                val serviceIntent = Intent(context, DisponibilidadService::class.java)
                DisponibilidadService.enqueueWork(context, serviceIntent)
            } else {
                Log.d(TAG, "No hay usuario autenticado, no se inicia el servicio")
            }
        }
    }
}