package com.firebase.geofire.geometry

@JvmInline
value class Latitude(val value: Double) {
    init {
        require(value in -90.0..90.0) {
            "Latitude must be between -90 and 90, was: $value"
        }
    }
}

val Double.latitude: Latitude get() = Latitude(this)

@JvmInline
value class Longitude(val value: Double) {
    init {
        require(value in -180.0..180.0) {
            "Longitude must be between -180 and 180, was: $value"
        }
    }
}

val Double.longitude: Longitude get() = Longitude(this)

operator fun Latitude.plus(other: Latitude): Latitude = this + other.value
operator fun Latitude.minus(other: Latitude): Latitude = this - other.value
operator fun Latitude.plus(other: Double): Latitude = (value + other).coerceAtMost(90.0).latitude
operator fun Latitude.minus(other: Double): Latitude = (value - other).coerceAtLeast(-90.0).latitude
operator fun Longitude.plus(other: Longitude): Longitude = this + other.value
operator fun Longitude.minus(other: Longitude): Longitude = this - other.value
operator fun Longitude.plus(other: Double): Longitude = (value + other).wrap().longitude
operator fun Longitude.minus(other: Double): Longitude = (value - other).wrap().longitude

private fun Double.wrap(): Double {
    if (this in -180.0..180.0) return this
    val adjusted = this + 180
    return when {
        adjusted > 0 -> (adjusted % 360.0) - 180
        else -> 180 - (-adjusted % 360)
    }
}
