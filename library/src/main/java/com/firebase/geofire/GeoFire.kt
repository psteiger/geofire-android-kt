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

import com.firebase.geofire.internal.geohash.geoHash
import com.freelapp.firebase.database.rtdb.value
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import kotlinx.coroutines.tasks.await
import java.lang.IllegalStateException

/** A GeoFire instance is used to store [GeoLocation] data in Firebase. */
public interface GeoFire {
    /** The Firebase reference this [GeoFire] instance uses. */
    public val databaseReference: DatabaseReference
}

/** Creates a new [GeoFire] instance. */
public fun DatabaseReference.asGeoFire(): GeoFire = GeoFireImpl(this)

/** Creates a new [GeoFire] instance. */
public fun GeoFire(databaseReference: DatabaseReference): GeoFire = GeoFireImpl(databaseReference)

/** Gets the current location for a [key]. */
public suspend fun GeoFire.getLocation(key: String): GeoLocation = key.ref.get().await().geoLocation

/** Sets the [location] for a given [key]. */
public suspend fun GeoFire.setLocation(key: String, location: GeoLocation) {
    val geoHash = location.geoHash().value
    val (lat, lng) = location
    val updates = mapOf(
        "g" to geoHash,
        "l" to listOf(lat.value, lng.value)
    )
    key.ref.setValue(updates, geoHash).await()
}

/** Removes the location for a [key] from this GeoFire. */
public suspend fun GeoFire.removeLocation(key: String) {
    key.ref.removeValue().await()
}

/**
 * Returns a new Query object covering the giving [circle].
 * The maximum radius that is supported is about 8587km. If a radius bigger than this is passed we'll cap it.
 */
public fun GeoFire.queryAtLocation(circle: Circle): GeoQuery = GeoQuery(this, circle)

/**
 * Returns a new Query object centered at the given [center] and [radius], in kilometers.
 * The maximum radius that is supported is about 8587km. If a radius bigger than this is passed we'll cap it.
 */
public fun GeoFire.queryAtLocation(center: GeoLocation, radius: Distance): GeoQuery =
    queryAtLocation(Circle(center, radius))

internal val DataSnapshot.geoLocation: GeoLocation
    get() = try {
        val data = value<Map<String, *>>()!!
        val location = data["l"] as List<*>
        val latitude = (location[0] as Number).toDouble().latitude
        val longitude = (location[1] as Number).toDouble().longitude
        GeoLocation(latitude, longitude)
    } catch (e: Throwable) {
        throw IllegalStateException("GeoFire data has invalid format: $this", e)
    }

context(GeoFire)
private val String.ref: DatabaseReference get() = databaseReference.child(this)

@JvmInline
private value class GeoFireImpl(override val databaseReference: DatabaseReference) : GeoFire