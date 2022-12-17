package com.firebase.geofire.geometry

import com.firebase.geofire.distanceTo
import com.firebase.geofire.plus
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.floor

class GeoLocationTest {

    @Test
    fun testGetDistanceBetween() {
        assertThat(floor(SAN_FRANCISCO distanceTo NEW_YORK_CITY)).isEqualTo(4127138.0)
        assertThat(floor(NEW_YORK_CITY distanceTo SAN_FRANCISCO)).isEqualTo(4127138.0)
    }

    companion object {
        private val SAN_FRANCISCO = 37.7749.latitude + (-122.4194).longitude
        private val NEW_YORK_CITY = 40.7128.latitude + (-74.0060).longitude
    }
}