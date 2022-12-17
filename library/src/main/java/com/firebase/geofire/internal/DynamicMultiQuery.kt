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