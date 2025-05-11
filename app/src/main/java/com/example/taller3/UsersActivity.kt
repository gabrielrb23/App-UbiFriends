package com.example.taller3

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.taller3.databinding.ActivityUsersBinding
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class UsersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUsersBinding
    private lateinit var userAdapter: UserAdapter
    private lateinit var usersList: ArrayList<User>

    lateinit var myRef: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUsersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        usersList = ArrayList()
        userAdapter = UserAdapter(this, usersList)
        binding.lvUsers.adapter = userAdapter

        myRef = FirebaseDatabase.getInstance().getReference("Users")
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

        //Se filtran los usuarios de la lista. Solo se observan si estan disponibles
        myRef.get().addOnSuccessListener { snapshot ->
            usersList.clear()
            for (userSnapshot in snapshot.children) {
                val user = userSnapshot.getValue(User::class.java)
                val uid = userSnapshot.key
                val disponible = userSnapshot.child("disponible").getValue(Boolean::class.java) ?: false

                if (user != null && uid != currentUserId && disponible) {
                    usersList.add(user)
                }
            }
            userAdapter.notifyDataSetChanged()
        }.addOnFailureListener {
            Toast.makeText(this, "Error cargando usuarios", Toast.LENGTH_SHORT).show()
        }


        binding.backBtn.setOnClickListener {
            val intent = Intent(this, MapsActivity::class.java)
            startActivity(intent)
        }
    }
}