package com.firebase.geofire

import com.firebase.geofire.internal.*
import kotlin.math.*

public interface Circle {
    public val center: GeoLocation
    public val radius: Distance
}

public fun Circle(center: GeoLocation, radius: Distance): Circle = CircleImpl(center, radius)
private data class CircleImpl(override val center: GeoLocation, override val radius: Distance): Circle {
    override fun toString(): String = "Circle(center=$center, radius=$radius)"
}

/** A wrapper class for location coordinates. */
public interface GeoLocation {
    public val latitude: Latitude
    public val longitude: Longitude
}

public fun GeoLocation(latitude: Latitude, longitude: Longitude): GeoLocation = GeoLocationImpl(latitude, longitude)
private data class GeoLocationImpl(override val latitude: Latitude, override val longitude: Longitude): GeoLocation {
    override fun toString(): String = "GeoLocation(latitude=$latitude, longitude=$longitude)"
}

public operator fun GeoLocation.component1(): Latitude = latitude
public operator fun GeoLocation.component2(): Longitude = longitude

@JvmInline
public value class Latitude(public val value: Double) {
    init {
        require(value in -90.0..90.0) {
            "Latitude must be between -90 and 90, was: $value"
        }
    }
}

@JvmInline
public value class Longitude(public val value: Double) {
    init {
        require(value in -180.0..180.0) {
            "Longitude must be between -180 and 180, was: $value"
        }
    }
}

public val Double.latitude: Latitude get() = Latitude(this)
public val Double.longitude: Longitude get() = Longitude(this)
public operator fun Latitude.plus(other: Latitude): Latitude = this + other.value
public operator fun Latitude.minus(other: Latitude): Latitude = this - other.value
public operator fun Latitude.plus(other: Double): Latitude = (value + other).coerceAtMost(90.0).latitude
public operator fun Latitude.minus(other: Double): Latitude = (value - other).coerceAtLeast(-90.0).latitude
public operator fun Longitude.plus(other: Longitude): Longitude = this + other.value
public operator fun Longitude.minus(other: Longitude): Longitude = this - other.value
public operator fun Longitude.plus(other: Double): Longitude = (value + other).wrap().longitude
public operator fun Longitude.minus(other: Double): Longitude = (value - other).wrap().longitude
public operator fun Latitude.plus(longitude: Longitude): GeoLocation = GeoLocation(this, longitude)
public operator fun Longitude.plus(latitude: Latitude): GeoLocation = GeoLocation(latitude, this)

public infix fun GeoLocation.distanceTo(other: GeoLocation): Distance {
    val radius = (EARTH_EQ_RADIUS + EARTH_POLAR_RADIUS) / 2 // Earth's mean radius in meters
    val lat1Radians = Math.toRadians(latitude.value)
    val lat2Radians = Math.toRadians(other.latitude.value)
    val lng1Radians = Math.toRadians(longitude.value)
    val lng2Radians = Math.toRadians(other.longitude.value)
    val latDelta = lat2Radians - lat1Radians
    val lngDelta = lng2Radians - lng1Radians
    val a = sin(latDelta / 2).pow(2) +
            cos(lat1Radians) * cos(lat2Radians) *
            sin(lngDelta / 2).pow(2)
    return (radius * 2 * atan2(sqrt(a), sqrt(1 - a))).meters
}

private fun Double.wrap(): Double {
    if (this in -180.0..180.0) return this
    val adjusted = this + 180
    return when {
        adjusted > 0 -> (adjusted % 360.0) - 180
        else -> 180 - (-adjusted % 360)
    }
}

public operator fun Circle.contains(location: GeoLocation): Boolean = (center distanceTo location) < radius

public sealed interface Distance : Comparable<Distance> {
    public val value: Double
    override fun compareTo(other: Distance): Int = inMeters().value compareTo other.inMeters().value
}

@JvmInline
public value class Meters(override val value: Double) : Distance
@JvmInline
public value class Kilometers(override val value: Double) : Distance

public val Int.kilometers: Kilometers get() = toDouble().kilometers
public val Int.meters: Meters get() = toDouble().meters
public val Double.kilometers: Kilometers get() = Kilometers(this)
public val Double.meters: Meters get() = Meters(this)

public fun Distance.inMeters(): Meters = when (this) {
    is Kilometers -> (value * METERS_IN_KILOMETER).meters
    is Meters -> this
}

public fun Distance.inKilometers(): Kilometers = when (this) {
    is Kilometers -> this
    is Meters -> (value / METERS_IN_KILOMETER).kilometers
}

internal fun Distance.toLatitudeDegrees(): Double = inMeters().value / METERS_PER_DEGREE_LATITUDE

internal fun Distance.toLongitudeDegrees(latitude: Latitude): Double {
    val radians = Math.toRadians(latitude.value)
    val numerator = cos(radians) * EARTH_EQ_RADIUS * Math.PI / 180
    val denominator = 1 / sqrt(1 - EARTH_E2 * sin(radians) * sin(radians))
    val deltaDegrees = numerator * denominator
    val distanceInMeters = inMeters().value
    val result = distanceInMeters
        .takeIf { deltaDegrees < EPSILON }
        ?: (distanceInMeters / deltaDegrees)
    return result.coerceAtMost(360.0)
}

private const val METERS_IN_KILOMETER = 1000.0