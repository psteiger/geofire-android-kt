/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.firebase.geofire.geohash

import com.firebase.geofire.geometry.*

// The default precision of a geohash
private const val DEFAULT_PRECISION = 10
// The maximal precision of a geohash
internal const val MAX_PRECISION = 22

@JvmInline
value class GeoHash(val value: String) : Comparable<GeoHash> {
    override fun compareTo(other: GeoHash): Int = value compareTo other.value

    init {
        require(value.isNotEmpty() && value.isValidBase32()) {
            "Not a valid geoHash: $value"
        }
    }
}

@JvmInline
value class GeoHashPrecision(val value: Int) {
    init {
        require(value in 1..MAX_PRECISION) {
            "Precision of a GeoHash must be a value between 1 and $MAX_PRECISION."
        }
    }
}

/**
 * Converts a lat/lng location into a GeoHash with specified precision.
 */
fun GeoLocation.geoHash(precision: GeoHashPrecision = GeoHashPrecision(10)): GeoHash =
    GeoHash(latitude, longitude, precision)

fun GeoHash.toGeoLocation(): GeoLocation {
    val decoded = value
        .map { it.toBase32Value() }
        .fold(0L) { acc, base32char -> (acc shl BITS_PER_BASE32_CHAR) + base32char }
    val numBits = value.length * BITS_PER_BASE32_CHAR
    var minLng = -180.0
    var maxLng = 180.0
    var minLat = -90.0
    var maxLat = 90.0
    for (i in 0 until numBits) {
        // Get the high bit
        val bit = decoded shr (numBits - i - 1) and 1
        // Even bits are longitude, odd bits are latitude
        if (i % 2 == 0) {
            if (bit == 1L) {
                minLng = (minLng + maxLng) / 2
            } else {
                maxLng = (minLng + maxLng) / 2
            }
        } else {
            if (bit == 1L) {
                minLat = (minLat + maxLat) / 2
            } else {
                maxLat = (minLat + maxLat) / 2
            }
        }
    }
    val lat = (minLat + maxLat) / 2
    val lng = (minLng + maxLng) / 2
    return lat.latitude + lng.longitude
}

fun GeoHash(
    latitude: Latitude,
    longitude: Longitude,
    precision: GeoHashPrecision = GeoHashPrecision(DEFAULT_PRECISION)
): GeoHash = buildGeoHash(latitude, longitude, precision)

private fun buildGeoHash(
    latitude: Latitude,
    longitude: Longitude,
    precision: GeoHashPrecision
) = GeoHashBuilder(latitude, longitude).build(precision)

private class GeoHashBuilder(val latitude: Latitude, val longitude: Longitude) {
    val latitudeRange = doubleArrayOf(-90.0, 90.0)
    val longitudeRange = doubleArrayOf(-180.0, 180.0)
}

private fun GeoHashBuilder.build(precision: GeoHashPrecision) =
    CharArray(precision.value, ::geoHashCharAt)
        .concatToString()
        .asGeoHash()

private fun GeoHashBuilder.geoHashCharAt(i: Int) =
    (0 until BITS_PER_BASE32_CHAR)
        .fold(0) { acc, j -> hash(acc, i, j) }
        .toBase32Char()

private fun GeoHashBuilder.hash(acc: Int, i: Int, j: Int): Int {
    val even = (i * BITS_PER_BASE32_CHAR + j) % 2 == 0
    val value = if (even) longitude.value else latitude.value
    val range = if (even) longitudeRange else latitudeRange
    val mid = (range[0] + range[1]) / 2
    val newMidIndex = if (value > mid) 0 else 1
    val increase = if (value > mid) 1 else 0
    range[newMidIndex] = mid
    return (acc shl 1) + increase
}

private fun String.asGeoHash() = GeoHash(this)

private fun String.isValidBase32(): Boolean = matches("^[$BASE32_CHARS]*$".toRegex())
