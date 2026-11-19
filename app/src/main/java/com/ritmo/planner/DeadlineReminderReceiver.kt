package com.ritmo.planner

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Agenda avisos locais para tarefas e missões quando o prazo termina. */
object DeadlineReminderScheduler {
    private const val ACTION_PREFIX = "com.ritmo.planner.DEADLINE."

    fun syncAll(context: Context) {
        val prefs = context.getSharedPreferences("ritmo_data", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("notifications_enabled", true)) {
            cancelAll(context, prefs.getString("todos", "[]"), "todo")
            cancelAll(context, prefs.getString("missions", "[]"), "mission")
            return
        }
        scheduleCollection(context, prefs.getString("todos", "[]"), "todo")
        scheduleCollection(context, prefs.getString("missions", "[]"), "mission")
    }

    private fun scheduleCollection(context: Context, raw: String?, type: String) {
        val items = try { JSONArray(raw ?: "[]") } catch (_: Exception) { JSONArray() }
        val prefs = context.getSharedPreferences("ritmo_data", Context.MODE_PRIVATE)
        val currentIds = HashSet<String>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val id = item.optString("id")
            if (id.isBlank()) continue
            val active = if (type == "todo") !item.optBoolean("done")
                else item.optString("status", "pendente") !in listOf("concluída", "cancelada")
            val due = item.optString("due")
            if (!active || due.isBlank()) {
                cancel(context, type, id)
                continue
            }
            currentIds.add(id)
            if (item.optBoolean("overdueNotified", false)) {
                cancel(context, type, id)
            } else {
                schedule(context, type, id, item.optString("title", "Item sem título"), due, item.optString("dueTime"))
            }
        }
        val trackingKey = "deadline_scheduled_ids_$type"
        val previousIds = prefs.getStringSet(trackingKey, emptySet())?.toSet() ?: emptySet()
        (previousIds - currentIds).forEach { cancel(context, type, it) }
        prefs.edit().putStringSet(trackingKey, currentIds).apply()
    }

    private fun cancelAll(context: Context, raw: String?, type: String) {
        val items = try { JSONArray(raw ?: "[]") } catch (_: Exception) { JSONArray() }
        for (i in 0 until items.length()) items.optJSONObject(i)?.optString("id")?.takeIf { it.isNotBlank() }?.let { cancel(context, type, it) }
    }

    fun schedule(context: Context, type: String, id: String, title: String, due: String, dueTime: String) {
        val deadline = deadlineMillis(due, dueTime) ?: return
        val triggerAt = maxOf(deadline + 1_000L, System.currentTimeMillis() + 2_000L)
        val pending = pendingIntent(context, type, id, title, due, dueTime)
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
    }

    fun cancel(context: Context, type: String, id: String) {
        val requestCode = "$type:$id".hashCode()
        val action = ACTION_PREFIX + type + "." + id
        val intent = Intent(context, DeadlineReminderReceiver::class.java).setAction(action)
        val pending = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending)
        pending.cancel()
    }

    private fun pendingIntent(context: Context, type: String, id: String, title: String, due: String, dueTime: String): PendingIntent {
        val intent = Intent(context, DeadlineReminderReceiver::class.java)
            .setAction(ACTION_PREFIX + type + "." + id)
            .putExtra("entity_type", type)
            .putExtra("entity_id", id)
            .putExtra("entity_title", title)
            .putExtra("deadline_date", due)
            .putExtra("deadline_time", dueTime)
        return PendingIntent.getBroadcast(
            context, "$type:$id".hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun deadlineMillis(due: String, dueTime: String): Long? {
        if (due.isBlank()) return null
        val time = if (Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d$").matches(dueTime)) dueTime else "23:59"
        return try {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { isLenient = false }.parse("$due $time")?.time
        } catch (_: Exception) { null }
    }
}

/** Dispara o aviso uma única vez por prazo; ao mudar o prazo, o app reprograma o alarme. */
class DeadlineReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("ritmo_data", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("notifications_enabled", true)) return

        val type = intent.getStringExtra("entity_type") ?: return
        if (type != "todo" && type != "mission") return
        val id = intent.getStringExtra("entity_id") ?: return
        val scheduledDate = intent.getStringExtra("deadline_date") ?: return
        val scheduledTime = intent.getStringExtra("deadline_time") ?: ""
        val key = if (type == "todo") "todos" else "missions"
        val items = try { JSONArray(prefs.getString(key, "[]")) } catch (_: Exception) { JSONArray() }
        var target: JSONObject? = null
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            if (item.optString("id") == id) { target = item; break }
        }
        val item = target ?: return
        val active = if (type == "todo") !item.optBoolean("done")
            else item.optString("status", "pendente") !in listOf("concluída", "cancelada")
        if (!active || item.optBoolean("overdueNotified", false)) return

        val currentDate = item.optString("due")
        val currentTime = item.optString("dueTime")
        if (currentDate != scheduledDate || currentTime != scheduledTime) return
        val dueAt = DeadlineReminderScheduler.deadlineMillis(currentDate, currentTime) ?: return
        if (System.currentTimeMillis() <= dueAt) {
            DeadlineReminderScheduler.schedule(context, type, id, item.optString("title"), currentDate, currentTime)
            return
        }

        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        item.put("overdueNotified", true)
        prefs.edit().putString(key, items.toString()).apply()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "LIFE.EXE — Prazos", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Avisos quando tarefas e missões passam do prazo"
                }
            )
        }

        val page = if (type == "todo") "todo" else "missions"
        val open = Intent(context, MainActivity::class.java).putExtra("open_page", page)
        val openPending = PendingIntent.getActivity(
            context, id.hashCode(), open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = item.optString("title", "item")
        val content = "Você esqueceu de fazer a $title"
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("LIFE.EXE  /  PRAZO ULTRAPASSADO")
                .setContentText(content)
                .setStyle(Notification.BigTextStyle().bigText(content))
                .setContentIntent(openPending)
                .setAutoCancel(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("LIFE.EXE  /  PRAZO ULTRAPASSADO")
                .setContentText(content)
                .setContentIntent(openPending)
                .setAutoCancel(true)
                .build()
        }
        try { manager.notify(("deadline:$type:$id").hashCode(), notification) } catch (_: SecurityException) { }
    }

    companion object { private const val CHANNEL_ID = "life_exe_deadline_reminders" }
}
