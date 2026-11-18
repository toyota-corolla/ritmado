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
import java.util.Calendar

/** Agenda o próximo lembrete respeitando a frequência configurada no hábito. */
object HabitReminderScheduler {
    fun schedule(
        context: Context,
        id: String,
        name: String,
        hour: Int,
        minute: Int,
        frequency: String,
        weeklyDay: Int
    ) {
        if (id.isBlank() || hour !in 0..23 || minute !in 0..59) return
        val next = Calendar.getInstance().apply {
            val now = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
            var attempts = 0
            while (!isScheduledDay(this, frequency, weeklyDay) && attempts < 8) {
                add(Calendar.DAY_OF_YEAR, 1)
                attempts++
            }
        }
        val reminder = Intent(context, HabitReminderReceiver::class.java)
            .putExtra("habit_id", id)
            .putExtra("habit_name", name)
            .putExtra("hour", hour)
            .putExtra("minute", minute)
            .putExtra("frequency", frequency)
            .putExtra("weekly_day", weeklyDay)
        val pending = PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            reminder,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pending)
    }

    private fun isScheduledDay(calendar: Calendar, frequency: String, weeklyDay: Int): Boolean = when (frequency) {
        "Dias úteis" -> calendar.get(Calendar.DAY_OF_WEEK) !in listOf(Calendar.SATURDAY, Calendar.SUNDAY)
        "Semanal" -> calendar.get(Calendar.DAY_OF_WEEK) == weeklyDay
        else -> true
    }
}

/** Lembretes locais de hábitos; não utiliza servidor nem conexão com a internet. */
class HabitReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("ritmo_data", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("notifications_enabled", true)) return

        val id = intent.getStringExtra("habit_id") ?: return
        val name = intent.getStringExtra("habit_name") ?: "Seu hábito"
        val hour = intent.getIntExtra("hour", 9)
        val minute = intent.getIntExtra("minute", 0)
        val frequency = intent.getStringExtra("frequency") ?: "Diário"
        val weeklyDay = intent.getIntExtra("weekly_day", Calendar.MONDAY)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "LIFE.EXE — Hábitos", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Lembretes locais dos hábitos cadastrados"
                }
            )
        }

        val open = Intent(context, MainActivity::class.java).putExtra("open_page", "habits")
        val openPending = PendingIntent.getActivity(
            context, id.hashCode(), open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("LIFE.EXE  /  HABIT SYSTEM")
                .setContentText("Hora de cuidar do hábito: $name")
                .setContentIntent(openPending)
                .setAutoCancel(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("LIFE.EXE  /  HABIT SYSTEM")
                .setContentText("Hora de cuidar do hábito: $name")
                .setContentIntent(openPending)
                .setAutoCancel(true)
                .build()
        }

        val canNotify = Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (canNotify) {
            try { manager.notify(id.hashCode(), notification) } catch (_: SecurityException) { }
        }

        // O próximo alarme é calculado respeitando diário, dias úteis ou dia semanal escolhido.
        HabitReminderScheduler.schedule(context, id, name, hour, minute, frequency, weeklyDay)
    }

    companion object { private const val CHANNEL_ID = "life_exe_habit_reminders" }
}

/** Reconstitui os alarmes de hábitos depois que o Android reinicia o aparelho. */
class HabitReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences("ritmo_data", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("notifications_enabled", true)) return
        val habits = try { org.json.JSONArray(prefs.getString("habits", "[]")) } catch (_: Exception) { org.json.JSONArray() }
        for (i in 0 until habits.length()) {
            val habit = habits.optJSONObject(i) ?: continue
            val time = habit.optString("reminder")
            if (!Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d$").matches(time)) continue
            val parts = time.split(":")
            HabitReminderScheduler.schedule(
                context,
                habit.optString("id"),
                habit.optString("name"),
                parts[0].toInt(),
                parts[1].toInt(),
                habit.optString("frequency", "Diário"),
                habit.optInt("weeklyDay", Calendar.MONDAY)
            )
        }
    }
}
