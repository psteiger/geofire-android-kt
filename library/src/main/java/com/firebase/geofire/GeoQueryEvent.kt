package com.firebase.geofire

import com.firebase.geofire.geometry.GeoLocation

sealed class GeoQueryEvent(open val key: String)
data class KeyEntered(override val key: String, val location: GeoLocation) : GeoQueryEvent(key)
data class KeyExited(override val key: String) : GeoQueryEvent(key)
data class KeyMoved(override val key: String, val location: GeoLocation) : GeoQueryEvent(key)