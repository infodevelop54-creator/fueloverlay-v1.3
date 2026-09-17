package com.infodevelop54.fueloverlay

import android.content.Context
import org.json.JSONArray

object RefuelJournal {

    fun loadAll(context: Context): List<RefuelEvent> {
        val arr = FuelDatabase.readArray(FuelDatabase.journalFile(context))
        val list = ArrayList<RefuelEvent>(arr.length())
        for (i in 0 until arr.length()) list.add(RefuelEvent.fromJson(arr.getJSONObject(i)))
        return list.sortedByDescending { it.timestamp }
    }

    fun saveAll(context: Context, events: List<RefuelEvent>) {
        val arr = JSONArray()
        events.sortedBy { it.timestamp }.forEach { arr.put(it.toJson()) }
        FuelDatabase.writeArray(FuelDatabase.journalFile(context), arr)
    }

    fun add(context: Context, event: RefuelEvent) {
        saveAll(context, loadAll(context) + event)
    }

    fun remove(context: Context, event: RefuelEvent) {
        val list = loadAll(context).toMutableList()
        list.removeAll { it.timestamp == event.timestamp && it.liters == event.liters }
        saveAll(context, list)
        recalculateFromJournal(context)
    }

    fun previousFull(context: Context, before: Long): RefuelEvent? =
        loadAll(context).filter { it.fullTank && it.timestamp < before }
            .maxByOrNull { it.timestamp }

    fun litersBetween(context: Context, after: Long, before: Long): Float =
        loadAll(context).filter { it.timestamp in (after + 1) until before }
            .sumOf { it.liters.toDouble() }.toFloat()

    fun recalculateFromJournal(context: Context) {
        val events = loadAll(context).sortedBy { it.timestamp }
        val lastFull = events.lastOrNull { it.fullTank } ?: return

        val repo = FuelStateRepository(context)
        repo.refuelOdometerKm = lastFull.odometerKm
        repo.extraFuelAddedL = events
            .filter { !it.fullTank && it.timestamp > lastFull.timestamp }
            .sumOf { it.liters.toDouble() }
            .toFloat()
    }
}