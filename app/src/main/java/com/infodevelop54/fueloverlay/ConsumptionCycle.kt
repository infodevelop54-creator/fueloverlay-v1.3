package com.infodevelop54.fueloverlay

import org.json.JSONObject

data class ConsumptionCycle(
    val startTimestamp: Long,
    val endTimestamp: Long,
    val startOdometerKm: Float,
    val endOdometerKm: Float,
    val distanceKm: Float,
    val totalLiters: Float,
    val avgConsumptionL100: Float
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("startTimestamp", startTimestamp)
        put("endTimestamp", endTimestamp)
        put("startOdometerKm", startOdometerKm.toDouble())
        put("endOdometerKm", endOdometerKm.toDouble())
        put("distanceKm", distanceKm.toDouble())
        put("totalLiters", totalLiters.toDouble())
        put("avgConsumptionL100", avgConsumptionL100.toDouble())
    }

    companion object {
        fun fromJson(o: JSONObject) = ConsumptionCycle(
            startTimestamp = o.optLong("startTimestamp"),
            endTimestamp = o.optLong("endTimestamp"),
            startOdometerKm = o.optDouble("startOdometerKm", 0.0).toFloat(),
            endOdometerKm = o.optDouble("endOdometerKm", 0.0).toFloat(),
            distanceKm = o.optDouble("distanceKm", 0.0).toFloat(),
            totalLiters = o.optDouble("totalLiters", 0.0).toFloat(),
            avgConsumptionL100 = o.optDouble("avgConsumptionL100", 0.0).toFloat()
        )
    }
}