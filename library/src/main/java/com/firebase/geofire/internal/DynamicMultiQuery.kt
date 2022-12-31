package com.firebase.geofire.internal

import com.firebase.geofire.*
import com.google.firebase.database.ktx.*
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
                        query.childEvents.buffer(Channel.UNLIMITED).onEach { send(it) }.launchIn(this)
                    }
                    jobs.putAll(newJobs)
                }
            }
            for (e in ch) {
                when (e) {
                    is ChildEvent.Added -> snapshots[e.snapshot.key!!] = e.snapshot
                    is ChildEvent.Changed -> snapshots[e.snapshot.key!!] = e.snapshot
                    is ChildEvent.Moved -> { /* handled by ChildChanged */ }
                    is ChildEvent.Removed -> snapshots.remove(e.snapshot.key!!)
                }
                emit(snapshots.toMap())
            }
        }
    }.distinctUntilChanged().conflate()