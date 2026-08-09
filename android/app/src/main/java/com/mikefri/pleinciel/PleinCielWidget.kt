package com.mikefri.pleinciel

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import java.net.URL
import org.json.JSONObject
import kotlin.concurrent.thread

class PleinCielWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) updateWidget(context, mgr, id)
    }

    private fun updateWidget(context: Context, mgr: AppWidgetManager, id: Int) {
        thread {
            try {
                val url = "https://api.open-meteo.com/v1/forecast?latitude=48.8566&longitude=2.3522&current=temperature_2m,weather_code&timezone=auto"
                val text = URL(url).openStream().bufferedReader().use { it.readText() }
                val cur = JSONObject(text).getJSONObject("current")
                val temp = cur.getDouble("temperature_2m").toInt()
                val code = cur.getInt("weather_code")

                val views = RemoteViews(context.packageName, R.layout.widget_plein_ciel)
                views.setTextViewText(R.id.widget_temp, "$temp°")
                views.setTextViewText(R.id.widget_cond, label(code))
                views.setTextViewText(R.id.widget_city, "Paris")

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
