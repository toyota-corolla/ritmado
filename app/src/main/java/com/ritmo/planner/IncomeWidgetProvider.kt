package com.ritmo.planner

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.widget.RemoteViews
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** Widget financeiro: saldo acumulado e resultado líquido do dia. */
class IncomeWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateOne(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        updateOne(context, manager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) refreshAll(context)
    }

    companion object {
        const val ACTION_REFRESH = "com.ritmo.planner.REFRESH_INCOME_WIDGET"

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, IncomeWidgetProvider::class.java))
            ids.forEach { updateOne(context, manager, it) }
        }

        private fun updateOne(context: Context, manager: AppWidgetManager, id: Int) {
            val prefs = context.getSharedPreferences("ritmo_data", Context.MODE_PRIVATE)
            val income = read(prefs.getString("income", "[]"))
            val expenses = read(prefs.getString("expenses", "[]"))
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val today = dateFormat.format(Date())
            val dailyNet = linkedMapOf<String, Double>()
            val calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -6) }
            repeat(7) {
                dailyNet[dateFormat.format(calendar.time)] = 0.0
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }

            var totalBalance = 0.0
            var todayNet = 0.0
            fun collect(items: JSONArray, sign: Double) {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val value = readAmount(item) * sign
                    val itemDate = normalizeDate(item.optString("date"))
                    totalBalance += value
                    if (itemDate == today) todayNet += value
                    if (dailyNet.containsKey(itemDate)) {
                        dailyNet[itemDate] = (dailyNet[itemDate] ?: 0.0) + value
                    }
                }
            }
            collect(income, 1.0)
            collect(expenses, -1.0)

            val views = RemoteViews(context.packageName, R.layout.widget_income)
            views.setTextViewText(R.id.widget_income_total, currency(totalBalance))
            views.setTextViewText(R.id.widget_income_today, "Hoje: ${signedCurrency(todayNet)}")
            views.setImageViewBitmap(R.id.widget_income_chart, makeSparkline(dailyNet.values.toList()))

            val open = Intent(context, MainActivity::class.java).putExtra("open_page", "money")
            val pending = PendingIntent.getActivity(
                context,
                4101,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            manager.updateAppWidget(id, views)
        }

        private fun read(raw: String?): JSONArray = try {
            JSONArray(raw ?: "[]")
        } catch (_: Exception) {
            JSONArray()
        }

        /** Handles numeric JSON values and older localized strings such as "1.250,50". */
        private fun readAmount(item: JSONObject): Double {
            val raw = when {
                !item.isNull("amount") -> item.opt("amount")
                !item.isNull("value") -> item.opt("value")
                !item.isNull("valor") -> item.opt("valor")
                else -> null
            }
            if (raw is Number) return raw.toDouble()
            val text = raw?.toString()?.replace("R$", "")?.replace(" ", "") ?: return 0.0
            val normalized = if (text.contains(',')) {
                text.replace(".", "").replace(',', '.')
            } else text
            return normalized.toDoubleOrNull() ?: 0.0
        }

        private fun normalizeDate(raw: String): String {
            if (raw.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) return raw
            val formats = listOf("dd/MM/yyyy", "d/M/yyyy")
            for (pattern in formats) {
                try {
                    val parsed = SimpleDateFormat(pattern, Locale("pt", "BR")).apply { isLenient = false }.parse(raw)
                    if (parsed != null) return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(parsed)
                } catch (_: Exception) {
                    // Try the next legacy date pattern.
                }
            }
            return ""
        }

        private fun currency(value: Double): String {
            val prefix = if (value < 0) "− R$ " else "R$ "
            return prefix + String.format(Locale("pt", "BR"), "%,.2f", abs(value))
        }

        private fun signedCurrency(value: Double): String {
            val prefix = when {
                value > 0 -> "+ R$ "
                value < 0 -> "− R$ "
                else -> "R$ "
            }
            return prefix + String.format(Locale("pt", "BR"), "%,.2f", abs(value))
        }

        private fun makeSparkline(values: List<Double>): Bitmap {
            val width = 220
            val height = 90
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF19F58A.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 6f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF19F58A.toInt()
                style = Paint.Style.FILL
            }
            val guide = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF28513C.toInt()
                strokeWidth = 1.5f
            }
            val left = 5f
            val right = width - 5f
            val top = 8f
            val bottom = height - 8f
            canvas.drawLine(left, (top + bottom) / 2, right, (top + bottom) / 2, guide)
            val maxValue = (values.maxOfOrNull { abs(it) } ?: 0.0).coerceAtLeast(1.0)
            val path = Path()
            val coords = values.mapIndexed { index, value ->
                val x = left + (right - left) * index / (values.size - 1).coerceAtLeast(1)
                val y = (top + bottom) / 2 - ((value / maxValue).toFloat() * (bottom - top) / 2)
                x to y
            }
            coords.forEachIndexed { index, point ->
                if (index == 0) path.moveTo(point.first, point.second) else path.lineTo(point.first, point.second)
            }
            canvas.drawPath(path, line)
            coords.forEach { canvas.drawCircle(it.first, it.second, 4.5f, dot) }
            return bitmap
        }
    }
}
