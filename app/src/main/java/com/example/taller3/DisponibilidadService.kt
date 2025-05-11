package com.example.taller3

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.app.JobIntentService
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class DisponibilidadService : JobIntentService() {

    //Funcion que se llama para iniciar el servicio
    companion object {
        private const val JOB_ID = 1000
        fun enqueueWork(context: Context, intent: Intent) {
            enqueueWork(context, DisponibilidadService::class.java, JOB_ID, intent)
        }
    }

    private lateinit var myRef: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private val estadosAnteriores = mutableMapOf<String, Boolean>()
    private var primerCarga = true

    override fun onHandleWork(intent: Intent) {
        auth = FirebaseAuth.getInstance()
        myRef = FirebaseDatabase.getInstance().getReference("Users")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                //Para evitar que llegue una notificacion apenas inicie la pantalla
                if (primerCarga) {
                    for (usuarioSnapshot in snapshot.children) {
                        val uid = usuarioSnapshot.key ?: continue
                        val disponible = usuarioSnapshot.child("disponible").getValue(Boolean::class.java) ?: false
                        estadosAnteriores[uid] = disponible
                    }
                    primerCarga = false
                    return
                }

                //Para cada usuario se compara su estado anterior al actual y se manda la notificacion
                for (usuarioSnapshot in snapshot.children) {
                    val uid = usuarioSnapshot.key ?: continue
                    if (uid == auth.currentUser?.uid) continue

                    val nombre = usuarioSnapshot.child("nombre").getValue(String::class.java) ?: ""
                    val apellido = usuarioSnapshot.child("apellido").getValue(String::class.java) ?: ""
                    val disponible = usuarioSnapshot.child("disponible").getValue(Boolean::class.java) ?: false

                    val estadoAnterior = estadosAnteriores[uid] ?: false

                    if (!estadoAnterior && disponible) {
                        mostrarNotificacion(applicationContext, uid, nombre, apellido)
                    }
                    estadosAnteriores[uid] = disponible
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        //Se agrega el listener a la referencia de la DB
        myRef.addValueEventListener(listener)
    }

    private fun mostrarNotificacion(context : Context, uid: String, nombre: String, apellido: String) {
        val intent = Intent(context, UserMapActivity::class.java).apply {
            putExtra("uid", uid)
            putExtra("nombre", "$nombre $apellido")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        //Construir la notificacion
        val builder = NotificationCompat.Builder(this, "disponibilidad_canal")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Parece que hay alguien por ahi...")
            .setContentText("$nombre $apellido está disponible!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(this)) {
            //Mostrar la notifiacion si se tienen permisos
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED){
                    notify(1, builder.build())
            }
        }
    }
}
