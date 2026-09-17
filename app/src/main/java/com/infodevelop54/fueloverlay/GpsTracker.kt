package com.infodevelop54.fueloverlay

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper

/**
 * Накапливает пройденное расстояние с фильтром «якоря».
 * Корректно считает и быструю езду, и пробку, и не набирает
 * фантомный пробег на стоянке.
 */
class GpsTracker(
    context: Context,
    private val onDistanceMeters: (Double) -> Unit
) {
    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var anchor: Location? = null

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (location.accuracy > MAX_ACCURACY_M) return

            val a = anchor
            if (a == null) { anchor = location; return }

            val dist = a.distanceTo(location).toDouble()
            if (dist < ANCHOR_MOVE_M) return

            val dtSec = (location.time - a.time) / 1000.0
            if (dtSec <= 0) { anchor = location; return }

            val impliedSpeed = dist / dtSec
            if (impliedSpeed > MAX_IMPLIED_SPEED_MPS) {
                if (location.accuracy <= a.accuracy) anchor = location
                return
            }

            onDistanceMeters(dist)
            anchor = location
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) { anchor = null }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) return
        try {
            lm.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                MIN_TIME_MS, MIN_DIST_M, listener, Looper.getMainLooper()
            )
        } catch (_: SecurityException) { }
    }

    fun stop() {
        try { lm.removeUpdates(listener) } catch (_: Exception) { }
        anchor = null
    }

    companion object {
        const val MIN_TIME_MS = 1000L
        const val MIN_DIST_M  = 0f
        const val MAX_ACCURACY_M = 20f
        const val ANCHOR_MOVE_M  = 15.0
        const val MAX_IMPLIED_SPEED_MPS = 60.0
    }
}