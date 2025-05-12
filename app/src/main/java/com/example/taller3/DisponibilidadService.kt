package com.example.taller3

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.JobIntentService
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class DisponibilidadService : JobIntentService() {

    companion object {
        private const val JOB_ID = 1000
        private const val TAG = "DisponibilidadService"

        fun enqueueWork(context: Context, intent: Intent) {
            Log.d(TAG, "Iniciando servicio de disponibilidad")
            enqueueWork(context, DisponibilidadService::class.java, JOB_ID, intent)
        }
    }

    private lateinit var myRef: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private val estadosAnteriores = mutableMapOf<String, Boolean>()
    private var primerCarga = true
    private var valueEventListener: ValueEventListener? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: Servicio creado")
    }

    override fun onHandleWork(intent: Intent) {
        Log.d(TAG, "onHandleWork: Comenzando a manejar trabajo")

        auth = FirebaseAuth.getInstance()
        myRef = FirebaseDatabase.getInstance().getReference("Users")

        // Crear un listener persistente
        valueEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d(TAG, "onDataChange: Datos actualizados en Firebase, analizando cambios")

                // Para evitar que llegue una notificación apenas inicie la pantalla
                if (primerCarga) {
                    for (usuarioSnapshot in snapshot.children) {
                        val uid = usuarioSnapshot.key ?: continue
                        val disponible = usuarioSnapshot.child("disponible").getValue(Boolean::class.java) ?: false
                        estadosAnteriores[uid] = disponible
                        Log.d(TAG, "Primera carga: Usuario $uid, disponible: $disponible")
                    }
                    primerCarga = false
                    return
                }

                // Para cada usuario se compara su estado anterior al actual y se manda la notificación
                for (usuarioSnapshot in snapshot.children) {
                    val uid = usuarioSnapshot.key ?: continue
                    if (uid == auth.currentUser?.uid) {
                        Log.d(TAG, "Ignorando usuario actual: $uid")
                        continue
                    }

                    val nombre = usuarioSnapshot.child("nombre").getValue(String::class.java) ?: ""
                    val apellido = usuarioSnapshot.child("apellido").getValue(String::class.java) ?: ""
                    val disponible = usuarioSnapshot.child("disponible").getValue(Boolean::class.java) ?: false

                    val estadoAnterior = estadosAnteriores[uid] ?: false

                    Log.d(TAG, "Usuario $uid ($nombre $apellido): estado anterior=$estadoAnterior, estado actual=$disponible")

                    if (!estadoAnterior && disponible) {
                        Log.d(TAG, "¡Cambio detectado! Mostrando notificación para $nombre $apellido")
                        mostrarNotificacion(applicationContext, uid, nombre, apellido)
                    }
                    estadosAnteriores[uid] = disponible
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "onCancelled: Error en la base de datos: ${error.message}")
            }
        }

        // Se agrega el listener a la referencia de la DB
        myRef.addValueEventListener(valueEventListener!!)

        // Mantener el servicio en ejecución
        // Nota: JobIntentService se detendrá después de que onHandleWork retorne,
        // pero el ValueEventListener seguirá activo mientras la app esté en ejecución
        Log.d(TAG, "onHandleWork: Listener añadido, esperando eventos")
    }

    private fun mostrarNotificacion(context: Context, uid: String, nombre: String, apellido: String) {
        try {
            val notificationId = uid.hashCode() // Usar un ID único basado en el UID del usuario

            val intent = Intent(context, UserMapActivity::class.java).apply {
                putExtra("uid", uid)
                putExtra("nombre", "$nombre $apellido")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = PendingIntent.getActivity(
                context, notificationId, intent, pendingIntentFlags
            )

            // Construir la notificación
            val builder = NotificationCompat.Builder(context, "disponibilidad_canal")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("¡Usuario disponible!")
                .setContentText("$nombre $apellido está ahora disponible")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setVibrate(longArrayOf(0, 500, 200, 500)) // Vibración personalizada

            // Mostrar la notificación
            with(NotificationManagerCompat.from(context)) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Mostrando notificación para $nombre $apellido (ID: $notificationId)")
                    notify(notificationId, builder.build())
                } else {
                    Log.e(TAG, "No se puede mostrar la notificación: permiso denegado")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al mostrar notificación: ${e.message}", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: Servicio destruido")
        // Remover el listener para evitar fugas de memoria
        valueEventListener?.let { myRef.removeEventListener(it) }
    }
}