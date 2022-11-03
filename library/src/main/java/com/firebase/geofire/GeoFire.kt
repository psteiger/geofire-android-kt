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
package com.firebase.geofire

import com.firebase.geofire.geohash.geoHash
import com.firebase.geofire.geometry.*
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.GenericTypeIndicator
import kotlinx.coroutines.tasks.await

/** A GeoFire instance is used to store geo location data in Firebase. */
interface GeoFire {
    /** The Firebase reference this GeoFire instance uses. */
    val databaseReference: DatabaseReference
}

fun DatabaseReference.asGeoFire(): GeoFire = GeoFire(this)

fun GeoFire(databaseReference: DatabaseReference): GeoFire = GeoFireImpl(databaseReference)

/**
 * Gets the current location for a key and calls the callback with the current value.
 *
 * @param key      The key whose location to get
 */
suspend fun GeoFire.getLocation(key: String): GeoLocation {
    val keyRef = databaseReference.child(key)
    val snapshot = keyRef.get().await()
    return snapshot.geoLocation
}

/**
 * Sets the location for a given key.
 *
 * @param key                The key to save the location for
 * @param location           The location of this key
 */
suspend fun GeoFire.setLocation(key: String, location: GeoLocation) {
    val keyRef = databaseReference.child(key)
    val geoHash = location.geoHash().value
    val updates = mapOf(
        "g" to geoHash,
        "l" to listOf(location.latitude.value, location.longitude.value)
    )
    keyRef.setValue(updates, geoHash).await()
}

/**
 * Removes the location for a key from this GeoFire.
 *
 * @param key The key to remove from this GeoFire
 */
suspend fun GeoFire.removeLocation(key: String) {
    val keyRef = databaseReference.child(key)
    keyRef.removeValue().await()
}


fun GeoFire.queryAtLocation(circle: Circle): GeoQuery = GeoQuery(this, circle)

/**
 * Returns a new Query object centered at the given location and with the given radius.
 *
 * @param center The center of the query
 * @param radius The radius of the query, in kilometers. The maximum radius that is
 * supported is about 8587km. If a radius bigger than this is passed we'll cap it.
 * @return The new GeoQuery object
 */
fun GeoFire.queryAtLocation(center: GeoLocation, radius: Distance): GeoQuery =
    queryAtLocation(Circle(center, radius))

internal val DataSnapshot.geoLocation: GeoLocation
    get() = runCatching {
        val typeIndicator = object : GenericTypeIndicator<Map<String, Any>>() {}
        val data = getValue(typeIndicator)!!
        val location = data["l"] as List<*>
        val latitudeObj = location[0] as Number
        val longitudeObj = location[1] as Number
        val latitude = latitudeObj.toDouble().latitude
        val longitude = longitudeObj.toDouble().longitude
        GeoLocation(latitude, longitude)
    }.getOrElse {
        error("GeoFire data has invalid format: $this")
    }

@JvmInline
private value class GeoFireImpl(override val databaseReference: DatabaseReference) : GeoFire