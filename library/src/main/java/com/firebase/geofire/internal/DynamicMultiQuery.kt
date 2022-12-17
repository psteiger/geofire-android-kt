package com.firebase.geofire.internal

import com.firebase.geofire.*
import com.freelapp.firebase.database.rtdb.*
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.Query
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.flow.*

internal class DynamicMultiQuery(initialQueries: Set<Query> = emptySet()) {
    val queries = MutableStateFlow(initialQueries)
}

@OptIn(ExperimentalCoroutinesApi::class)
internal val DynamicMultiQuery.state: Flow<Map<String, DataSnapshot>>
    get() = flow {
        coroutineScope {
            val jobs = mutableMapOf<Query, Job>()
            val snapshots = mutableMapOf<String, DataSnapshot>()
            val ch = produce(capacity = Channel.UNLIMITED) {
                queries.collect { newQueries ->
                    val old = jobs.keys
                    (old - newQueries).forEach { jobs.remove(it)!!.cancelAndJoin() }
                    val newJobs = (newQueries - old).associateWith { query ->
                        query.children.onEach { send(it) }.launchIn(this)
                    }
                    jobs.putAll(newJobs)
                }
            }
            for (e in ch) {
                val snapshot = e.snapshot
                val key = snapshot.key!!
                when (e) {
                    is ChildAdded -> snapshots[key] = snapshot
                    is ChildChanged -> snapshots[key] = snapshot
                    is ChildMoved -> { /* handled by ChildChanged */ }
                    is ChildRemoved -> snapshots.remove(key)
                }
                emit(snapshots.toMap())
            }
        }
    }.distinctUntilChanged().conflate()

//@OptIn(ExperimentalCoroutinesApi::class)
//internal val DynamicMultiQuery.events: Flow<GeoQueryDataEvent>
//    get() = flow {
//        coroutineScope {
//            val jobs = mutableMapOf<Query, Job>()
//            val snapshots = mutableMapOf<String, DataSnapshot>()
//            val ch = produce(capacity = Channel.UNLIMITED) {
//                queries.collect { newQueries ->
//                    val old = jobs.keys
//                    (old - newQueries).forEach { jobs.remove(it)!!.cancelAndJoin() }
//                    val newJobs = (newQueries - old).associateWith { query ->
//                        query.childrenWithInitialData.onEach { send(it) }.launchIn(this)
//                    }
//                    jobs.putAll(newJobs)
//                }
//            }
//            for (e in ch) {
//                val snapshot = e.snapshot
//                val key = snapshot.key!!
//                when (e) {
//                    is ChildAdded -> {
//                        snapshots[key] = snapshot
//                        emit(DataEntered(snapshot))
//                    }
//
//                    is ChildChanged -> {
//                        snapshots[key] = snapshot
//                        emit(DataMoved(e.snapshot))
//                    }
//
//                    is ChildMoved -> { /* handled by ChildChanged */
//                    }
//
//                    is ChildRemoved -> {
//                        snapshots.remove(key)
//                        emit(DataExited(e.snapshot))
//                    }
//                }
//            }
//        }
//    }
//
//context(ProducerScope<GeoQueryDataEvent>)
//private fun Set<Query>.produceChildren(currentState: MutableMap<String, DataSnapshot>): Map<Query, Job> {
//    val size = size
//    val processedInitial = AtomicInteger(0)
//    val initialData = Channel<List<DataSnapshot>>()
//    val initialDataJob = launch {
//        val data = initialData.toList().flatten().associateBy { it.key!! }
//        currentState.substitute(data)
//    }
//    return associateWith { query ->
//        query.childrenWithInitialData
//            .onEach { event ->
//                when (event) {
//                    is InitialData -> {
//                        initialData.send(event.children)
//                        if (processedInitial.incrementAndGet() == size) initialData.close()
//                    }
//
//                    is ChildEvent -> {
//                        initialDataJob.join()
//                        val snapshot = event.snapshot
//                        val key = snapshot.key!!
//                        when (event) {
//                            is ChildAdded -> {
//                                currentState[key] = snapshot
//                                send(DataEntered(snapshot))
//                            }
//
//                            is ChildChanged -> {
//                                currentState[key] = snapshot
//                                send(DataMoved(event.snapshot))
//                            }
//
//                            is ChildMoved -> { /* handled by ChildChanged */
//                            }
//
//                            is ChildRemoved -> {
//                                currentState.remove(key)
//                                send(DataExited(event.snapshot))
//                            }
//                        }
//                    }
//                }
//            }
//            .launchIn(this@ProducerScope)
//    }
//}
//
//context(ProducerScope<GeoQueryDataEvent>)
//private suspend fun MutableMap<String, DataSnapshot>.substitute(map: Map<String, DataSnapshot>) {
//    withContext(NonCancellable) {
//        val keys = keys.toSet()
//        val added = map - keys
//        val removed = this@substitute - map.keys
//        val maybeChanged = map - (added.keys + removed.keys)
//        removed.keys.forEach { remove(it) }
//        val changed = maybeChanged.filter { (k, v) ->
//            val changed = getValue(k).geoLocation != v.geoLocation
//            Log.d("MainActivity", "substitute: $k qchanged = $changed ${getValue(k).geoLocation} ${v.geoLocation}")
//            changed
//        }
//        putAll(changed + added)
//        removed.values.forEach { send(DataExited(it)) }
//        changed.values.forEach { send(DataMoved(it)) }
//        added.values.forEach { send(DataEntered(it)) }
//    }
//}

//private fun Flow<ChildEvent>.onCancelled(
//    block: (Map<String, DataSnapshot>) -> Unit
//) = flow {
//    val snapshots = mutableMapOf<String, DataSnapshot>()
//    try {
//        collect { event ->
//            val snap = event.snapshot
//            val key = snap.key!!
//            val oldValue = snapshots[key]
//            when (event) {
//                is ChildAdded -> snapshots[key] = snap
//                is ChildChanged -> snapshots[key] = snap
//                is ChildMoved -> {}
//                is ChildRemoved -> snapshots.remove(key)
//            }
//            try {
//                emit(event)
//            } catch (e: CancellationException) {
//                snapshots[key] = oldValue!!
//                throw e
//            }
//        }
//    } catch (e: CancellationException) {
//        block(snapshots)
//        throw e
//    }
//}