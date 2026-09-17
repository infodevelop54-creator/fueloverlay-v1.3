package com.infodevelop54.fueloverlay

import android.content.Context
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object FuelDatabase {

    private const val DIR_NAME = "fuel_overlay"
    private const val FILE_SETTINGS = "settings.json"
    private const val FILE_JOURNAL = "refuel_journal.json"
    private const val FILE_CYCLES = "consumption_cycles.json"

    fun baseDir(context: Context): File {
        val external = context.getExternalFilesDir(null)
        val dir = when {
            external != null && isExternalAvailable() -> File(external, DIR_NAME)
            else -> File(context.filesDir, DIR_NAME)
        }
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun settingsFile(context: Context) = File(baseDir(context), FILE_SETTINGS)
    fun journalFile(context: Context)  = File(baseDir(context), FILE_JOURNAL)
    fun cyclesFile(context: Context)   = File(baseDir(context), FILE_CYCLES)

    private fun isExternalAvailable(): Boolean = try {
        Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    } catch (_: Exception) { false }

    fun readJson(context: Context, file: File): JSONObject? = try {
        if (file.exists()) JSONObject(file.readText()) else null
    } catch (_: Exception) { null }

    fun writeJson(file: File, obj: JSONObject) = try {
        file.parentFile?.mkdirs()
        file.writeText(obj.toString(2))
    } catch (_: Exception) { }

    fun readArray(file: File): JSONArray = try {
        if (file.exists()) JSONArray(file.readText()) else JSONArray()
    } catch (_: Exception) { JSONArray() }

    fun writeArray(file: File, arr: JSONArray) = try {
        file.parentFile?.mkdirs()
        file.writeText(arr.toString(2))
    } catch (_: Exception) { }
}