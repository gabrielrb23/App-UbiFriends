package com.example.taller3

import android.content.Intent
import android.os.Bundle
import android.service.controls.ControlsProviderService.TAG
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.taller3.databinding.ActivityLoginBinding
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.util.TextUtils

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = Firebase.auth

        //Loguear al usuario
        binding.loginBtn.setOnClickListener{
            if(validarInfo()) {
                auth.signInWithEmailAndPassword(
                    binding.emailInput.text.toString(),
                    binding.passwordInput.text.toString()
                )
                    .addOnCompleteListener(this) { task ->
                        Log.d(TAG, "signInWithEmail:onComplete:" + task.isSuccessful)
                        if (!task.isSuccessful) {
                            Log.w(TAG, "signInWithEmail:failure", task.exception)
                            Toast.makeText(this, "El inicio de sesion fallo.", Toast.LENGTH_SHORT).show()
                            updateUI(null)
                        }else{
                            Toast.makeText(this, "Inicio de sesion exitoso!", Toast.LENGTH_SHORT).show()
                            updateUI(auth.currentUser)
                        }
                    }
            }
        }

        binding.registerBtn.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }

    //Revisar que sean datos validos
    private fun validarInfo(): Boolean {
        var valid = true
        val email = binding.emailInput.text.toString()
        if (TextUtils.isEmpty(email)) {
            binding.emailInput.error = "Obligatorio."
            valid = false
        } else {
            binding.emailInput.error = null
            if (!email.contains("@") || !email.contains(".") || email.length < 5){
                Toast.makeText(this, "Debe ingresar un correo valido", Toast.LENGTH_SHORT).show()
                valid = false
            }
        }
        val password = binding.passwordInput.text.toString()
        if (TextUtils.isEmpty(password)) {
            binding.passwordInput.error = "Obligatorio."
            valid = false
        } else {
            binding.passwordInput.error = null
        }
        return valid
    }

    override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        updateUI(currentUser)
    }

    //Si ya esta logueado, mandelo de una vez al MapsActivity. Si no logueate
    private fun updateUI(currentUser: FirebaseUser?) {
        if (currentUser != null) {
            val intent = Intent(this, MapsActivity::class.java)
            startActivity(intent)
        } else {
            binding.emailInput.setText("")
            binding.passwordInput.setText("")
        }
    }

}