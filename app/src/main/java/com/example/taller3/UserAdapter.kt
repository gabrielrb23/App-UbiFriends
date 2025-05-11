package com.example.taller3

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide

class UserAdapter(
    private val context: Context,
    private val users: List<User>
) : BaseAdapter() {

    override fun getCount(): Int = users.size

    override fun getItem(position: Int): Any = users[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View
        val holder: ViewHolder

        if (convertView == null) {
            view = LayoutInflater.from(context).inflate(R.layout.user_item, parent, false)
            holder = ViewHolder(view)
            view.tag = holder
        } else {
            view = convertView
            holder = view.tag as ViewHolder
        }

        val user = users[position]
        holder.bind(user)

        return view
    }

    //Pone los atributos de usuario en la tarjeta
    private inner class ViewHolder(view: View) {
        private val profileImage: ImageView = view.findViewById(R.id.imgUser)
        private val userName: TextView = view.findViewById(R.id.tvUserName)
        private val findBtn: View = view.findViewById(R.id.findBtn)

        fun bind(user: User) {
            val nombre = """${user.nombre} ${user.apellido}"""
            userName.text = nombre
            if (!user.imagen.isNullOrEmpty()) {
                Glide.with(profileImage.context)
                    .load(user.imagen)
                    .circleCrop()
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .into(profileImage)
            } else {
                profileImage.setImageResource(R.drawable.ic_person)
            }

            findBtn.setOnClickListener {
                val intent = Intent(context, UserMapActivity::class.java)
                intent.putExtra("nombre", "${user.nombre} ${user.apellido}")
                intent.putExtra("uid", user.id)
                context.startActivity(intent)
            }
        }
    }
}