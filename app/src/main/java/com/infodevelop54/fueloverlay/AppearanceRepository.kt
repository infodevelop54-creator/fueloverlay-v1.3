package com.infodevelop54.fueloverlay

import android.content.Context
import android.content.SharedPreferences

object AppearanceRepository {
    private const val PREFS = "widget_appearance"
    private const val KEY = "json"

    fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(context: Context, appearance: WidgetAppearance) {
        getPrefs(context).edit().putString(KEY, appearance.toJson()).apply()
    }

    fun load(context: Context): WidgetAppearance {
        val json = getPrefs(context).getString(KEY, null) ?: return WidgetAppearance()
        return WidgetAppearance.fromJson(json)
    }
}