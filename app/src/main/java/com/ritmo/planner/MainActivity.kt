package com.ritmo.planner

import android.app.Activity
import android.os.Bundle
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.Gravity
import android.view.View
import android.widget.*
import android.text.InputType
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar

private const val BG = 0xFF020906.toInt()
private const val INK = 0xFFB9FFD9.toInt()
private const val MUTED = 0xFF5E9879.toInt()
private const val AQUA = 0xFF19F58A.toInt()
private const val PANEL = 0xFF07150F.toInt()
private const val GRID = 0xFF123D29.toInt()

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("ritmo_data", MODE_PRIVATE) }
    private lateinit var root: LinearLayout
    private var page = "home"
    private val date get() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    private val prettyDate get() = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date())

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); showPage("home") }

    private fun showPage(target: String) {
        page = target
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BG) }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(10, 10, 10, 14) }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(3, 7, 3, 7)
            background = android.graphics.drawable.GradientDrawable().apply { setColor(0xFF020A06.toInt()); setStroke(1, 0xFF12683D.toInt()) }
        }
        nav.addView(navButton("home", "⌂\nInício"), LinearLayout.LayoutParams(0, 58, 1f))
        nav.addView(navButton("body", "▥\nEstatísticas"), LinearLayout.LayoutParams(0, 58, 1f))
        val add = TextView(this).apply {
            text = "+"; textSize = 36f; gravity = Gravity.CENTER; setTextColor(AQUA)
            background = neonPanel(0xFF04150C.toInt(), 0xFF19F58A.toInt(), 3)
            setOnClickListener { showQuickAdd() }
        }
        nav.addView(add, LinearLayout.LayoutParams(58, 58).apply { setMargins(4, 0, 4, 0) })
        nav.addView(navButton("social", "♜\nConquistas"), LinearLayout.LayoutParams(0, 58, 1f))
        nav.addView(navButton("settings", "⚙\nConfig."), LinearLayout.LayoutParams(0, 58, 1f))
        root.addView(nav)
        setContentView(root)
        when(target) {
            "money" -> moneyPage(body)
            "body" -> bodyPage(body)
            "social" -> socialPage(body)
            "todo" -> todoPage(body)
            "settings" -> settingsPage(body)
            else -> homePage(body)
        }
    }

    private fun navButton(target:String, caption:String) = TextView(this).apply {
        text = caption; gravity = Gravity.CENTER; textSize = 10f
        setTextColor(if(page==target) AQUA else MUTED)
        setOnClickListener { showPage(target) }
    }

    private fun showQuickAdd() {
        val options = arrayOf("Registrar receita ou gasto", "Registrar treino", "Adicionar tarefa", "Registrar avanço")
        android.app.AlertDialog.Builder(this).setTitle("NOVO REGISTRO").setItems(options) { _, which ->
            when(which) { 0 -> showPage("money"); 1 -> showPage("body"); 2 -> showPage("todo"); 3 -> showPage("social") }
        }.show()
    }

    private fun settingsPage(b:LinearLayout) {
        header(b, "05 / SISTEMA", "Configurações")
        card(b) {
            addView(label("LIFE.EXE", 22, AQUA, true))
            addView(label("Planeje. Registre. Evolua.", 13, MUTED))
            addView(label("Seus registros são armazenados localmente neste dispositivo.", 12, INK))
        }
        action(b, "VOLTAR AO INÍCIO") { showPage("home") }
    }

    private fun homePage(b: LinearLayout) {
        val headerRow = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(4, 5, 4, 10) }
        headerRow.addView(label("◎", 48, AQUA, true), LinearLayout.LayoutParams(66, 66))
        val brand = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER_VERTICAL }
        brand.addView(label("LIFE.EXE", 25, AQUA, true))
        brand.addView(label("PLANEJE. REGISTRE. EVOLUA.", 10, MUTED, true))
        headerRow.addView(brand, LinearLayout.LayoutParams(0,-2,1f))
        headerRow.addView(label("● ONLINE", 10, AQUA, true))
        b.addView(headerRow)

        val balance = totalIncome() - totalExpenses()
        val today = todayNet()
        val finance = lastSevenDaysNet()
        val financeTrend = if (finance.size >= 2) finance.last().second - finance.first().second else 0.0
        val moneyRow = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        dashboardCard(moneyRow, 1f) {
            addView(label("▣  SALDO TOTAL", 11, AQUA, true))
            addView(label("R$ ${money(balance)}", 21, INK, true))
            addView(label("${if(balance>=0) "▲" else "▼"} ${if(balance>=0) "POSITIVO" else "NEGATIVO"}", 10, if(balance>=0) AQUA else 0xFFFF6B6B.toInt(), true))
            addView(IncomeChart(this@MainActivity, finance).apply { layoutParams=LinearLayout.LayoutParams(-1,42) })
        }
        dashboardCard(moneyRow, 1f) {
            addView(label("▦  SALDO DE HOJE", 11, AQUA, true))
            addView(label("R$ ${money(today)}", 21, INK, true))
            addView(label("${if(today>=0) "▲" else "▼"} ENTRADAS − SAÍDAS", 9, if(today>=0) AQUA else 0xFFFF6B6B.toInt(), true))
            addView(IncomeChart(this@MainActivity, finance).apply { layoutParams=LinearLayout.LayoutParams(-1,42) })
        }
        b.addView(moneyRow)

        val metricsRow = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.TOP }
        val exercises = todayExercise()
        val totals = allExerciseTotals()
        val workoutTrend = workoutDirection()
        dashboardCard(metricsRow, 1f) {
            addView(label("▥  DESEMPENHO", 11, AQUA, true))
            addView(label("FLEXÕES", 10, MUTED, true))
            addView(label("${totals.first}", 27, INK, true))
            addView(label("Hoje: ${exercises.first}", 10, MUTED))
            addView(View(this@MainActivity).apply { setBackgroundColor(GRID) }, LinearLayout.LayoutParams(-1,1).apply { setMargins(0,5,0,5) })
            addView(label("BARRAS", 10, MUTED, true))
            addView(label("${totals.second}", 27, INK, true))
            addView(label("Hoje: ${exercises.second}", 10, MUTED))
            addView(label(workoutTrend.first, 10, if(workoutTrend.first.contains("MELHORANDO")) AQUA else if(workoutTrend.first.contains("PIORANDO")) 0xFFFF6B6B.toInt() else MUTED, true))
        }
        dashboardCard(metricsRow, 1f) {
            addView(label("⌁  EVOLUÇÃO FINANCEIRA", 11, AQUA, true))
            addView(label("ÚLTIMOS 7 DIAS", 9, MUTED, true))
            addView(IncomeChart(this@MainActivity, finance).apply { layoutParams=LinearLayout.LayoutParams(-1,150) })
            addView(label("Variação: R$ ${money(kotlin.math.abs(financeTrend))}", 10, if(financeTrend>=0) AQUA else 0xFFFF6B6B.toInt(), true))
        }
        b.addView(metricsRow)

        val advances = readArray("social")
        card(b) {
            addView(label("›  PROJETO LIBERDADE", 14, AQUA, true))
            addView(label("Construindo uma vida livre.", 12, MUTED))
            val divider = View(this@MainActivity).apply { setBackgroundColor(0xFF17683D.toInt()) }
            addView(divider, LinearLayout.LayoutParams(-1,1).apply { setMargins(0,3,0,7) })
            addView(label("${advances.length()} AVANÇOS REGISTRADOS", 12, INK, true))
            val from = maxOf(0, advances.length()-2)
            for(i in advances.length()-1 downTo from) {
                val item=advances.optJSONObject(i) ?: continue
                addView(label("✓  ${item.optString("title")}  •  ${item.optString("date")}", 11, INK))
            }
            if(advances.length()==0) addView(label("Registre suas conquistas e próximos passos para acompanhar sua evolução.", 11, MUTED))
            action(this, "＋  REGISTRAR AVANÇO") { showPage("social") }
        }

        val pending = pendingTodos()
        card(b) {
            val titleRow=LinearLayout(this@MainActivity).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
            titleRow.addView(label("☑  TO DO LIST", 14, AQUA, true), LinearLayout.LayoutParams(0,-2,1f))
            titleRow.addView(label("${pending.length()} PENDENTES", 9, AQUA, true))
            addView(titleRow)
            addView(View(this@MainActivity).apply { setBackgroundColor(0xFF17683D.toInt()) }, LinearLayout.LayoutParams(-1,1).apply { setMargins(0,2,0,6) })
            for(i in 0 until minOf(3,pending.length())) {
                val task=pending.optJSONObject(i) ?: continue
                val line=LinearLayout(this@MainActivity).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(0,3,0,3) }
                val taskCheck=CheckBox(this@MainActivity).apply {
                    isChecked=false
                    buttonTintList=android.content.res.ColorStateList.valueOf(AQUA)
                    setOnCheckedChangeListener { _, checked ->
                        if(checked) {
                            val all=readArray("todos")
                            for(j in 0 until all.length()) { val entry=all.optJSONObject(j) ?: continue; if(entry.optString("id")==task.optString("id")) entry.put("done",true) }
                            saveArray("todos",all); TodoWidgetProvider.refreshAll(this@MainActivity); showPage("home")
                        }
                    }
                }
                line.addView(taskCheck, LinearLayout.LayoutParams(34,42))
                line.addView(label(task.optString("title"), 12, INK), LinearLayout.LayoutParams(0,-2,1f))
                line.addView(label("PENDENTE", 8, AQUA, true))
                addView(line)
                if(i<minOf(3,pending.length())-1) addView(View(this@MainActivity).apply { setBackgroundColor(0xFF123D29.toInt()) }, LinearLayout.LayoutParams(-1,1))
            }
            if(pending.length()==0) addView(label("Nenhuma tarefa pendente.", 12, MUTED))
            action(this, "＋  GERENCIAR TAREFAS") { showPage("todo") }
        }
        action(b, "＋  REGISTRO RÁPIDO") { showQuickAdd() }
    }

    private fun workoutDirection(): Pair<String,String> {
        val a = readArray("workouts")
        val now = Calendar.getInstance(); val current = now.timeInMillis
        val week = 7L * 24L * 60L * 60L * 1000L
        var recent = 0; var previous = 0
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        for(i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val d = try { fmt.parse(o.optString("date"))?.time } catch(_:Exception) { null } ?: continue
            val count = o.optInt("push") + o.optInt("pull")
            if(current-d in 0..week) recent += count else if(current-d in week..(2*week)) previous += count
        }
        return when { recent > previous -> "▲ MELHORANDO" to "Mais repetições nos últimos 7 dias"; recent < previous -> "▼ PIORANDO" to "Menos repetições nos últimos 7 dias"; else -> "◆ ESTÁVEL" to "Sem variação clara nos últimos 7 dias" }
    }

    private fun moneyPage(b: LinearLayout) {
        header(b,"01 / FINANCEIRO","Receitas e gastos")
        val balance = totalIncome() - totalExpenses()
        card(b) {
            addView(label("SALDO ACUMULADO",12,MUTED,true))
            addView(label("R$ ${money(balance)}",32,INK,true))
            addView(label("Hoje: + R$ ${money(todayIncome())}  /  − R$ ${money(todayExpenses())}",12,MUTED))
        }
        b.addView(label("NOVA RECEITA",13,MUTED,true))
        val incomeValue = field("Valor recebido (ex.: 150,00)", true)
        val incomeDesc = field("Origem / descrição", false)
        b.addView(incomeValue); b.addView(incomeDesc)
        action(b,"＋  SALVAR RECEITA") {
            val amount = parseAmount(incomeValue.text.toString())
            if (amount == null || amount <= 0) toast("Digite um valor válido") else {
                val a = readArray("income"); a.put(JSONObject().put("date",date).put("amount",amount).put("desc",incomeDesc.text.toString().ifBlank { "Receita" }))
                saveArray("income",a); refreshWidgets(); showPage("money"); toast("Receita salva")
            }
        }
        b.addView(label("NOVO GASTO",13,MUTED,true))
        val expenseValue = field("Valor gasto (ex.: 35,90)", true)
        val expenseDesc = field("Com o que você gastou?", false)
        b.addView(expenseValue); b.addView(expenseDesc)
        action(b,"−  REGISTRAR GASTO") {
            val amount = parseAmount(expenseValue.text.toString())
            if (amount == null || amount <= 0) toast("Digite um valor válido") else {
                val a = readArray("expenses"); a.put(JSONObject().put("date",date).put("amount",amount).put("desc",expenseDesc.text.toString().ifBlank { "Gasto" }))
                saveArray("expenses",a); refreshWidgets(); showPage("money"); toast("Gasto registrado e descontado do saldo")
            }
        }
        b.addView(label("HISTÓRICO DE RECEITAS",13,MUTED,true))
        val incomes = readArray("income")
        for (i in incomes.length()-1 downTo 0) { val o=incomes.getJSONObject(i); editableRecord(b,"＋ ${o.optString("desc")}  •  ${o.optString("date")}","R$ ${money(o.optDouble("amount"))}", "Editar receita") { editMoneyRecord("income",i) } }
        if(incomes.length()==0) b.addView(label("Nenhuma receita registrada.",13,MUTED))
        b.addView(label("HISTÓRICO DE GASTOS",13,MUTED,true))
        val expenses = readArray("expenses")
        for (i in expenses.length()-1 downTo 0) { val o=expenses.getJSONObject(i); editableRecord(b,"− ${o.optString("desc")}  •  ${o.optString("date")}","R$ ${money(o.optDouble("amount"))}", "Editar gasto") { editMoneyRecord("expenses",i) } }
        if(expenses.length()==0) b.addView(label("Nenhum gasto registrado.",13,MUTED))
    }

    private fun todoPage(b: LinearLayout) {
        header(b,"04 / ORGANIZAÇÃO","Lista de tarefas")
        val title = field("O que precisa ser feito?", false)
        b.addView(title)
        action(b,"＋  ADICIONAR TAREFA") {
            val text = title.text.toString().trim()
            if (text.isBlank()) toast("Digite uma tarefa") else {
                val tasks = readArray("todos")
                tasks.put(JSONObject().put("id",System.currentTimeMillis().toString()).put("title",text).put("done",false))
                saveArray("todos",tasks); TodoWidgetProvider.refreshAll(this@MainActivity); showPage("todo"); toast("Tarefa adicionada")
            }
        }
        val tasks = readArray("todos")
        val pendingCount = (0 until tasks.length()).count { !tasks.optJSONObject(it)!!.optBoolean("done") }
        card(b) { addView(label("$pendingCount PENDENTE(S)  •  ${tasks.length()-pendingCount} CONCLUÍDA(S)",12,MUTED,true)) }
        for (i in tasks.length()-1 downTo 0) {
            val item = tasks.getJSONObject(i)
            val id = item.optString("id")
            val done = item.optBoolean("done")
            card(b) {
                val line = LinearLayout(this@MainActivity).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
                val check = CheckBox(this@MainActivity).apply { isChecked=done; buttonTintList=android.content.res.ColorStateList.valueOf(AQUA); setOnCheckedChangeListener { _, checked -> setTodoDone(id,checked) } }
                line.addView(check)
                line.addView(label(item.optString("title"),15,if(done) MUTED else INK,true).apply { if(done) paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG }, LinearLayout.LayoutParams(0,-2,1f))
                val remove = Button(this@MainActivity).apply { text="×"; textSize=18f; isAllCaps=false; setTextColor(MUTED); background=pill(PANEL); setOnClickListener { deleteTodo(id) } }
                line.addView(remove, LinearLayout.LayoutParams(44,42))
                addView(line)
            }
        }
        if (tasks.length()==0) b.addView(label("Sua lista está vazia. Adicione a primeira tarefa.",13,MUTED))
        b.addView(label("O widget mostra até três tarefas pendentes. Marque uma como concluída no app ou diretamente no widget.",11,MUTED))
    }

    private fun setTodoDone(id:String,done:Boolean) {
        val tasks=readArray("todos")
        for(i in 0 until tasks.length()) { val item=tasks.optJSONObject(i) ?: continue; if(item.optString("id")==id) item.put("done",done) }
        saveArray("todos",tasks); TodoWidgetProvider.refreshAll(this); showPage("todo")
    }
    private fun deleteTodo(id:String) {
        val old=readArray("todos"); val fresh=JSONArray()
        for(i in 0 until old.length()) { val item=old.optJSONObject(i) ?: continue; if(item.optString("id")!=id) fresh.put(item) }
        saveArray("todos",fresh); TodoWidgetProvider.refreshAll(this); showPage("todo")
    }

    private fun bodyPage(b: LinearLayout) {
        header(b,"02 / PERFORMANCE","Treino físico")
        val totals = allExerciseTotals()
        card(b) { addView(label("ACUMULADO",12,MUTED,true)); addView(label("${totals.first} flexões  •  ${totals.second} barras",22,INK,true)) }
        val push = field("Quantidade de flexões",true); val pull = field("Quantidade de barras",true); val note=field("Observação (opcional)",false)
        b.addView(push); b.addView(pull); b.addView(note)
        action(b,"SALVAR TREINO") {
            val p=push.text.toString().toIntOrNull() ?: 0; val q=pull.text.toString().toIntOrNull() ?: 0
            if(p<0||q<0||(p==0&&q==0)) toast("Informe pelo menos uma quantidade válida") else {
                val a=readArray("workouts"); a.put(JSONObject().put("date",date).put("push",p).put("pull",q).put("note",note.text.toString()))
                saveArray("workouts",a); showPage("body"); toast("Treino salvo")
            }
        }
        b.addView(label("EVOLUÇÃO — ÚLTIMOS REGISTROS",13,MUTED,true))
        val arr=readArray("workouts"); val recent=mutableListOf<Triple<String,Int,Int>>()
        for(i in maxOf(0,arr.length()-7) until arr.length()) { val o=arr.getJSONObject(i); recent.add(Triple(o.optString("date").takeLast(5),o.optInt("push"),o.optInt("pull"))) }
        b.addView(ProgressChart(this,recent).apply { layoutParams=LinearLayout.LayoutParams(-1,210).apply { setMargins(0,8,0,12) } })
        for(i in arr.length()-1 downTo 0) { val o=arr.getJSONObject(i); editableRecord(b,o.optString("date"),"${o.optInt("push")} flexões  •  ${o.optInt("pull")} barras", "Editar treino") { editWorkoutRecord(i) } }
        if(arr.length()==0) b.addView(label("Seu gráfico aparecerá após o primeiro treino.",13,MUTED))
    }

    private fun socialPage(b: LinearLayout) {
        header(b,"03 / IMPACTO","Projeto social")
        card(b) { addView(label("DIÁRIO DE EVOLUÇÃO",12,MUTED,true)); addView(label("Registre o que saiu do papel.",20,INK,true)); addView(label("Documente entregas, contatos, reuniões, resultados e próximos passos.",12,MUTED)) }
        val title=field("Título do avanço",false); val detail=field("O que aconteceu? Qual o próximo passo?",false, true)
        b.addView(title); b.addView(detail)
        action(b,"REGISTRAR AVANÇO") {
            if(title.text.isNullOrBlank() || detail.text.isNullOrBlank()) toast("Preencha título e descrição") else {
                val a=readArray("social"); a.put(JSONObject().put("date",date).put("title",title.text.toString()).put("detail",detail.text.toString()))
                saveArray("social",a); showPage("social"); toast("Avanço registrado")
            }
        }
        b.addView(label("LINHA DO TEMPO",13,MUTED,true))
        val arr=readArray("social")
        for(i in arr.length()-1 downTo 0) { val o=arr.getJSONObject(i); card(b) { addView(label(o.optString("date"),11,MUTED,true)); addView(label(o.optString("title"),18,INK,true)); addView(label(o.optString("detail"),13,MUTED)); val edit=Button(this@MainActivity).apply { text="✎  EDITAR AVANÇO"; textSize=11f; isAllCaps=false; setTextColor(INK); background=pill(PANEL); setOnClickListener { editSocialRecord(i) } }; addView(edit,LinearLayout.LayoutParams(-1,42)) } }
        if(arr.length()==0) b.addView(label("Nenhum avanço registrado. Comece pelo primeiro.",13,MUTED))
    }

    private fun editableRecord(parent:LinearLayout,title:String,value:String,buttonText:String,onEdit:()->Unit) {
        card(parent) {
            val line=LinearLayout(this@MainActivity).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
            line.addView(label(title,13,INK,true),LinearLayout.LayoutParams(0,-2,1f))
            line.addView(label(value,13,AQUA,true))
            addView(line)
            val edit=Button(this@MainActivity).apply { text="✎  $buttonText"; textSize=11f; isAllCaps=false; setTextColor(INK); background=pill(PANEL); setOnClickListener { onEdit() } }
            addView(edit,LinearLayout.LayoutParams(-1,40))
        }
    }

    private fun editMoneyRecord(key:String,index:Int) {
        val arr=readArray(key); val item=arr.optJSONObject(index) ?: return
        val amount=field("Valor",true).apply { setText(item.optString("amount")) }
        val desc=field("Descrição",false).apply { setText(item.optString("desc")) }
        val dateField=field("Data (AAAA-MM-DD)",false).apply { setText(item.optString("date",date)) }
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(8,4,8,0); addView(amount); addView(desc); addView(dateField) }
        android.app.AlertDialog.Builder(this).setTitle(if(key=="income") "Editar receita" else "Editar gasto").setView(box)
            .setNegativeButton("Cancelar",null).setPositiveButton("Salvar") { _,_ ->
                val parsed=parseAmount(amount.text.toString())
                if(parsed==null || parsed<=0 || dateField.text.toString().isBlank()) { toast("Confira valor e data"); return@setPositiveButton }
                item.put("amount",parsed).put("desc",desc.text.toString().ifBlank { if(key=="income") "Receita" else "Gasto" }).put("date",dateField.text.toString().trim())
                saveArray(key,arr); refreshWidgets(); showPage("money"); toast("Registro atualizado")
            }.show()
    }

    private fun editWorkoutRecord(index:Int) {
        val arr=readArray("workouts"); val item=arr.optJSONObject(index) ?: return
        val push=field("Quantidade de flexões",true).apply { setText(item.optString("push")) }
        val pull=field("Quantidade de barras",true).apply { setText(item.optString("pull")) }
        val note=field("Observação",false,true).apply { setText(item.optString("note")) }
        val dateField=field("Data (AAAA-MM-DD)",false).apply { setText(item.optString("date",date)) }
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(8,4,8,0); addView(push); addView(pull); addView(note); addView(dateField) }
        android.app.AlertDialog.Builder(this).setTitle("Editar treino").setView(box).setNegativeButton("Cancelar",null)
            .setPositiveButton("Salvar") { _,_ ->
                val p=push.text.toString().toIntOrNull(); val q=pull.text.toString().toIntOrNull()
                if(p==null || q==null || p<0 || q<0 || (p==0 && q==0) || dateField.text.toString().isBlank()) { toast("Confira quantidades e data"); return@setPositiveButton }
                item.put("push",p).put("pull",q).put("note",note.text.toString()).put("date",dateField.text.toString().trim())
                saveArray("workouts",arr); showPage("body"); toast("Treino atualizado")
            }.show()
    }

    private fun editSocialRecord(index:Int) {
        val arr=readArray("social"); val item=arr.optJSONObject(index) ?: return
        val title=field("Título",false).apply { setText(item.optString("title")) }
        val detail=field("Descrição / evolução",false,true).apply { setText(item.optString("detail")) }
        val dateField=field("Data (AAAA-MM-DD)",false).apply { setText(item.optString("date",date)) }
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(8,4,8,0); addView(title); addView(detail); addView(dateField) }
        android.app.AlertDialog.Builder(this).setTitle("Editar evolução").setView(box).setNegativeButton("Cancelar",null)
            .setPositiveButton("Salvar") { _,_ ->
                if(title.text.isNullOrBlank() || detail.text.isNullOrBlank() || dateField.text.toString().isBlank()) { toast("Preencha título, descrição e data"); return@setPositiveButton }
                item.put("title",title.text.toString().trim()).put("detail",detail.text.toString().trim()).put("date",dateField.text.toString().trim())
                saveArray("social",arr); showPage("social"); toast("Evolução atualizada")
            }.show()
    }

    private fun refreshWidgets() { IncomeWidgetProvider.refreshAll(this); TodoWidgetProvider.refreshAll(this) }
    private fun parseAmount(raw:String):Double? = raw.trim().replace("R$","").replace(" ","").let { if(it.contains(",")) it.replace(".","").replace(",",".").toDoubleOrNull() else it.toDoubleOrNull() }
    private fun header(b:LinearLayout,tag:String,title:String){ b.addView(label(tag,12,MUTED,true)); b.addView(label(title,29,INK,true)) }
    private fun label(s:String,size:Int,color:Int,bold:Boolean=false)=TextView(this).apply {
        text=s; textSize=size.toFloat(); setTextColor(color)
        typeface=android.graphics.Typeface.MONOSPACE
        if(bold) setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        if(color==AQUA) setShadowLayer(7f,0f,0f,0xAA19F58A.toInt())
        setPadding(0,5,0,7)
    }
    private fun pill(color:Int)=android.graphics.drawable.GradientDrawable().apply { setColor(color); cornerRadius=22f }
    private fun neonPanel(fill:Int, stroke:Int, strokeWidth:Int=1)=android.graphics.drawable.GradientDrawable().apply {
        setColor(fill); cornerRadius=9f; setStroke(strokeWidth,stroke)
    }
    private fun dashboardCard(parent:LinearLayout, weight:Float, content:LinearLayout.()->Unit) {
        val box=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; setPadding(10,10,10,9)
            background=android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF071A10.toInt(),0xFF020A06.toInt())).apply { cornerRadius=8f; setStroke(1,0xFF16834A.toInt()) }
            elevation=3f
            content()
        }
        parent.addView(box, LinearLayout.LayoutParams(0,-2,weight).apply { setMargins(4,5,4,5) })
    }
    private fun card(parent:LinearLayout, content:LinearLayout.()->Unit) { val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(16,13,16,13); background=android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,intArrayOf(0xFF0B1D13.toInt(),0xFF041009.toInt())).apply { cornerRadius=7f; setStroke(1,0xFF167747.toInt()) }; content() }; parent.addView(box,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,8,0,8)}) }
    private fun field(hint:String,numeric:Boolean,multi:Boolean=false)=EditText(this).apply { this.hint=hint; textSize=14f; setTextColor(INK); setHintTextColor(MUTED); inputType=if(numeric) (InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL) else (InputType.TYPE_CLASS_TEXT or (if(multi) InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0)); if(multi){minLines=3;gravity=Gravity.TOP}; setPadding(14,12,14,12); background=pill(0xFF0B1B12.toInt()); layoutParams=LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,6,0,6)} }
    private fun action(parent:LinearLayout,text:String,onClick:()->Unit){ val btn=Button(this).apply{this.text=text; textSize=13f; setTextColor(0xFF001B0D.toInt()); background=pill(AQUA); setOnClickListener{onClick()} }; parent.addView(btn,LinearLayout.LayoutParams(-1,52).apply{setMargins(0,6,0,6)}) }
    private fun rowCard(parent:LinearLayout,left:String,right:String){ card(parent){ val r=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}; r.addView(label(left,13,INK,true),LinearLayout.LayoutParams(0,-2,1f)); r.addView(label(right,14,AQUA,true)); addView(r) } }
    private fun readArray(key:String)=try{JSONArray(prefs.getString(key,"[]"))}catch(_:Exception){JSONArray()}
    private fun saveArray(key:String,a:JSONArray){prefs.edit().putString(key,a.toString()).apply()}
    private fun totalIncome():Double { val a=readArray("income"); var s=0.0; for(i in 0 until a.length())s+=a.optJSONObject(i)?.optDouble("amount",0.0) ?: 0.0; return s }
    private fun totalExpenses():Double { val a=readArray("expenses"); var s=0.0; for(i in 0 until a.length())s+=a.optJSONObject(i)?.optDouble("amount",0.0) ?: 0.0; return s }
    private fun sumToday(key:String):Double { val a=readArray(key); var s=0.0; for(i in 0 until a.length()){val o=a.optJSONObject(i) ?: continue;if(o.optString("date")==date)s+=o.optDouble("amount",0.0)};return s }
    private fun todayIncome()=sumToday("income")
    private fun todayExpenses()=sumToday("expenses")
    private fun todayNet()=todayIncome()-todayExpenses()
    private fun pendingTodos():JSONArray { val all=readArray("todos"); val out=JSONArray(); for(i in 0 until all.length()){val o=all.optJSONObject(i) ?: continue;if(!o.optBoolean("done"))out.put(o)};return out }
    private fun todayExercise():Pair<Int,Int>{val a=readArray("workouts");var p=0;var q=0;for(i in 0 until a.length()){val o=a.getJSONObject(i);if(o.optString("date")==date){p+=o.optInt("push");q+=o.optInt("pull")}};return p to q}
    private fun allExerciseTotals():Pair<Int,Int>{val a=readArray("workouts");var p=0;var q=0;for(i in 0 until a.length()){p+=a.getJSONObject(i).optInt("push");q+=a.getJSONObject(i).optInt("pull")};return p to q}
    private fun lastSevenDaysNet(): List<Pair<String, Double>> {
        val totals = linkedMapOf<String, Double>(); val keyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US); val labelFormat = SimpleDateFormat("dd/MM", Locale("pt", "BR")); val calendar = Calendar.getInstance(); calendar.add(Calendar.DAY_OF_YEAR, -6)
        repeat(7) { totals[keyFormat.format(calendar.time)] = 0.0; calendar.add(Calendar.DAY_OF_YEAR, 1) }
        listOf("income" to 1.0,"expenses" to -1.0).forEach { (key,sign) -> val entries=readArray(key); for(i in 0 until entries.length()){val item=entries.optJSONObject(i) ?: continue;val d=item.optString("date");if(totals.containsKey(d))totals[d]=(totals[d] ?: 0.0)+sign*item.optDouble("amount")} }
        return totals.map { (key,amount) -> val parsed=try{keyFormat.parse(key)}catch(_:Exception){null}; (if(parsed!=null)labelFormat.format(parsed) else key.takeLast(5)) to amount }
    }
    private fun money(v:Double)=String.format(Locale("pt","BR"),"%,.2f",v)
    private fun toast(s:String){Toast.makeText(this,s,Toast.LENGTH_SHORT).show()}
}

private class ProgressChart(context: android.content.Context, private val points:List<Triple<String,Int,Int>>):View(context){
    private val paint=Paint(3)
    override fun onDraw(canvas:Canvas){ super.onDraw(canvas); val w=width.toFloat(); val h=height.toFloat(); val left=30f; val right=w-12f; val top=20f; val bottom=h-34f; paint.color=0xFF123D29.toInt(); paint.strokeWidth=1f; for(i in 0..3){val y=top+i*(bottom-top)/3f;canvas.drawLine(left,y,right,y,paint)}; paint.textSize=22f; paint.color=0xFF19F58A.toInt(); canvas.drawText("— Flexões",left,14f,paint); paint.color=0xFF70C99A.toInt(); canvas.drawText("— Barras",left+125f,14f,paint); if(points.isEmpty())return; val max=points.maxOf{maxOf(it.second,it.third)}.coerceAtLeast(1).toFloat(); val slot=(right-left)/(points.size.coerceAtLeast(1)); fun drawSeries(value:(Triple<String,Int,Int>)->Int,color:Int){paint.color=color;paint.strokeWidth=5f;paint.style=Paint.Style.STROKE;paint.strokeCap=Paint.Cap.ROUND;val path=Path();points.forEachIndexed{idx,p->val x=left+slot*(idx+.5f);val y=bottom-(value(p)/max)*(bottom-top);if(idx==0)path.moveTo(x,y)else path.lineTo(x,y);paint.style=Paint.Style.FILL;canvas.drawCircle(x,y,5f,paint);paint.style=Paint.Style.STROKE};canvas.drawPath(path,paint);paint.style=Paint.Style.FILL}; drawSeries({it.second},0xFF19F58A.toInt());drawSeries({it.third},0xFF70C99A.toInt());paint.color=0xFF5E9879.toInt();paint.textSize=21f;points.forEachIndexed{idx,p->canvas.drawText(p.first,left+slot*(idx+.5f)-18f,h-6f,paint)} }
}

private class IncomeChart(context: android.content.Context, private val points: List<Pair<String, Double>>) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) { super.onDraw(canvas); val w=width.toFloat(); val h=height.toFloat(); val left=12f; val right=w-8f; val top=18f; val bottom=h-34f; val maxValue=(points.maxOfOrNull{ kotlin.math.abs(it.second) }?.coerceAtLeast(1.0) ?: 1.0).toFloat(); val zero=top+(bottom-top)*maxValue/(2f*maxValue); paint.color=0xFF123D29.toInt();paint.strokeWidth=1f;for(i in 0..3){val y=top+i*(bottom-top)/3f;canvas.drawLine(left,y,right,y,paint)};if(points.isEmpty())return;val slot=(right-left)/points.size.toFloat();val path=Path();points.forEachIndexed{index,point->val x=left+slot*(index+.5f);val y=zero-(point.second/maxValue).toFloat()*(bottom-top)/2f;if(index==0)path.moveTo(x,y)else path.lineTo(x,y);paint.color=if(point.second<0)0xFFCE806D.toInt() else 0xFF19F58A.toInt();paint.style=Paint.Style.FILL;canvas.drawCircle(x,y,4.5f,paint);paint.color=MUTED;paint.textSize=10f;canvas.drawText(point.first,x-paint.measureText(point.first)/2f,h-8f,paint)};paint.color=0xFF19F58A.toInt();paint.strokeWidth=3.5f;paint.style=Paint.Style.STROKE;paint.strokeCap=Paint.Cap.ROUND;canvas.drawPath(path,paint);paint.style=Paint.Style.FILL }
}
