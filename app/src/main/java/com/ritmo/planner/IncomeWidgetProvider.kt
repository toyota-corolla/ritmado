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
import android.widget.RemoteViews
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Widget financeiro compacto: saldo líquido do dia (receitas menos gastos). */
class IncomeWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { ids.forEach { updateOne(context, manager, it) } }
    override fun onReceive(context: Context, intent: Intent) { super.onReceive(context, intent); if (intent.action == ACTION_REFRESH) refreshAll(context) }

    companion object {
        const val ACTION_REFRESH = "com.ritmo.planner.REFRESH_INCOME_WIDGET"
        fun refreshAll(context: Context) { val manager=AppWidgetManager.getInstance(context); val ids=manager.getAppWidgetIds(ComponentName(context,IncomeWidgetProvider::class.java)); ids.forEach{updateOne(context,manager,it)} }

        private fun updateOne(context:Context,manager:AppWidgetManager,id:Int) {
            val prefs=context.getSharedPreferences("ritmo_data",Context.MODE_PRIVATE)
            val income=read(prefs.getString("income","[]")); val expenses=read(prefs.getString("expenses","[]"))
            val fmt=SimpleDateFormat("yyyy-MM-dd",Locale.US); val today=fmt.format(Date())
            val totals=linkedMapOf<String,Double>(); val cal=Calendar.getInstance();cal.add(Calendar.DAY_OF_YEAR,-6);repeat(7){totals[fmt.format(cal.time)]=0.0;cal.add(Calendar.DAY_OF_YEAR,1)}
            var todayNet=0.0
            var totalBalance=0.0
            fun collect(items:JSONArray,sign:Double){for(i in 0 until items.length()){val item=items.optJSONObject(i)?:continue;val d=item.optString("date");val amount=item.optDouble("amount",0.0)*sign;totalBalance+=amount;if(d==today)todayNet+=amount;if(totals.containsKey(d))totals[d]=(totals[d]?:0.0)+amount}}
            collect(income,1.0);collect(expenses,-1.0)
            val views=RemoteViews(context.packageName,R.layout.widget_income)
            val totalPrefix=if(totalBalance<0)"− R$ " else "R$ "
            val todayPrefix=if(todayNet<0)"− R$ " else "R$ "
            views.setTextViewText(R.id.widget_income_total,totalPrefix+String.format(Locale("pt","BR"),"%,.2f",kotlin.math.abs(totalBalance)))
            views.setTextViewText(R.id.widget_income_today,"Hoje: "+todayPrefix+String.format(Locale("pt","BR"),"%,.2f",kotlin.math.abs(todayNet)))
            views.setImageViewBitmap(R.id.widget_income_chart,makeSparkline(totals.values.toList()))
            val open=Intent(context,MainActivity::class.java);val pending=PendingIntent.getActivity(context,4101,open,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root,pending);manager.updateAppWidget(id,views)
        }
        private fun read(raw:String?)=try{JSONArray(raw?:"[]")}catch(_:Exception){JSONArray()}
        private fun makeSparkline(values:List<Double>):Bitmap {
            val width=220;val height=90;val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);val canvas=Canvas(bitmap)
            val line=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=0xFF19F58A.toInt();style=Paint.Style.STROKE;strokeWidth=6f;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND}
            val dot=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=0xFF19F58A.toInt();style=Paint.Style.FILL}
            val guide=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=0xFF123D29;strokeWidth=1.5f}
            val left=5f;val right=width-5f;val top=8f;val bottom=height-8f;canvas.drawLine(left,(top+bottom)/2,right,(top+bottom)/2,guide)
            val max=(values.maxOfOrNull{kotlin.math.abs(it)}?:0.0).coerceAtLeast(1.0);val path=Path()
            val coords=values.mapIndexed{index,value->val x=left+(right-left)*index/(values.size-1).coerceAtLeast(1);val y=(top+bottom)/2-((value/max).toFloat()*(bottom-top)/2);x to y}
            coords.forEachIndexed{index,p->if(index==0)path.moveTo(p.first,p.second)else path.lineTo(p.first,p.second)};canvas.drawPath(path,line);coords.forEach{canvas.drawCircle(it.first,it.second,4.5f,dot)};return bitmap
        }
    }
}
