package com.focusguard.app.data

data class LocationRule(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val blockedPackages: Set<String>
)
