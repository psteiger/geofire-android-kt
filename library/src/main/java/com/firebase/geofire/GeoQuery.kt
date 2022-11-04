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

import com.firebase.geofire.geohash.*
import com.firebase.geofire.geometry.Circle
import com.firebase.geofire.geometry.Distance
import com.firebase.geofire.geometry.inKilometers
import com.firebase.geofire.geometry.kilometers
import com.freelapp.firebase.database.rtdb.*
import com.google.firebase.database.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.*

/**
 * A GeoQuery object can be used for geo queries in a given circle.
 */
interface GeoQuery {
    val geoFire: GeoFire
    val circle: MutableStateFlow<Circle>
}

var GeoQuery.center
    get() = circle.value.center
    set(center) = circle.update { Circle(center, it.radius) }

var GeoQuery.radius
    get() = circle.value.radius
    set(radius) = circle.update { Circle(it.center, radius) }

val GeoQuery.events: Flow<GeoQueryEvent>
    get() = channelFlow {
        val multiQuery = MultiQuery()
        circle
            .map { it.capRadius() }
            .map { it.queries }
            .onEach(multiQuery::setQueries)
            .launchIn(this)
    }.map(GeoQueryDataEvent::toGeoQueryEvent)

internal fun GeoQuery(geoFire: GeoFire, circle: Circle): GeoQuery = GeoQueryImpl(geoFire, circle)

private fun Circle.capRadius() = Circle(center, radius.cap())
private fun Distance.cap() =
    inKilometers().value.coerceAtMost(MAX_SUPPORTED_RADIUS_IN_KM).kilometers

private fun DatabaseReference.query(range: ClosedRange<GeoHash>): Flow<GeoQueryDataEvent> =
    orderByChild("g")
        .startAt(range.start.value)
        .endAt(range.endInclusive.value)
        .children()
        .toGeoQueryEventFlow()

private fun Flow<ChildEvent>.toGeoQueryEventFlow(): Flow<GeoQueryDataEvent> =
    transform {
        val event = when (it) {
            is ChildAdded -> DataEntered(it.snapshot)
            is ChildChanged -> DataMoved(it.snapshot)
            is ChildMoved -> return@transform // Handled by ChildChanged
            is ChildRemoved -> DataExited(it.snapshot)
        }
        emit(event)
    }

context(GeoQuery, ProducerScope<GeoQueryDataEvent>)
private class MultiQuery {
    private val snaps = hashMapOf<String, DataSnapshot>()
    private val jobs = hashMapOf<ClosedRange<GeoHash>, Job>()

    // needed because we launch concurrent coroutines to handle each query from the range
    @OptIn(ExperimentalStdlibApi::class, ExperimentalCoroutinesApi::class)
    private val noParallelismContext =
        (coroutineContext[CoroutineDispatcher] ?: Dispatchers.IO).limitedParallelism(1)

    suspend fun setQueries(queries: Set<ClosedRange<GeoHash>>) {
        withContext(noParallelismContext) {
            removeOldQueries(queries)
            launchNewQueries(queries)
        }
    }

    private suspend fun removeOldQueries(newQueries: Set<ClosedRange<GeoHash>>) {
        val oldQueries = jobs.keys.toSet()
        val removed = oldQueries - newQueries
        // cancel jobs
        removed
            .map { jobs.remove(it)!! }
            .forEach { it.cancelAndJoin() }
        // remove snaps that are not in new queries
        snaps
            .filterValues { snap ->
                val hash = snap.geoLocation.geoHash()
                newQueries.none { hash in it }
            }
            .forEach { (key, snap) ->
                send(DataExited(snap))
                snaps.remove(key)
            }
    }

    private fun launchNewQueries(queries: Set<ClosedRange<GeoHash>>) {
        val newJobs = queries.associateWith {
            geoFire.databaseReference.query(it)
                .onEach(::onGeoQueryEvent)
                .launchIn(this@ProducerScope)
        }
        jobs.putAll(newJobs)
    }

    private suspend fun onGeoQueryEvent(geoQueryDataEvent: GeoQueryDataEvent) {
        withContext(noParallelismContext) {
            val snap = geoQueryDataEvent.snapshot
            val key = snap.key!!
            val location = snap.geoLocation
            when (geoQueryDataEvent) {
                is DataEntered -> {
                    if (snaps.contains(key)) {
                        val oldLocation = snaps.getValue(key).geoLocation
                        if (oldLocation != location) {
                            send(DataMoved(snap))
                        } // else ignore, data entered and did not change
                    } else {
                        send(DataEntered(snap))
                    }
                    snaps[key] = snap
                }
                is DataExited -> {
                    send(geoQueryDataEvent)
                    snaps.remove(key)
                }
                is DataMoved -> {
                    send(geoQueryDataEvent)
                    snaps[key] = snap
                }
            }
        }
    }
}

private sealed class GeoQueryDataEvent(open val snapshot: DataSnapshot)
private data class DataEntered(override val snapshot: DataSnapshot) : GeoQueryDataEvent(snapshot)
private data class DataExited(override val snapshot: DataSnapshot) : GeoQueryDataEvent(snapshot)
private data class DataMoved(override val snapshot: DataSnapshot) : GeoQueryDataEvent(snapshot)

private fun GeoQueryDataEvent.toGeoQueryEvent(): GeoQueryEvent =
    when (this) {
        is DataEntered -> KeyEntered(snapshot.key!!, snapshot.geoLocation)
        is DataExited -> KeyExited(snapshot.key!!)
        is DataMoved -> KeyMoved(snapshot.key!!, snapshot.geoLocation)
    }

private class GeoQueryImpl(
    override val geoFire: GeoFire,
    initialParameters: Circle,
) : GeoQuery {
    override val circle = MutableStateFlow(initialParameters)
}

private const val MAX_SUPPORTED_RADIUS_IN_KM = 8587.0