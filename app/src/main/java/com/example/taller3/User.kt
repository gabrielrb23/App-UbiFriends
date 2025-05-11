package com.example.taller3

import java.io.Serializable

data class User(
    val id: String = "",
    var nombre: String = "",
    var apellido: String = "",
    var email: String = "",
    var imagen: String = "",
    var cedula: Int? = 0,
    var latitud: Double? = 0.0,
    var longitud: Double? = 0.0,
    var disponible: Boolean = false
): Serializable {
    constructor() : this("","", "", "", "", null, null, null, false)
}
