package com.ritmo.planner

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import org.json.JSONArray

/** Widget de tarefas: permite concluir tarefas sem abrir o aplicativo. */
class TodoWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray){ids.forEach{updateOne(context,manager,it)}}
    override fun onReceive(context:Context,intent:Intent){super.onReceive(context,intent);if(intent.action==ACTION_REFRESH)refreshAll(context)}

    companion object {
        const val ACTION_REFRESH="com.ritmo.planner.REFRESH_TODO_WIDGET"
        fun refreshAll(context:Context){val manager=AppWidgetManager.getInstance(context);val ids=manager.getAppWidgetIds(ComponentName(context,TodoWidgetProvider::class.java));ids.forEach{updateOne(context,manager,it)}}
        private fun updateOne(context:Context,manager:AppWidgetManager,widgetId:Int){
            val prefs=context.getSharedPreferences("ritmo_data",Context.MODE_PRIVATE);val all=try{JSONArray(prefs.getString("todos","[]"))}catch(_:Exception){JSONArray()};val pending=mutableListOf<org.json.JSONObject>()
            for(i in 0 until all.length()){val item=all.optJSONObject(i)?:continue;if(!item.optBoolean("done"))pending.add(item)}
            val views=RemoteViews(context.packageName,R.layout.widget_todo);views.setTextViewText(R.id.widget_todo_count,"${pending.size} ATIVAS")
            val rowIds=intArrayOf(R.id.todo_row_1,R.id.todo_row_2,R.id.todo_row_3);val textIds=intArrayOf(R.id.todo_line_1,R.id.todo_line_2,R.id.todo_line_3);val buttonIds=intArrayOf(R.id.todo_check_1,R.id.todo_check_2,R.id.todo_check_3)
            for(index in 0..2){if(index<pending.size){val item=pending[index];views.setViewVisibility(rowIds[index],android.view.View.VISIBLE);views.setViewVisibility(textIds[index],android.view.View.VISIBLE);views.setViewVisibility(buttonIds[index],android.view.View.VISIBLE);views.setTextViewText(textIds[index],item.optString("title"));views.setTextViewText(buttonIds[index],"○")
                    val toggle=Intent(context,TodoActionReceiver::class.java).putExtra(TodoActionReceiver.EXTRA_TODO_ID,item.optString("id"));val pi=PendingIntent.getBroadcast(context,(widgetId*10)+index,toggle,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);views.setOnClickPendingIntent(buttonIds[index],pi)
                }else{views.setViewVisibility(rowIds[index],android.view.View.GONE);views.setViewVisibility(textIds[index],android.view.View.GONE);views.setViewVisibility(buttonIds[index],android.view.View.GONE)}}
            if(pending.isEmpty()){views.setViewVisibility(R.id.widget_todo_empty,android.view.View.VISIBLE);views.setTextViewText(R.id.widget_todo_empty,"Tudo em dia  ·  bom trabalho")}else{views.setViewVisibility(R.id.widget_todo_empty,android.view.View.GONE)}
            val open=Intent(context,MainActivity::class.java);val openPi=PendingIntent.getActivity(context,5201,open,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);views.setOnClickPendingIntent(R.id.widget_todo_root,openPi)
            manager.updateAppWidget(widgetId,views)
        }
    }
}

/** Recebe apenas os toques nos círculos do widget de tarefas. */
class TodoActionReceiver : BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        val id=intent.getStringExtra(EXTRA_TODO_ID) ?: return
        val prefs=context.getSharedPreferences("ritmo_data",Context.MODE_PRIVATE)
        val tasks=try { JSONArray(prefs.getString("todos","[]")) } catch(_:Exception) { JSONArray() }
        for(i in 0 until tasks.length()) { val item=tasks.optJSONObject(i) ?: continue; if(item.optString("id")==id) item.put("done",!item.optBoolean("done")) }
        prefs.edit().putString("todos",tasks.toString()).apply()
        TodoWidgetProvider.refreshAll(context)
    }
    companion object { const val EXTRA_TODO_ID="todo_id" }
}
