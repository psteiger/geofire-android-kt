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

import com.firebase.geofire.internal.DynamicMultiQuery
import com.firebase.geofire.internal.geohash.GeoHash
import com.firebase.geofire.internal.geohash.queries
import com.firebase.geofire.internal.state
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.Query
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*

/** A GeoQuery object can be used for geo queries in a given circle. */
public interface GeoQuery {
    public val geoFire: GeoFire
    public val circle: MutableStateFlow<Circle>
}

public var GeoQuery.center: GeoLocation
    get() = circle.value.center
    set(center) = circle.update { Circle(center, it.radius) }

public var GeoQuery.radius: Distance
    get() = circle.value.radius
    set(radius) = circle.update { Circle(it.center, radius.cap()) }

public val GeoQuery.state: Flow<Map<String, GeoLocation>>
    get() = stateImpl

//public val GeoQuery.events: Flow<GeoQueryEvent> get() = dataEventsImpl.map(GeoQueryDataEvent::toGeoQueryEvent)

//public sealed interface GeoQueryEvent {
//    public val key: String
//}
//
//public data class KeyEntered(override val key: String, val location: GeoLocation) : GeoQueryEvent
//public data class KeyExited(override val key: String) : GeoQueryEvent
//public data class KeyMoved(override val key: String, val location: GeoLocation) : GeoQueryEvent

/*
 * Implementation
 */

private val GeoQuery.stateImpl: Flow<Map<String, GeoLocation>>
    get() = flow {
        val multiQuery = DynamicMultiQuery()
        coroutineScope {
            circle
                .map { it.queries.map(geoFire.databaseReference::query).toSet() }
                .onEach { multiQuery.queries.value = it }
                .launchIn(this)
            emitAll(multiQuery.state.map { it.mapValues { it.value.geoLocation } })
        }
    }
        .combine(circle) { state, circle -> state.filterValues { it in circle } }
        .distinctUntilChanged()
        .conflate()

internal fun GeoQuery(geoFire: GeoFire, circle: Circle): GeoQuery = GeoQueryImpl(geoFire, circle)

private fun Circle.capRadius() = Circle(center, radius.cap())
private fun Distance.cap() = inKilometers().value.coerceAtMost(MAX_SUPPORTED_RADIUS_IN_KM).kilometers

private fun DatabaseReference.query(range: ClosedRange<GeoHash>): Query =
    orderByChild("g")
        .startAt(range.start.value)
        .endAt(range.endInclusive.value)

//internal sealed class GeoQueryDataEvent(open val snapshot: DataSnapshot)
//internal data class DataEntered(override val snapshot: DataSnapshot) : GeoQueryDataEvent(snapshot)
//internal data class DataExited(override val snapshot: DataSnapshot) : GeoQueryDataEvent(snapshot)
//internal data class DataMoved(override val snapshot: DataSnapshot) : GeoQueryDataEvent(snapshot)
//
//private fun GeoQueryDataEvent.toGeoQueryEvent(): GeoQueryEvent =
//    when (this) {
//        is DataEntered -> KeyEntered(snapshot.key!!, snapshot.geoLocation)
//        is DataExited -> KeyExited(snapshot.key!!)
//        is DataMoved -> KeyMoved(snapshot.key!!, snapshot.geoLocation)
//    }

private class GeoQueryImpl(override val geoFire: GeoFire, initialParameters: Circle) : GeoQuery {
    override val circle = MutableStateFlow(initialParameters.capRadius())
}

private const val MAX_SUPPORTED_RADIUS_IN_KM = 8587.0