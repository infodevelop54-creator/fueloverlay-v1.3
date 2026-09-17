package com.infodevelop54.fueloverlay

import android.content.Context
import org.json.JSONArray

object AdaptiveConsumption {

    private const val HALF_LIFE_CYCLES = 3.0

    fun loadCycles(context: Context): List<ConsumptionCycle> {
        val arr = FuelDatabase.readArray(FuelDatabase.cyclesFile(context))
        val list = ArrayList<ConsumptionCycle>(arr.length())
        for (i in 0 until arr.length()) list.add(ConsumptionCycle.fromJson(arr.getJSONObject(i)))
        return list.sortedByDescending { it.endTimestamp }
    }

    fun saveCycles(context: Context, cycles: List<ConsumptionCycle>) {
        val arr = JSONArray()
        cycles.sortedBy { it.endTimestamp }.forEach { arr.put(it.toJson()) }
        FuelDatabase.writeArray(FuelDatabase.cyclesFile(context), arr)
    }

    fun addCycle(context: Context, cycle: ConsumptionCycle) {
        saveCycles(context, loadCycles(context) + cycle)
        recompute(context)
    }

    fun recompute(context: Context): Float {
        val repo = FuelStateRepository(context)
        val cycles = loadCycles(context)
        if (cycles.isEmpty()) {
            repo.averageConsumptionL100 = repo.manualAverageConsumptionL100
            return repo.averageConsumptionL100
        }

        val decay = Math.pow(0.5, 1.0 / HALF_LIFE_CYCLES)
        var weight = 1.0
        var wSum = 0.0
        var wVal = 0.0
        for (c in cycles) {
            wSum += weight
            wVal += c.avgConsumptionL100 * weight
            weight *= decay
        }
        val adaptive = if (wSum > 0) (wVal / wSum).toFloat() else cycles.first().avgConsumptionL100
        repo.averageConsumptionL100 = adaptive
        return adaptive
    }

    fun clear(context: Context) {
        saveCycles(context, emptyList())
        val repo = FuelStateRepository(context)
        repo.averageConsumptionL100 = repo.manualAverageConsumptionL100
    }
}