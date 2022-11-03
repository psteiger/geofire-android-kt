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
package com.firebase.geofire.geometry

import com.firebase.geofire.EARTH_EQ_RADIUS
import com.firebase.geofire.EARTH_POLAR_RADIUS
import kotlin.math.*

/** A wrapper class for location coordinates. */
data class GeoLocation(val latitude: Latitude, val longitude: Longitude)

operator fun Latitude.plus(longitude: Longitude): GeoLocation = GeoLocation(this, longitude)
operator fun Longitude.plus(latitude: Latitude): GeoLocation = GeoLocation(latitude, this)

infix fun GeoLocation.distanceTo(other: GeoLocation): Double =
    distance(
        latitude,
        longitude,
        other.latitude,
        other.longitude
    )

private fun distance(lat1: Latitude, lng1: Longitude, lat2: Latitude, lng2: Longitude): Double {
    // Earth's mean radius in meters
    val radius = (EARTH_EQ_RADIUS + EARTH_POLAR_RADIUS) / 2
    val lat1Radians = Math.toRadians(lat1.value)
    val lat2Radians = Math.toRadians(lat2.value)
    val lng1Radians = Math.toRadians(lng1.value)
    val lng2Radians = Math.toRadians(lng2.value)
    val latDelta = lat2Radians - lat1Radians
    val lngDelta = lng2Radians - lng1Radians
    val a = sin(latDelta / 2).pow(2) +
            cos(lat1Radians) * cos(lat2Radians) *
            sin(lngDelta / 2).pow(2)
    return radius * 2 * atan2(sqrt(a), sqrt(1 - a))
}
