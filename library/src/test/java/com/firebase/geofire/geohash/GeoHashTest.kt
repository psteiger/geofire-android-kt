package com.firebase.geofire.geohash

import com.firebase.geofire.geometry.latitude
import com.firebase.geofire.geometry.longitude
import com.firebase.geofire.plus
import com.firebase.geofire.internal.geohash.GeoHash
import com.firebase.geofire.internal.geohash.geoHash
import com.firebase.geofire.internal.geohash.toGeoLocation
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GeoHashTest {

    @Test
    fun testGetGeoHashForLocation() {
        assertThat(SAN_FRANCISCO.geoHash()).isEqualTo(GeoHash("9q8yyk8ytp"))
        assertThat(NEW_YORK_CITY.geoHash()).isEqualTo(GeoHash("dr5regw3pp"))
    }

    @Test
    fun locationFromHash() {
        assertHashRoundtrip(37.7853074, -122.4054274)
        assertHashRoundtrip(38.98719, -77.250783)
        assertHashRoundtrip(29.3760648, 47.9818853)
        assertHashRoundtrip(78.216667, 15.55)
        assertHashRoundtrip(-54.933333, -67.616667)
        assertHashRoundtrip(-54.0, -67.0)
        assertHashRoundtrip(0.0, 0.0)
        assertHashRoundtrip(0.0, -180.0)
        assertHashRoundtrip(0.0, 180.0)
        assertHashRoundtrip(-90.0, 0.0)
        assertHashRoundtrip(-90.0, -180.0)
        assertHashRoundtrip(-90.0, 180.0)
        assertHashRoundtrip(90.0, 0.0)
        assertHashRoundtrip(90.0, -180.0)
        assertHashRoundtrip(90.0, 180.0)
    }

    private fun assertHashRoundtrip(lat: Double, lng: Double) {
        val hash = (lat.latitude + lng.longitude).geoHash()
        val loc = hash.toGeoLocation()
        assertThat(loc.latitude.value).isWithin(EPSILON).of(lat)
    }

    companion object {
        private const val EPSILON = 0.01
        private val SAN_FRANCISCO = 37.7749.latitude + (-122.4194).longitude
        private val NEW_YORK_CITY = 40.7128.latitude + (-74.0060).longitude
    }
}