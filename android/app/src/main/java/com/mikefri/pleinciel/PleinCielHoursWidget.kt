package com.mikefri.pleinciel

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.widget.RemoteViews
import java.net.URL
import org.json.JSONObject
import kotlin.concurrent.thread
import kotlin.math.cos
import kotlin.math.sin

class PleinCielHoursWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) update(context, mgr, id)
    }

    private fun update(context: Context, mgr: AppWidgetManager, id: Int) {
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

                val url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon +
                    "&current=temperature_2m&hourly=temperature_2m,weather_code,is_day&forecast_days=2&timezone=auto"
                val text = URL(url).openStream().bufferedReader().use { it.readText() }
                val j = JSONObject(text)
                val curTime = j.getJSONObject("current").getString("time")
                val h = j.getJSONObject("hourly")
                val times = h.getJSONArray("time")
                val temps = h.getJSONArray("temperature_2m")
                val codes = h.getJSONArray("weather_code")
                val days = h.getJSONArray("is_day")

                var i0 = 0
                for (i in 0 until times.length()) {
                    if (times.getString(i).substring(0, 13) == curTime.substring(0, 13)) { i0 = i; break }
                }

                val labels = ArrayList<String>()
                val tv = ArrayList<Float>()
                val cd = ArrayList<Int>()
                val dy = ArrayList<Boolean>()
                for (k in 0 until 8) {
                    val i = i0 + k
                    if (i >= times.length()) break
                    labels.add(if (k == 0) "Maint." else times.getString(i).substring(11, 16))
                    tv.add(temps.getDouble(i).toFloat())
                    cd.add(codes.getInt(i))
                    dy.add(days.getInt(i) == 1)
                }

                val views = RemoteViews(context.packageName, R.layout.widget_plein_ciel_hours)
                views.setTextViewText(R.id.widget_city2, city)
                views.setTextViewText(R.id.widget_updt, "MAJ " + java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date()))
                views.setImageViewBitmap(R.id.widget_chart, drawChart(labels, tv, cd, dy))
                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                if (intent != null) {
                    val pi = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
                    views.setOnClickPendingIntent(R.id.widget_root2, pi)
                }
                mgr.updateAppWidget(id, views)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun group(code: Int): String = when {
        code <= 1 -> "clear"
        code == 2 -> "partly"
        code == 3 -> "cloud"
        code == 45 || code == 48 -> "fog"
        code in 51..67 || code in 80..82 -> "rain"
        code in 71..77 || code == 85 || code == 86 -> "snow"
        else -> "thunder"
    }

    private fun drawChart(labels: List<String>, temps: List<Float>, codes: List<Int>, days: List<Boolean>): Bitmap {
        val w = 900
        val h = 260
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val n = temps.size
        if (n == 0) return bmp

        val textP = Paint(Paint.ANTI_ALIAS_FLAG); textP.color = 0xCCFFFFFF.toInt(); textP.textSize = 22f; textP.textAlign = Paint.Align.CENTER
        val tempP = Paint(Paint.ANTI_ALIAS_FLAG); tempP.color = 0xFFFFFFFF.toInt(); tempP.textSize = 30f; tempP.textAlign = Paint.Align.CENTER; tempP.isFakeBoldText = true
        val yellow = Paint(Paint.ANTI_ALIAS_FLAG); yellow.color = 0xFFFFD166.toInt(); yellow.style = Paint.Style.STROKE; yellow.strokeWidth = 5f; yellow.strokeCap = Paint.Cap.ROUND
        val dot = Paint(Paint.ANTI_ALIAS_FLAG); dot.color = 0xFFFFD166.toInt()

        val min = temps.min()!!
        val max = temps.max()!!
        val span = maxOf(1f, max - min)
        fun xAt(k: Int): Float = w.toFloat() * (k + 0.5f) / n
        fun yAt(t: Float): Float = 78f + (1f - (t - min) / span) * 80f

        val path = Path()
        for (k in 0 until n) { if (k == 0) path.moveTo(xAt(k), yAt(temps[k])) else path.lineTo(xAt(k), yAt(temps[k])) }
        c.drawPath(path, yellow)

        for (k in 0 until n) {
            val x = xAt(k)
            c.drawText(labels[k], x, 32f, textP)
            c.drawCircle(x, yAt(temps[k]), 6f, dot)
            drawIcon(c, x, 128f, codes[k], days[k])
            c.drawText(Math.round(temps[k]).toString() + "°", x, 240f, tempP)
        }
        return bmp
    }

    private fun drawCloud(c: Canvas, x: Float, y: Float, p: Paint) {
        c.drawCircle(x - 9f, y, 9f, p)
        c.drawCircle(x + 3f, y - 6f, 11f, p)
        c.drawCircle(x + 12f, y + 1f, 8f, p)
        c.drawRect(x - 11f, y, x + 14f, y + 8f, p)
    }

    private fun drawIcon(c: Canvas, x: Float, y: Float, code: Int, day: Boolean) {
        val g = group(code)
        val sunP = Paint(Paint.ANTI_ALIAS_FLAG); sunP.color = 0xFFFFD166.toInt()
        val sunS = Paint(Paint.ANTI_ALIAS_FLAG); sunS.color = 0xFFFFD166.toInt(); sunS.style = Paint.Style.STROKE; sunS.strokeWidth = 5f; sunS.strokeCap = Paint.Cap.ROUND
        val moonP = Paint(Paint.ANTI_ALIAS_FLAG); moonP.color = 0xFFE9EEFC.toInt()
        val cloudP = Paint(Paint.ANTI_ALIAS_FLAG); cloudP.color = 0xFFFFFFFF.toInt()
        val dropP = Paint(Paint.ANTI_ALIAS_FLAG); dropP.color = 0xFF8FD9FF.toInt(); dropP.style = Paint.Style.STROKE; dropP.strokeWidth = 4f; dropP.strokeCap = Paint.Cap.ROUND
        val boltP = Paint(Paint.ANTI_ALIAS_FLAG); boltP.color = 0xFFFFD23F.toInt()

        if (g == "clear" && day) {
            c.drawCircle(x, y, 13f, sunP)
            for (a in 0 until 8) {
                val ang = Math.PI * a / 4.0
                c.drawLine((x + cos(ang) * 19).toFloat(), (y + sin(ang) * 19).toFloat(), (x + cos(ang) * 26).toFloat(), (y + sin(ang) * 26).toFloat(), sunS)
            }
            return
        }
        if (g == "clear") {
            c.drawCircle(x, y, 13f, moonP)
            val cut = Paint(); cut.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            c.drawCircle(x + 6f, y - 5f, 11f, cut)
            return
        }
        if (g == "partly") {
            if (day) c.drawCircle(x - 8f, y - 10f, 9f, sunP) else c.drawCircle(x - 8f, y - 10f, 8f, moonP)
            drawCloud(c, x + 2f, y + 4f, cloudP)
            return
        }
        if (g == "fog") {
            drawCloud(c, x, y - 6f, cloudP)
            c.drawLine(x - 16f, y + 12f, x + 14f, y + 12f, dropP)
            c.drawLine(x - 12f, y + 19f, x + 16f, y + 19f, dropP)
            return
        }
        if (g == "rain") {
            drawCloud(c, x, y - 4f, cloudP)
            c.drawLine(x - 10f, y + 10f, x - 13f, y + 18f, dropP)
            c.drawLine(x, y + 10f, x - 3f, y + 18f, dropP)
            c.drawLine(x + 10f, y + 10f, x + 7f, y + 18f, dropP)
            return
        }
        if (g == "snow") {
            drawCloud(c, x, y - 4f, cloudP)
            c.drawCircle(x - 10f, y + 14f, 2.5f, cloudP)
            c.drawCircle(x, y + 17f, 2.5f, cloudP)
            c.drawCircle(x + 10f, y + 14f, 2.5f, cloudP)
            return
        }
        if (g == "thunder") {
            drawCloud(c, x, y - 6f, cloudP)
            val p = Path()
            p.moveTo(x + 2f, y + 2f); p.lineTo(x - 6f, y + 14f); p.lineTo(x - 1f, y + 14f)
            p.lineTo(x - 5f, y + 24f); p.lineTo(x + 7f, y + 10f); p.lineTo(x + 2f, y + 10f); p.close()
            c.drawPath(p, boltP)
            return
        }
        drawCloud(c, x, y, cloudP)
        drawCloud(c, x - 14f, y + 6f, cloudP)
    }
}
