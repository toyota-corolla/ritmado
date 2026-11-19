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

/** Widget de saldo: lê as mesmas preferências do financeiro e atualiza em cada gravação. */
class IncomeWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateOne(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
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
            val incomes = read(prefs.getString("income", "[]"))
            val expenses = read(prefs.getString("expenses", "[]"))
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val today = dateFormat.format(Date())
            val lastSeven = linkedMapOf<String, Double>()
            val cursor = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -6) }
            repeat(7) {
                lastSeven[dateFormat.format(cursor.time)] = 0.0
                cursor.add(Calendar.DAY_OF_YEAR, 1)
            }

            var total = 0.0
            var todayNet = 0.0
            fun accumulate(items: JSONArray, sign: Double) {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val amount = readAmount(item) * sign
                    val itemDate = normalizeDate(item.optString("date"))
                    total += amount
                    if (itemDate == today) todayNet += amount
                    if (lastSeven.containsKey(itemDate)) lastSeven[itemDate] = (lastSeven[itemDate] ?: 0.0) + amount
                }
            }
            accumulate(incomes, 1.0)
            accumulate(expenses, -1.0)

            // Mini gráfico do saldo acumulado (não apenas do movimento isolado por dia).
            val weekMovements = lastSeven.values.sum()
            var running = total - weekMovements
            val balancePoints = lastSeven.values.map { movement -> running += movement; running }

            val views = RemoteViews(context.packageName, R.layout.widget_income)
            views.setTextViewText(R.id.widget_income_total, currency(total))
            views.setTextColor(R.id.widget_income_total, if (total < 0) 0xFFFF7D85.toInt() else 0xFFE7FFF0.toInt())
            views.setTextViewText(R.id.widget_income_today, "HOJE  ${signedCurrency(todayNet)}")
            views.setTextColor(R.id.widget_income_today, if (todayNet < 0) 0xFFFF7D85.toInt() else 0xFFB5DCC4.toInt())
            views.setImageViewBitmap(R.id.widget_income_chart, makeSparkline(balancePoints))

            val open = Intent(context, MainActivity::class.java).putExtra("open_page", "money")
            val pending = PendingIntent.getActivity(context, 4101, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            manager.updateAppWidget(id, views)
        }

        private fun read(raw: String?): JSONArray = try { JSONArray(raw ?: "[]") } catch (_: Exception) { JSONArray() }

        /** Aceita números e strings antigas em formatos pt-BR e en-US. */
        private fun readAmount(item: JSONObject): Double {
            val raw = when {
                !item.isNull("amount") -> item.opt("amount")
                !item.isNull("value") -> item.opt("value")
                !item.isNull("valor") -> item.opt("valor")
                else -> null
            }
            if (raw is Number) return raw.toDouble()
            var text = raw?.toString()?.replace("R$", "")?.replace(" ", "")?.trim() ?: return 0.0
            if (text.isBlank()) return 0.0
            val comma = text.lastIndexOf(',')
            val dot = text.lastIndexOf('.')
            text = when {
                comma >= 0 && dot >= 0 && comma > dot -> text.replace(".", "").replace(',', '.')
                comma >= 0 && dot >= 0 && dot > comma -> text.replace(",", "")
                comma >= 0 -> text.replace(".", "").replace(',', '.')
                text.count { it == '.' } > 1 -> text.replace(".", "")
                else -> text
            }
            return text.toDoubleOrNull() ?: 0.0
        }

        private fun normalizeDate(raw: String): String {
            if (raw.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) return raw
            for (pattern in listOf("dd/MM/yyyy", "d/M/yyyy")) {
                try {
                    val parsed = SimpleDateFormat(pattern, Locale("pt", "BR")).apply { isLenient = false }.parse(raw)
                    if (parsed != null) return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(parsed)
                } catch (_: Exception) { /* tenta o próximo formato legado */ }
            }
            return ""
        }

        private fun currency(value: Double): String = (if (value < 0) "− R$ " else "R$ ") +
            String.format(Locale("pt", "BR"), "%,.2f", abs(value))

        private fun signedCurrency(value: Double): String {
            val sign = when { value > 0 -> "+ R$ "; value < 0 -> "− R$ "; else -> "R$ " }
            return sign + String.format(Locale("pt", "BR"), "%,.2f", abs(value))
        }

        private fun makeSparkline(values: List<Double>): Bitmap {
            val width = 180
            val height = 42
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val left = 4f; val right = width - 4f; val top = 4f; val bottom = height - 4f
            val guide = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x554D8063; strokeWidth = 1f }
            canvas.drawLine(left, (top + bottom) / 2f, right, (top + bottom) / 2f, guide)
            val minValue = values.minOrNull() ?: 0.0
            val maxValue = values.maxOrNull() ?: 1.0
            val range = (maxValue - minValue).coerceAtLeast(1.0)
            val points = values.mapIndexed { index, value ->
                val x = left + (right - left) * index / (values.size - 1).coerceAtLeast(1)
                val y = bottom - (((value - minValue) / range).toFloat() * (bottom - top))
                x to y
            }
            val path = Path()
            points.forEachIndexed { index, point -> if (index == 0) path.moveTo(point.first, point.second) else path.lineTo(point.first, point.second) }
            val rising = values.lastOrNull()?.let { it >= (values.firstOrNull() ?: it) } ?: true
            val color = if (rising) 0xFF35F59A.toInt() else 0xFFFF7279.toInt()
            val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = (color and 0x00FFFFFF) or 0x44000000; style = Paint.Style.STROKE; strokeWidth = 7f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = 2.5f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
            canvas.drawPath(path, glow)
            canvas.drawPath(path, line)
            points.lastOrNull()?.let { point -> Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }.also { canvas.drawCircle(point.first, point.second, 3.5f, it) } }
            return bitmap
        }
    }
}
