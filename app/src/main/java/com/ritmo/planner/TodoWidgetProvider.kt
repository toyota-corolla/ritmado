package com.ritmo.planner

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import org.json.JSONArray
import org.json.JSONObject

/** Widget Mission Control: lista as missões abertas e aproveita a altura disponível. */
class TodoWidgetProvider : AppWidgetProvider() {
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
        const val ACTION_REFRESH = "com.ritmo.planner.REFRESH_TODO_WIDGET"

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TodoWidgetProvider::class.java))
            ids.forEach { updateOne(context, manager, it) }
        }

        private fun updateOne(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val prefs = context.getSharedPreferences("ritmo_data", Context.MODE_PRIVATE)
            val all = try {
                JSONArray(prefs.getString("missions", "[]"))
            } catch (_: Exception) {
                JSONArray()
            }
            val pending = mutableListOf<JSONObject>()
            for (index in 0 until all.length()) {
                val item = all.optJSONObject(index) ?: continue
                if (item.optString("status", "pendente") !in listOf("concluída", "cancelada")) pending.add(item)
            }
            val priorityRank = mapOf("CRÍTICA" to 0, "ALTA" to 1, "NORMAL" to 2, "BAIXA" to 3)
            pending.sortWith(compareBy<JSONObject> { it.optString("due", "9999-12-31").ifBlank { "9999-12-31" } }
                .thenBy { priorityRank[it.optString("priority").uppercase()] ?: 4 }
                .thenBy { it.optString("title").lowercase() })

            val options = manager.getAppWidgetOptions(widgetId)
            val allocatedHeight = options.getInt(
                AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            )
            // MAX_HEIGHT tracks user resizing. Reserve 36dp for header and outer padding,
            // then allocate one compact row for each ~34dp of remaining space.
            val maxRows = if (allocatedHeight > 0) {
                ((allocatedHeight - 36) / 34).coerceIn(1, 6)
            } else 2
            val visibleCount = minOf(pending.size, maxRows)

            val views = RemoteViews(context.packageName, R.layout.widget_todo)
            views.setTextViewText(R.id.widget_todo_count, "${pending.size} ATIVAS")

            val rowIds = intArrayOf(
                R.id.todo_row_1, R.id.todo_row_2, R.id.todo_row_3,
                R.id.todo_row_4, R.id.todo_row_5, R.id.todo_row_6
            )
            val textIds = intArrayOf(
                R.id.todo_line_1, R.id.todo_line_2, R.id.todo_line_3,
                R.id.todo_line_4, R.id.todo_line_5, R.id.todo_line_6
            )
            val metaIds = intArrayOf(
                R.id.todo_meta_1, R.id.todo_meta_2, R.id.todo_meta_3,
                R.id.todo_meta_4, R.id.todo_meta_5, R.id.todo_meta_6
            )
            val checkIds = intArrayOf(
                R.id.todo_check_1, R.id.todo_check_2, R.id.todo_check_3,
                R.id.todo_check_4, R.id.todo_check_5, R.id.todo_check_6
            )

            for (index in rowIds.indices) {
                if (index < visibleCount) {
                    val item = pending[index]
                    views.setViewVisibility(rowIds[index], View.VISIBLE)
                    views.setTextViewText(textIds[index], item.optString("title", "Missão sem título"))
                    val due = item.optString("due").takeIf { it.isNotBlank() }
                    val category = item.optString("category").takeIf { it.isNotBlank() }
                    val priority = item.optString("priority").takeIf { it.isNotBlank() }
                    val dueLabel = due?.let {
                        val shortDate = if (it.length >= 10) "${it.substring(8, 10)}/${it.substring(5, 7)}" else it
                        "PRAZO $shortDate${item.optString("dueTime").takeIf { t -> t.isNotBlank() }?.let { t -> " $t" } ?: ""}"
                    }
                    val details = listOfNotNull(dueLabel, category, priority).joinToString("  ·  ").ifBlank { "SEM PRAZO DEFINIDO" }
                    views.setTextViewText(metaIds[index], details)
                    views.setTextViewText(checkIds[index], "›")
                } else {
                    views.setViewVisibility(rowIds[index], View.GONE)
                }
            }

            if (pending.isEmpty()) {
                views.setViewVisibility(R.id.widget_todo_empty, View.VISIBLE)
                views.setViewVisibility(R.id.widget_todo_overflow, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_todo_empty, View.GONE)
                if (pending.size > visibleCount) {
                    views.setViewVisibility(R.id.widget_todo_overflow, View.VISIBLE)
                    views.setTextViewText(R.id.widget_todo_overflow, "+ ${pending.size - visibleCount} outras missões pendentes")
                } else {
                    views.setViewVisibility(R.id.widget_todo_overflow, View.GONE)
                }
            }

            val open = Intent(context, MainActivity::class.java).putExtra("open_page", "missions")
            val openPendingIntent = PendingIntent.getActivity(
                context,
                5301,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_todo_root, openPendingIntent)
            rowIds.forEach { views.setOnClickPendingIntent(it, openPendingIntent) }
            checkIds.forEach { views.setOnClickPendingIntent(it, openPendingIntent) }
            manager.updateAppWidget(widgetId, views)
        }
    }
}

