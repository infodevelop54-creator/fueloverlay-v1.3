package com.infodevelop54.fueloverlay

import android.content.Context
import org.json.JSONObject

object FuelBackup {

    fun export(context: Context): String {
        FuelStateRepository.flush()
        val root = JSONObject().apply {
            put("version", 1)
            put("exportedAt", System.currentTimeMillis())
            put(
                "settings",
                FuelDatabase.readJson(context, FuelDatabase.settingsFile(context)) ?: JSONObject()
            )
            put("journal", FuelDatabase.readArray(FuelDatabase.journalFile(context)))
            put("cycles", FuelDatabase.readArray(FuelDatabase.cyclesFile(context)))
        }
        return root.toString(2)
    }

    fun import(context: Context, text: String) {
        val root = JSONObject(text)
        root.optJSONObject("settings")?.let {
            FuelDatabase.writeJson(FuelDatabase.settingsFile(context), it)
        }
        root.optJSONArray("journal")?.let {
            FuelDatabase.writeArray(FuelDatabase.journalFile(context), it)
        }
        root.optJSONArray("cycles")?.let {
            FuelDatabase.writeArray(FuelDatabase.cyclesFile(context), it)
        }
        FuelStateRepository.invalidate(context)
        AdaptiveConsumption.recompute(context)
    }
}