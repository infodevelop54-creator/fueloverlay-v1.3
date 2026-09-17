package com.infodevelop54.fueloverlay

import org.json.JSONObject

data class RefuelEvent(
    val timestamp: Long,
    val odometerKm: Float,
    val liters: Float,
    val pricePerLiter: Float,
    val fullTank: Boolean
) {
    val totalPrice: Float get() = liters * pricePerLiter

    fun toJson(): JSONObject = JSONObject().apply {
        put("timestamp", timestamp)
        put("odometerKm", odometerKm.toDouble())
        put("liters", liters.toDouble())
        put("pricePerLiter", pricePerLiter.toDouble())
        put("fullTank", fullTank)
    }

    companion object {
        fun fromJson(o: JSONObject) = RefuelEvent(
            timestamp = o.optLong("timestamp"),
            odometerKm = o.optDouble("odometerKm", 0.0).toFloat(),
            liters = o.optDouble("liters", 0.0).toFloat(),
            pricePerLiter = o.optDouble("pricePerLiter", 0.0).toFloat(),
            fullTank = o.optBoolean("fullTank", false)
        )
    }
}