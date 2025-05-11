package com.example.taller3

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.taller3.databinding.ActivityRegisterBinding
import com.google.firebase.auth.FirebaseAuth
import android.text.TextUtils
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.bumptech.glide.Glide
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var myRef: DatabaseReference

    private lateinit var imagePickerLauncher: ActivityResultLauncher<Intent>
    private var imageUri: Uri? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        myRef = FirebaseDatabase.getInstance().getReference("Users")

        //Abre el laucher de la galeria y guarda la direccion de la imagen elegida
        imagePickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val data: Intent? = result.data
                imageUri = data?.data
                Glide.with(this)
                    .load(imageUri)
                    .circleCrop()
                    .into(binding.contactImage)
            }
        }

        binding.contactImage.setOnClickListener {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED) {
                    abrirGaleria()
                } else { requestPermissions(arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES), 2001) }
            } else {
                if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    abrirGaleria()
                } else { requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), 2001) }
            }
        }

        //Crea el usuario con Authentication
        binding.registerBtn.setOnClickListener {
            if (validarInfo()) {
                val email = binding.email.text.toString()
                val password = binding.password.text.toString()
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            val userId = auth.currentUser?.uid
                            if (userId != null) {
                                //Sube la imagen a Storage
                                if (imageUri != null) {
                                    val storageRef = FirebaseStorage.getInstance().reference
                                        .child("profile_images/$userId.jpg")
                                    storageRef.putFile(imageUri!!)
                                        .addOnSuccessListener {
                                            storageRef.downloadUrl.addOnSuccessListener { uri ->
                                                val imageUrl = uri.toString()
                                                guardarUsuario(userId, imageUrl)
                                            }
                                        }
                                        .addOnFailureListener {
                                            Toast.makeText(this, "Error subiendo imagen", Toast.LENGTH_SHORT).show()
                                        }
                                } else {
                                    guardarUsuario(userId, "")
                                }
                            } else {
                                Toast.makeText(
                                    baseContext,
                                    "User ID no encontrado.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        } else {
                            Toast.makeText(baseContext, "Registro Fallo.", Toast.LENGTH_LONG).show()
                        }
                    }
            }
        }
    }

    //Metodo que se asegura que los campos sean validos
    private fun validarInfo(): Boolean {
        var valid = true
        val email = binding.email.text.toString()
        if (TextUtils.isEmpty(email)) {
            binding.email.error = "Obligatorio."
            valid = false
        } else {
            binding.email.error = null
            if (!email.contains("@") || !email.contains(".") || email.length < 5) {
                Toast.makeText(this, "Debe ingresar un correo valido", Toast.LENGTH_SHORT).show()
                valid = false
            }
        }
        val password = binding.password.text.toString()
        if (TextUtils.isEmpty(password)) {
            binding.password.error = "Obligatorio."
            valid = false
        } else {
            binding.password.error = null
        }
        return valid
    }

    //Metodo que guarda el usuario en Firebase
    private fun guardarUsuario(userId: String, fotoUrl: String) {
        val user = User(
            id = userId,
            nombre = binding.firstName.text.toString(),
            apellido = binding.lastName.text.toString(),
            email = binding.email.text.toString(),
            imagen = fotoUrl,
            cedula = binding.idNumber.text.toString().toInt(),
            latitud = binding.latitude.text.toString().toDouble(),
            longitud = binding.longitude.text.toString().toDouble()
        )
        myRef.child(userId).setValue(user)
            .addOnCompleteListener { dbTask ->
                if (dbTask.isSuccessful) {
                    Toast.makeText(baseContext, "Registro Exitoso!", Toast.LENGTH_LONG).show()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(baseContext, "Error al guardar su información", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun abrirGaleria() {
        val intent = Intent(Intent.ACTION_PICK).apply {
            type = "image/*"
        }
        imagePickerLauncher.launch(intent)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 2001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            abrirGaleria()
        } else {
            Toast.makeText(this, "Permiso denegado para acceder a la galería", Toast.LENGTH_SHORT).show()
        }
    }

}