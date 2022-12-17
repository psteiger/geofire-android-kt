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
package com.firebase.geofire.internal.geohash

import com.firebase.geofire.*
import com.firebase.geofire.internal.EARTH_MERIDIONAL_CIRCUMFERENCE
import com.firebase.geofire.internal.METERS_PER_DEGREE_LATITUDE
import kotlin.math.*

// The maximal number of bits precision for a geohash
private const val MAX_PRECISION_BITS = MAX_PRECISION * BITS_PER_BASE32_CHAR

internal val Circle.queries: Set<ClosedRange<GeoHash>>
    get() {
        val queryBits = bitsForBoundingBox.coerceAtLeast(1)
        val (latitude, longitude) = center
        val radiusInMeters = radius.inMeters()
        val precision = GeoHashPrecision(ceil(queryBits.toFloat() / BITS_PER_BASE32_CHAR).toInt())
        val latitudeDegrees = radiusInMeters.value / METERS_PER_DEGREE_LATITUDE
        val latitudeNorth = (latitude + latitudeDegrees)
        val latitudeSouth = (latitude - latitudeDegrees)
        val longitudeDeltaNorth = radiusInMeters.toLongitudeDegrees(latitudeNorth)
        val longitudeDeltaSouth = radiusInMeters.toLongitudeDegrees(latitudeSouth)
        val longitudeDelta = max(longitudeDeltaNorth, longitudeDeltaSouth)
        val geoHash = GeoHash(latitude, longitude, precision)
        val geoHashW = GeoHash(latitude, longitude - longitudeDelta, precision)
        val geoHashE = GeoHash(latitude, longitude + longitudeDelta, precision)
        val geoHashN = GeoHash(latitudeNorth, longitude, precision)
        val geoHashNW = GeoHash(latitudeNorth, longitude - longitudeDelta, precision)
        val geoHashNE = GeoHash(latitudeNorth, longitude + longitudeDelta, precision)
        val geoHashS = GeoHash(latitudeSouth, longitude, precision)
        val geoHashSW = GeoHash(latitudeSouth, longitude - longitudeDelta, precision)
        val geoHashSE = GeoHash(latitudeSouth, longitude + longitudeDelta, precision)
        val result = buildSet {
            add(geoHash.queries(queryBits))
            add(geoHashE.queries(queryBits))
            add(geoHashW.queries(queryBits))
            add(geoHashN.queries(queryBits))
            add(geoHashNE.queries(queryBits))
            add(geoHashNW.queries(queryBits))
            add(geoHashS.queries(queryBits))
            add(geoHashSE.queries(queryBits))
            add(geoHashSW.queries(queryBits))
            merge()
        }
        return result
    }

private val Circle.bitsForBoundingBox: Int
    get() {
        val (latitude, _) = center
        val latitudeDegreesDelta = radius.toLatitudeDegrees()
        val latitudeNorth = latitude + latitudeDegreesDelta
        val latitudeSouth = latitude - latitudeDegreesDelta
        val bitsLatitude = floor(bitsLatitude(radius)).toInt() * 2
        val bitsLongitudeNorth = floor(bitsLongitude(radius, latitudeNorth)).toInt() * 2 - 1
        val bitsLongitudeSouth = floor(bitsLongitude(radius, latitudeSouth)).toInt() * 2 - 1
        val bitsLongitude = min(bitsLongitudeNorth, bitsLongitudeSouth)
        return min(bitsLatitude, bitsLongitude)
    }

private infix fun ClosedRange<GeoHash>.canMergeWith(other: ClosedRange<GeoHash>): Boolean =
    this isPrefixOf other
            || other isPrefixOf this
            || this isSuperQueryOf other
            || other isSuperQueryOf this

private infix fun ClosedRange<GeoHash>.isPrefixOf(other: ClosedRange<GeoHash>): Boolean =
    start < other.start &&
            endInclusive >= other.start &&
            endInclusive < other.endInclusive

private infix fun ClosedRange<GeoHash>.isSuperQueryOf(other: ClosedRange<GeoHash>): Boolean =
    start <= other.start &&
            endInclusive >= other.endInclusive

private infix fun ClosedRange<GeoHash>.mergeWith(other: ClosedRange<GeoHash>): ClosedRange<GeoHash> =
    when {
        this isPrefixOf other -> start..other.endInclusive
        other isPrefixOf this -> other.start..endInclusive
        this isSuperQueryOf other -> this
        other isSuperQueryOf this -> other
        else -> error("Can't join these 2 ranges: $this, $other")
    }

private fun bitsLatitude(resolution: Distance): Double =
    (ln(EARTH_MERIDIONAL_CIRCUMFERENCE / 2 / resolution.inMeters().value) / ln(2.0))
        .coerceAtMost(MAX_PRECISION_BITS.toDouble())

private fun bitsLongitude(resolution: Distance, latitude: Latitude): Double {
    val degrees = resolution.toLongitudeDegrees(latitude)
    return when {
        abs(degrees) > 0 -> (ln(360 / degrees) / ln(2.0))
        else -> 1.0
    }.coerceAtLeast(1.0)
}

private fun GeoHash.queries(bits: Int): ClosedRange<GeoHash> {
    val precision = ceil(bits.toDouble() / BITS_PER_BASE32_CHAR).toInt()
    if (value.length < precision) return this..GeoHash("$value~")
    val hash = value.substring(0, precision)
    val base = hash.substring(0, hash.length - 1)
    val lastValue = hash[hash.length - 1].toBase32Value()
    val significantBits = bits - (base.length * BITS_PER_BASE32_CHAR)
    val unusedBits = BITS_PER_BASE32_CHAR - significantBits
    // delete unused bits
    val start = (lastValue shr unusedBits) shl unusedBits
    val end = start + (1 shl unusedBits)
    val startHash = base + start.toBase32Char()
    val endHash = base + (end.takeIf { it <= 31 }?.toBase32Char() ?: '~')
    return GeoHash(startHash)..GeoHash(endHash)
}

private fun MutableCollection<ClosedRange<GeoHash>>.merge() {
    while (true) {
        val (query1, query2) = firstMergeableOrNull() ?: break
        remove(query1)
        remove(query2)
        add(query1 mergeWith query2)
    }
}

private fun Iterable<ClosedRange<GeoHash>>.firstMergeableOrNull(): Pair<ClosedRange<GeoHash>, ClosedRange<GeoHash>>? {
    for (range in this) {
        for (other in this) {
            if (range !== other && range canMergeWith other) {
                return range to other
            }
        }
    }
    return null
}
