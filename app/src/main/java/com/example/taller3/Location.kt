package com.example.taller3

import java.io.Serializable

data class Location(
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
): Serializable {
    constructor() : this("", 0.0, 0.0)
}
