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
package com.firebase.geofire.example

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.firebase.geofire.*
import com.firebase.geofire.geometry.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

private const val TAG = "MainActivity"

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        lifecycleScope.launch {
            val geoFire = Firebase.database.getReference("geofire").asGeoFire()
            val location = 15.0.latitude + 15.0.longitude
            geoFire.setLocation("abc", location)
            geoFire.setLocation("def", location)
            val geoQuery = geoFire.queryAtLocation(location, 10.0.kilometers)
            launch {
                geoQuery.events
                    .onEach { Log.d(TAG, "onCreate: $it") }
                    .flowOn(Dispatchers.IO)
                    .launchIn(this)
            }
            launch {
                delay(2.seconds)
                geoFire.setLocation("def", 15.0.latitude + 40.0.longitude)
            }
            launch {
                delay(4.seconds)
                geoQuery.radius = 1000000.0.meters
            }
        }
    }
}