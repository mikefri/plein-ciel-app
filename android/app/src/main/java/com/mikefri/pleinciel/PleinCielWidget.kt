package com.mikefri.pleinciel

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.widget.RemoteViews
import java.net.URL
import org.json.JSONObject
import kotlin.concurrent.thread

open class PleinCielWidget : AppWidgetProvider() {
    open fun layoutId(): Int = R.layout.widget_plein_ciel
    open fun detailed(): Boolean = false

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) updateWidget(context, mgr, id)
    }

    private fun updateWidget(context: Context, mgr: AppWidgetManager, id: Int) {
        thread {
            try {
                var lat = 48.8566
                var lon = 2.3522
                var city = "Paris"

                var raw: String? = null
                try {
                    val dbPath = context.getDatabasePath("RKStorage").absolutePath
                    val db = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY)
                    val cur = db.rawQuery("SELECT value FROM catalystLocalStorage WHERE key = ?", arrayOf("pc_widget_loc"))
                    if (cur.moveToFirst()) raw = cur.getString(0)
                    cur.close()
                    db.close()
                } catch (e: Exception) { }

                if (raw != null) {
                    val o = JSONObject(raw)
                    lat = o.optDouble("lat", lat)
                    lon = o.optDouble("lon", lon)
                    city = o.optString("name", city)
                }

                val fields = if (detailed()) {
                    "temperature_2m,weather_code,relative_humidity_2m,apparent_temperature,wind_speed_10m,is_day"
                } else {
                    "temperature_2m,weather_code,is_day"
                }

                val url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon + "&current=" + fields + "&timezone=auto"
                val text = URL(url).openStream().bufferedReader().use { it.readText() }
                val cur2 = JSONObject(text).getJSONObject("current")
                val temp = cur2.getDouble("temperature_2m").toInt()
                val code = cur2.getInt("weather_code")
                val isDay = cur2.optInt("is_day", 1) == 1

                val views = RemoteViews(context.packageName, layoutId())
                views.setTextViewText(R.id.widget_temp, temp.toString() + "°")
                views.setTextViewText(R.id.widget_cond, label(code))
                views.setTextViewText(R.id.widget_city, city)
                views.setInt(R.id.widget_root, "setBackgroundColor", widgetBg(code, isDay))

                if (detailed()) {
                    val wind = cur2.getDouble("wind_speed_10m").toInt()
                    val hum = cur2.getDouble("relative_humidity_2m").toInt()
                    val feels = cur2.getDouble("apparent_temperature").toInt()
                    views.setTextViewText(R.id.widget_wind, wind.toString() + " km/h")
                    views.setTextViewText(R.id.widget_humidity, hum.toString() + " %")
                    views.setTextViewText(R.id.widget_feels, feels.toString() + "°")
                }

                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                if (intent != null) {
                    val pi = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
                    views.setOnClickPendingIntent(R.id.widget_root, pi)
                }
                mgr.updateAppWidget(id, views)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun label(code: Int): String = when {
        code == 0 -> "Ciel dégagé"
        code <= 3 -> "Nuageux"
        code <= 48 -> "Brouillard"
        code <= 67 -> "Pluie"
        code <= 77 -> "Neige"
        code <= 82 -> "Averses"
        else -> "Orage"
    }
}

class PleinCielWidgetSmall : PleinCielWidget() {
    override fun layoutId(): Int = R.layout.widget_plein_ciel_small
}

class PleinCielWidgetLarge : PleinCielWidget() {
    override fun layoutId(): Int = R.layout.widget_plein_ciel_large
    override fun detailed(): Boolean = true
}