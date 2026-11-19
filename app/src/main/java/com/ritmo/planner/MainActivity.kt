package com.ritmo.planner

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import android.text.InputType
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

private const val BG = 0xFF020805.toInt()
private const val INK = 0xFFD8FFE8.toInt()
private const val MUTED = 0xFF70A98A.toInt()
private const val AQUA = 0xFF00FF88.toInt()
private const val PANEL = 0xFF071A12.toInt()
private const val PANEL_2 = 0xFF0A2117.toInt()
private const val GRID = 0xFF164A30.toInt()
private const val WARNING = 0xFFFFC857.toInt()
private const val DANGER = 0xFFFF6B72.toInt()

private data class LifeAchievement(val id: String, val title: String, val unlocked: Boolean, val description: String)

/**
 * LIFE.EXE mantém o armazenamento e os componentes do projeto Ritmo original.
 * As chaves antigas (income, expenses, todos, workouts e social) são preservadas
 * para que as instalações existentes mantenham seus dados e os widgets continuem funcionando.
 */
class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("ritmo_data", MODE_PRIVATE) }
    private lateinit var root: LinearLayout
    private var page = "home"
    private val date get() = dateOf(Calendar.getInstance())
    private val prettyDate get() = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date())
    private val timerHandler = Handler(Looper.getMainLooper())
    private var focusClock: TextView? = null
    private var focusBar: ProgressBar? = null
    private val focusTick = object : Runnable {
        override fun run() {
            updateFocusDisplay()
            if (page == "focus" && activeFocus()?.optString("status") == "running") timerHandler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        showPage(intent.getStringExtra("open_page") ?: "home")
        checkFocusCompletion()
        syncAchievements()
        generateWeeklyReportIfNeeded()
        if (prefs.getBoolean("notifications_enabled", true)) {
            scheduleAllHabitReminders()
            DeadlineReminderScheduler.syncAll(this)
            if (hasActiveDeadlines()) requestNotificationPermissionIfNeeded()
        }
    }

    override fun onResume() {
        super.onResume()
        checkFocusCompletion()
        // Recalcula e publica os dados atuais; evita o widget ficar preso em R$ 0,00 após atualizar o app.
        refreshWidgets()
        DeadlineReminderScheduler.syncAll(this)
        if (page == "focus") startFocusTicker()
    }

    override fun onPause() {
        timerHandler.removeCallbacks(focusTick)
        super.onPause()
    }

    override fun onDestroy() {
        timerHandler.removeCallbacks(focusTick)
        super.onDestroy()
    }

    private fun showPage(target: String) {
        page = target
        focusClock = null
        focusBar = null
        timerHandler.removeCallbacks(focusTick)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BG) }
        val scroll = ScrollView(this).apply { isFillViewport = false; clipToPadding = false }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 16, 18, 18) }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(5, 7, 5, 7)
            setBackgroundColor(0xFF030D08.toInt())
        }
        addNavItem(nav, "home", "⌂\nINÍCIO")
        addNavItem(nav, "analytics", "⌁\nDADOS")
        val add = Button(this).apply {
            text = "+"
            textSize = 27f
            isAllCaps = false
            setTextColor(0xFF001B0D.toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(AQUA)
                setStroke(2, 0xFF78FFC0.toInt())
            }
            setOnClickListener { quickActions() }
        }
        nav.addView(add, LinearLayout.LayoutParams(52, 48).apply { setMargins(5, 0, 5, 0) })
        addNavItem(nav, "achievements", "◇\nRANK")
        addNavItem(nav, "settings", "⚙\nSISTEMA")
        root.addView(nav)
        setContentView(root)

        when (target) {
            "money" -> moneyPage(body)
            "body" -> bodyPage(body)
            "social" -> socialPage(body)
            "missions" -> missionPage(body)
            "habits" -> habitsPage(body)
            "focus" -> focusPage(body)
            "avoidance" -> avoidancePage(body)
            "goals" -> goalsPage(body)
            "analytics" -> analyticsPage(body)
            "achievements" -> achievementsPage(body)
            "settings" -> settingsPage(body)
            else -> homePage(body)
        }
        if (target == "focus") startFocusTicker()
    }

    private fun addNavItem(nav: LinearLayout, key: String, title: String) {
        val selected = page == key
        val btn = TextView(this).apply {
            text = title
            textSize = 9f
            gravity = Gravity.CENTER
            setLineSpacing(0f, 0.92f)
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(if (selected) AQUA else MUTED)
            setPadding(2, 5, 2, 5)
            setOnClickListener { showPage(key) }
        }
        nav.addView(btn, LinearLayout.LayoutParams(0, 48, 1f))
    }

    private fun quickActions() {
        val labels = arrayOf("Registrar receita ou gasto", "Registrar treino", "Criar missão", "Registrar hábito", "Registrar avanço", "Criar objetivo", "Iniciar sessão de foco", "Protocolo anti-procrastinação")
        val pages = arrayOf("money", "body", "missions", "habits", "social", "goals", "focus", "avoidance")
        AlertDialog.Builder(this).setTitle("＋  AÇÃO RÁPIDA").setItems(labels) { _, which -> showPage(pages[which]) }.setNegativeButton("Fechar", null).show()
    }

    private fun homePage(b: LinearLayout) {
        b.addView(label("◉ SYSTEM ONLINE     LIFE.EXE", 12, AQUA, true))
        b.addView(label("DISCIPLINE PROTOCOL", 25, INK, true))
        b.addView(label("PAINEL DE CONTROLE  /  $prettyDate", 10, MUTED, true))
        val xp = totalXp()
        val level = xpLevel(xp)
        card(b) {
            val line = horizontal()
            line.addView(label("NÍVEL $level", 14, AQUA, true), LinearLayout.LayoutParams(0, -2, 1f))
            line.addView(label("$xp XP", 14, INK, true))
            addView(line)
            val progress = (xp % 500L).toInt() * 100 / 500
            addView(progressBar(progress))
            addView(label("${500 - (xp % 500L)} XP PARA O PRÓXIMO NÍVEL", 10, MUTED))
        }

        val balance = totalIncome() - totalExpenses()
        val today = todayNet()
        val finance = monthlyPatrimonySeries()
        val financeTrend = if (finance.size >= 2) finance.last().second - finance.first().second else 0.0
        val financeRow = horizontal()
        smallStat(financeRow, "SALDO TOTAL", "R$ ${money(balance)}", if (balance >= 0) AQUA else DANGER, 1f) { showPage("money") }
        smallStat(financeRow, "SALDO DE HOJE", "R$ ${money(today)}", if (today >= 0) AQUA else DANGER, 1f) { showPage("money") }
        b.addView(financeRow)
        card(b) {
            addView(label("⌁  PATRIMÔNIO  /  MÊS ATUAL", 11, AQUA, true))
            addView(label("${if (financeTrend >= 0) "▲" else "▼"} ${if (financeTrend >= 0) "EM ALTA" else "EM QUEDA"}  •  R$ ${money(kotlin.math.abs(financeTrend))} no período", 11, if (financeTrend >= 0) AQUA else DANGER, true))
            addView(IncomeChart(this@MainActivity, finance).apply { layoutParams = LinearLayout.LayoutParams(-1, 125) })
            action(this, "ABRIR FINANCEIRO  ↗", AQUA) { showPage("money") }
        }

        val exercises = todayExercise()
        val pushRecord = personalExerciseRecord("push")
        val pullRecord = personalExerciseRecord("pull")
        val pushTrend = exerciseDayTrend("push")
        val pullTrend = exerciseDayTrend("pull")
        card(b) {
            addView(label("▥  PERFORMANCE FÍSICA  /  RECORDES PESSOAIS", 12, AQUA, true))
            val row = horizontal()
            val left = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            left.addView(label("FLEXÕES", 10, MUTED, true))
            left.addView(label("${pushRecord.first}", 29, INK, true))
            left.addView(label("RECORDE  •  ${pushRecord.second.ifBlank { "—" }}", 9, MUTED))
            left.addView(label("${pushTrend.first} ${pushTrend.second}", 11, pushTrend.third, true))
            left.addView(label("Hoje: ${exercises.first}", 10, MUTED))
            val right = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            right.addView(label("BARRAS", 10, MUTED, true))
            right.addView(label("${pullRecord.first}", 29, INK, true))
            right.addView(label("RECORDE  •  ${pullRecord.second.ifBlank { "—" }}", 9, MUTED))
            right.addView(label("${pullTrend.first} ${pullTrend.second}", 11, pullTrend.third, true))
            right.addView(label("Hoje: ${exercises.second}", 10, MUTED))
            row.addView(left, LinearLayout.LayoutParams(0, -2, 1f)); row.addView(right, LinearLayout.LayoutParams(0, -2, 1f)); addView(row)
            action(this, "SUPERAR MEU RECORDE  ↗", AQUA) { showPage("body") }
        }

        val todayMissions = activeMissionsForToday()
        card(b) {
            val row = horizontal()
            row.addView(label("◎  MISSION CONTROL", 12, AQUA, true), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(label("${completedCount(todayMissions)}/${todayMissions.length()}", 12, INK, true))
            addView(row)
            if (todayMissions.length() == 0) addView(label("Nenhuma missão prioritária para hoje. Defina até três ações que realmente importam.", 11, MUTED))
            for (i in 0 until min(3, todayMissions.length())) {
                val m = todayMissions.optJSONObject(i) ?: continue
                addView(label("${if (m.optString("priority") == "CRÍTICA") "▣" else "□"} ${m.optString("title")}  •  ${m.optString("status", "pendente").uppercase(Locale("pt", "BR"))}", 12, INK))
            }
            action(this, "ABRIR MISSION CONTROL  ↗", AQUA) { showPage("missions") }
        }

        val habits = readArray("habits")
        val dueToday = habits.length()
        val doneToday = countHabitsDoneToday()
        card(b) {
            addView(label("◷  HABIT SYSTEM", 12, AQUA, true))
            addView(label("$doneToday / $dueToday hábitos realizados hoje", 17, INK, true))
            addView(progressBar(if (dueToday == 0) 0 else doneToday * 100 / dueToday))
            addView(label(if (dueToday == 0) "Cadastre seus hábitos e defina uma meta mínima para dias difíceis." else "Meta mínima também conta. O importante é manter a retomada.", 11, MUTED))
            action(this, "ABRIR HÁBITOS  ↗", AQUA) { showPage("habits") }
        }

        val focusActive = activeFocus()
        card(b) {
            addView(label("◉  FOCUS MODE", 12, AQUA, true))
            addView(label(if (focusActive == null) "Pronto para executar?" else "Sessão ativa: ${focusActive.optString("task")}", 15, INK, true))
            action(this, if (focusActive == null) "INICIAR FOCO  ↗" else "RETOMAR SESSÃO  ↗", AQUA) { showPage("focus") }
        }

        val goals = readArray("goals")
        card(b) {
            addView(label("⌁  PROJETO LIBERDADE", 12, AQUA, true))
            addView(label("${goals.length()} objetivo(s) em andamento  •  ${readArray("social").length()} avanços", 12, INK))
            val from = max(0, goals.length() - 2)
            for (i in goals.length() - 1 downTo from) {
                val goal = goals.optJSONObject(i) ?: continue
                addView(label("${goal.optString("title")}  •  ${goalProgress(goal)}%", 12, MUTED))
            }
            if (goals.length() == 0) addView(label("Transforme sua visão em objetivos e etapas executáveis.", 11, MUTED))
            action(this, "GERENCIAR OBJETIVOS  ↗", AQUA) { showPage("goals") }
        }

        card(b) {
            addView(label("ANTI-PROCRASTINATION PROTOCOL", 11, WARNING, true))
            addView(label("Identifique a resistência. Defina a primeira ação. Comece por cinco minutos.", 12, INK))
            action(this, "CONFRONTAR UMA TAREFA  ↗", WARNING) { showPage("avoidance") }
        }
    }

    private fun moneyPage(b: LinearLayout) {
        header(b, "01 / FINANCEIRO", "Receitas e gastos")
        val balance = totalIncome() - totalExpenses()
        card(b) {
            addView(label("SALDO ACUMULADO", 11, MUTED, true))
            addView(label("R$ ${money(balance)}", 30, INK, true))
            addView(label("Hoje: + R$ ${money(todayIncome())}  /  − R$ ${money(todayExpenses())}", 11, MUTED))
            val goal = prefs.getString("monthly_income_goal", "")?.toDoubleOrNull()
            if (goal != null && goal > 0) {
                val thisMonth = sumMonthIncome()
                addView(label("META DE RECEITA MENSAL  /  R$ ${money(thisMonth)} DE R$ ${money(goal)}", 10, MUTED, true))
                addView(progressBar((thisMonth * 100 / goal).toInt().coerceIn(0, 100)))
            }
        }
        b.addView(label("NOVA RECEITA", 12, AQUA, true))
        val incomeValue = field("Valor recebido (ex.: 150,00)", true)
        val incomeDesc = field("Origem / descrição", false)
        val incomeDate = datePickerControl("DATA DA ENTRADA", date)
        b.addView(incomeValue); b.addView(incomeDesc); b.addView(incomeDate)
        action(b, "＋  SALVAR RECEITA") {
            val amount = parseAmount(incomeValue.text.toString())
            if (amount == null || amount <= 0) toast("Digite um valor válido") else {
                val selectedDate = incomeDate.tag as? String ?: date
                val a = readArray("income")
                a.put(JSONObject().put("id", newId()).put("date", selectedDate).put("amount", amount).put("desc", incomeDesc.text.toString().ifBlank { "Receita" }))
                saveArray("income", a); refreshWidgets(); showPage("money")
                toast(if (selectedDate == date) "Receita salva" else "Receita histórica salva em $selectedDate")
            }
        }
        b.addView(label("NOVO GASTO", 12, AQUA, true))
        val expenseValue = field("Valor gasto (ex.: 35,90)", true)
        val expenseDesc = field("Com o que você gastou?", false)
        val expenseDate = datePickerControl("DATA DO GASTO", date)
        b.addView(expenseValue); b.addView(expenseDesc); b.addView(expenseDate)
        action(b, "−  REGISTRAR GASTO", WARNING) {
            val amount = parseAmount(expenseValue.text.toString())
            if (amount == null || amount <= 0) toast("Digite um valor válido") else {
                val selectedDate = expenseDate.tag as? String ?: date
                val a = readArray("expenses")
                a.put(JSONObject().put("id", newId()).put("date", selectedDate).put("amount", amount).put("desc", expenseDesc.text.toString().ifBlank { "Gasto" }))
                saveArray("expenses", a); refreshWidgets(); showPage("money")
                toast(if (selectedDate == date) "Gasto registrado e descontado do saldo" else "Gasto histórico salvo em $selectedDate")
            }
        }
        b.addView(label("HISTÓRICO DE RECEITAS", 12, AQUA, true))
        val incomes = readArray("income")
        for (i in incomes.length() - 1 downTo 0) {
            val o = incomes.optJSONObject(i) ?: continue
            editableRecord(b, "＋ ${o.optString("desc")}  •  ${o.optString("date")}", "R$ ${money(transactionAmount(o))}", "Editar receita", { editMoneyRecord("income", i) }, { deleteMoneyRecord("income", i) })
        }
        if (incomes.length() == 0) b.addView(label("Nenhuma receita registrada.", 12, MUTED))
        b.addView(label("HISTÓRICO DE GASTOS", 12, AQUA, true))
        val expenses = readArray("expenses")
        for (i in expenses.length() - 1 downTo 0) {
            val o = expenses.optJSONObject(i) ?: continue
            editableRecord(b, "− ${o.optString("desc")}  •  ${o.optString("date")}", "R$ ${money(transactionAmount(o))}", "Editar gasto", { editMoneyRecord("expenses", i) }, { deleteMoneyRecord("expenses", i) })
        }
        if (expenses.length() == 0) b.addView(label("Nenhum gasto registrado.", 12, MUTED))
    }

    private fun deleteMoneyRecord(key: String, index: Int) {
        AlertDialog.Builder(this).setTitle("Excluir registro?").setMessage("Esta ação não pode ser desfeita.")
            .setNegativeButton("Cancelar", null).setPositiveButton("Excluir") { _, _ ->
                val old = readArray(key); val fresh = JSONArray()
                for (i in 0 until old.length()) if (i != index) old.optJSONObject(i)?.let { fresh.put(it) }
                saveArray(key, fresh); refreshWidgets(); showPage("money")
            }.show()
    }

    private fun todoPage(b: LinearLayout) {
        header(b, "02 / ORGANIZAÇÃO", "Lista de tarefas")
        val tasks = readArray("todos")
        val title = field("O que precisa ser feito?", false)
        val due = datePickerControl("PRAZO (OPCIONAL)", "", optional = true)
        val dueTime = field("Horário (HH:mm) — opcional", false)
        val priority = spinner(arrayOf("Normal", "Alta", "Crítica"))
        val category = spinner(arrayOf("Pessoal", "Negócio", "Corpo", "Conhecimento", "Finanças"))
        b.addView(title); b.addView(due); b.addView(dueTime); b.addView(label("PRIORIDADE", 10, MUTED, true)); b.addView(priority)
        b.addView(label("CATEGORIA", 10, MUTED, true)); b.addView(category)
        action(b, "＋  ADICIONAR TAREFA") {
            val text = title.text.toString().trim()
            val selectedDue = due.tag as? String ?: ""
            if (text.isBlank()) toast("Digite uma tarefa") else if (selectedDue.isNotBlank() && !validDate(selectedDue)) toast("Selecione um prazo válido") else if (selectedDue.isBlank() && dueTime.text.isNotBlank()) toast("Selecione uma data para definir o horário do prazo") else if (dueTime.text.isNotBlank() && !validTime(dueTime.text.toString().trim())) toast("Horário inválido. Use HH:mm") else {
                        tasks.put(JSONObject().put("id", newId()).put("title", text).put("done", false).put("due", selectedDue).put("dueTime", dueTime.text.toString().trim()).put("priority", priority.selectedItem.toString()).put("category", category.selectedItem.toString()).put("createdAt", date).put("overdueNotified", false))
                saveArray("todos", tasks); TodoWidgetProvider.refreshAll(this@MainActivity); showPage("todo"); toast("Tarefa adicionada")
            }
        }
        val pendingCount = (0 until tasks.length()).count { !(tasks.optJSONObject(it)?.optBoolean("done") ?: true) }
        card(b) { addView(label("$pendingCount PENDENTE(S)  •  ${tasks.length() - pendingCount} CONCLUÍDA(S)", 12, AQUA, true)) }
        for (i in tasks.length() - 1 downTo 0) {
            val item = tasks.optJSONObject(i) ?: continue
            val id = item.optString("id")
            val done = item.optBoolean("done")
            val taskDue = item.optString("due")
            val overdue = !done && taskDue.isNotBlank() && taskDue < date
            card(b) {
                val line = horizontal()
                val check = CheckBox(this@MainActivity).apply {
                    isChecked = done
                    if (Build.VERSION.SDK_INT >= 21) buttonTintList = android.content.res.ColorStateList.valueOf(AQUA)
                    setOnCheckedChangeListener { _, checked -> setTodoDone(id, checked) }
                }
                line.addView(check)
                val titleLabel = label(item.optString("title"), 13, if (done) MUTED else INK, true)
                if (done) titleLabel.paintFlags = titleLabel.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                line.addView(titleLabel, LinearLayout.LayoutParams(0, -2, 1f)); addView(line)
                val dueLabel = if (taskDue.isNotBlank()) "  •  $taskDue${if (item.optString("dueTime").isNotBlank()) " ${item.optString("dueTime")}" else ""}" else ""
                val meta = "${item.optString("category", "Pessoal")}  •  ${item.optString("priority", "Normal")}" + dueLabel
                addView(label(if (overdue) "ATRASADA  /  $meta" else meta, 10, if (overdue) DANGER else MUTED))
                val buttons = horizontal()
                smallButton(buttons, "EDITAR", PANEL) { editTodo(id) }
                if (!done) smallButton(buttons, "ADIAR / RESISTÊNCIA", 0xFF302612.toInt()) { logAvoidanceDialog(item.optString("title"), id) }
                smallButton(buttons, "EXCLUIR", 0xFF301216.toInt()) { deleteTodo(id) }
                addView(buttons)
            }
        }
        if (tasks.length() == 0) b.addView(label("Sua lista está vazia. Adicione a primeira tarefa.", 12, MUTED))
        b.addView(label("O widget da tela inicial exibe suas missões ativas.", 10, MUTED))
    }

    private fun editTodo(id: String) {
        val item = findById(tasks, id) ?: return
        val title = field("Tarefa", false).apply { setText(item.optString("title")) }
        val due = datePickerControl("PRAZO (OPCIONAL)", item.optString("due"), optional = true)
        val dueTime = field("Horário (HH:mm)", false).apply { setText(item.optString("dueTime")) }
        val priorityItems = arrayOf("Normal", "Alta", "Crítica")
        val categoryItems = arrayOf("Pessoal", "Negócio", "Corpo", "Conhecimento", "Finanças")
        val priority = spinner(priorityItems).apply { setSelection(priorityItems.indexOf(item.optString("priority", "Normal")).coerceAtLeast(0)) }
        val category = spinner(categoryItems).apply { setSelection(categoryItems.indexOf(item.optString("category", "Pessoal")).coerceAtLeast(0)) }
        val box = verticalBox(title, due, dueTime, label("PRIORIDADE", 10, MUTED, true), priority,
            label("CATEGORIA", 10, MUTED, true), category)
        AlertDialog.Builder(this).setTitle("Editar tarefa").setView(box).setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar") { _, _ ->
                if (title.text.isNullOrBlank()) { toast("Informe o título"); return@setPositiveButton }
                val selectedDue = due.tag as? String ?: ""
                if (selectedDue.isNotBlank() && !validDate(selectedDue)) { toast("Selecione um prazo válido"); return@setPositiveButton }
                if (selectedDue.isBlank() && dueTime.text.isNotBlank()) { toast("Selecione uma data para definir o horário do prazo"); return@setPositiveButton }
                if (dueTime.text.isNotBlank() && !validTime(dueTime.text.toString().trim())) { toast("Horário inválido"); return@setPositiveButton }
                val previousDue = item.optString("due")
                val previousTime = item.optString("dueTime")
                val selectedTime = dueTime.text.toString().trim()
                item.put("title", title.text.toString().trim()).put("due", selectedDue).put("dueTime", selectedTime)
                    .put("priority", priority.selectedItem.toString()).put("category", category.selectedItem.toString())
                if (previousDue != selectedDue || previousTime != selectedTime) item.put("overdueNotified", false)
                saveArray("todos", tasks); TodoWidgetProvider.refreshAll(this); showPage("todo")
            }.show()
    }

    private fun setTodoDone(id: String, done: Boolean) {
        for (i in 0 until tasks.length()) {
            val item = tasks.optJSONObject(i) ?: continue
            if (item.optString("id") == id) {
                val wasDone = item.optBoolean("done")
                item.put("done", done)
                if (done && !wasDone) awardXp(10, "Tarefa concluída")
            }
        }
        saveArray("todos", tasks); TodoWidgetProvider.refreshAll(this); syncAchievements(); showPage("todo")
    }

    private fun deleteTodo(id: String) {
        AlertDialog.Builder(this).setTitle("Excluir tarefa?").setNegativeButton("Cancelar", null)
            .setPositiveButton("Excluir") { _, _ ->
                val old = readArray("todos"); val fresh = JSONArray()
                for (i in 0 until old.length()) { val item = old.optJSONObject(i) ?: continue; if (item.optString("id") != id) fresh.put(item) }
                saveArray("todos", fresh); TodoWidgetProvider.refreshAll(this); showPage("todo")
            }.show()
    }

    private fun bodyPage(b: LinearLayout) {
        header(b, "03 / PERFORMANCE", "Treino físico")
        val today = todayExercise()
        val pushRecord = personalExerciseRecord("push")
        val pullRecord = personalExerciseRecord("pull")
        card(b) {
            addView(label("SEUS RECORDES PESSOAIS  /  SUPERE-SE", 11, AQUA, true))
            val row = horizontal()
            val pushBox = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            pushBox.addView(label("FLEXÕES", 10, MUTED, true)); pushBox.addView(label("${pushRecord.first}", 32, INK, true)); pushBox.addView(label(if (pushRecord.second.isBlank()) "Ainda sem recorde" else "Desde ${pushRecord.second}", 10, MUTED))
            val pullBox = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            pullBox.addView(label("BARRAS", 10, MUTED, true)); pullBox.addView(label("${pullRecord.first}", 32, INK, true)); pullBox.addView(label(if (pullRecord.second.isBlank()) "Ainda sem recorde" else "Desde ${pullRecord.second}", 10, MUTED))
            row.addView(pushBox, LinearLayout.LayoutParams(0, -2, 1f)); row.addView(pullBox, LinearLayout.LayoutParams(0, -2, 1f)); addView(row)
            addView(label("Hoje: ${today.first} flexões  •  ${today.second} barras. Seu próximo treino pode bater esses números.", 11, MUTED))
        }
        val push = field("Quantidade de flexões", true)
        val pull = field("Quantidade de barras", true)
        val note = field("Observação (opcional)", false)
        val workoutDate = datePickerControl("DATA DO TREINO", date)
        b.addView(push); b.addView(pull); b.addView(note); b.addView(workoutDate)
        action(b, "＋  SALVAR TREINO") {
            val p = push.text.toString().toIntOrNull() ?: 0
            val q = pull.text.toString().toIntOrNull() ?: 0
            val selectedDate = workoutDate.tag as? String ?: date
            if (p < 0 || q < 0 || (p == 0 && q == 0) || !validDate(selectedDate)) toast("Informe quantidades e uma data válida") else {
                val a = readArray("workouts")
                a.put(JSONObject().put("id", newId()).put("date", selectedDate).put("push", p).put("pull", q).put("note", note.text.toString()))
                saveArray("workouts", a); awardXp(10, "Treino registrado"); syncAchievements(); showPage("body"); toast("Treino salvo")
            }
        }
        b.addView(label("EVOLUÇÃO — ÚLTIMOS REGISTROS", 12, AQUA, true))
        val arr = readArray("workouts")
        val recent = mutableListOf<Triple<String, Int, Int>>()
        for (i in max(0, arr.length() - 7) until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            recent.add(Triple(o.optString("date").takeLast(5), o.optInt("push"), o.optInt("pull")))
        }
        if (recent.isEmpty()) b.addView(label("O gráfico aparecerá após o primeiro treino.", 11, MUTED))
        else b.addView(ProgressChart(this, recent).apply { layoutParams = LinearLayout.LayoutParams(-1, 190).apply { setMargins(0, 8, 0, 12) } })
        for (i in arr.length() - 1 downTo 0) {
            val o = arr.optJSONObject(i) ?: continue
            editableRecord(b, o.optString("date"), "${o.optInt("push")} flexões  •  ${o.optInt("pull")} barras", "Editar treino", { editWorkoutRecord(i) }, { deleteWorkoutRecord(i) })
        }
    }

    private fun deleteWorkoutRecord(index: Int) {
        AlertDialog.Builder(this).setTitle("Excluir treino?").setNegativeButton("Cancelar", null).setPositiveButton("Excluir") { _, _ ->
            val old = readArray("workouts"); val fresh = JSONArray()
            for (i in 0 until old.length()) if (i != index) old.optJSONObject(i)?.let { fresh.put(it) }
            saveArray("workouts", fresh); showPage("body")
        }.show()
    }

    private fun socialPage(b: LinearLayout) {
        header(b, "04 / PROJETO LIBERDADE", "Diário de evolução")
        card(b) {
            addView(label("REGISTRE O QUE SAIU DO PAPEL", 12, AQUA, true))
            addView(label("Entregas, contatos, reuniões, resultados e próximos passos.", 12, MUTED))
            action(this, "GERENCIAR OBJETIVOS E ETAPAS  ↗", AQUA) { showPage("goals") }
        }
        val title = field("Título do avanço", false)
        val detail = field("O que aconteceu? Qual o próximo passo?", false, true)
        val socialDate = datePickerControl("DATA DO AVANÇO", date)
        b.addView(title); b.addView(detail); b.addView(socialDate)
        action(b, "＋  REGISTRAR AVANÇO") {
            val selectedDate = socialDate.tag as? String ?: date
            if (title.text.isNullOrBlank() || detail.text.isNullOrBlank() || !validDate(selectedDate)) toast("Preencha título, descrição e data") else {
                val a = readArray("social")
                a.put(JSONObject().put("id", newId()).put("date", selectedDate).put("title", title.text.toString().trim()).put("detail", detail.text.toString().trim()))
                saveArray("social", a); awardXp(10, "Avanço registrado"); showPage("social"); toast("Avanço registrado")
            }
        }
        b.addView(label("LINHA DO TEMPO", 12, AQUA, true))
        val arr = readArray("social")
        for (i in arr.length() - 1 downTo 0) {
            val o = arr.optJSONObject(i) ?: continue
            card(b) {
                addView(label(o.optString("date"), 10, MUTED, true))
                addView(label(o.optString("title"), 16, INK, true))
                addView(label(o.optString("detail"), 12, MUTED))
                val buttons = horizontal()
                smallButton(buttons, "EDITAR", PANEL) { editSocialRecord(i) }
                smallButton(buttons, "EXCLUIR", 0xFF301216.toInt()) { deleteSocialRecord(i) }
                addView(buttons)
            }
        }
        if (arr.length() == 0) b.addView(label("Nenhum avanço registrado. Comece pelo primeiro.", 12, MUTED))
    }

    private fun deleteSocialRecord(index: Int) {
        AlertDialog.Builder(this).setTitle("Excluir avanço?").setNegativeButton("Cancelar", null).setPositiveButton("Excluir") { _, _ ->
            val old = readArray("social"); val fresh = JSONArray()
            for (i in 0 until old.length()) if (i != index) old.optJSONObject(i)?.let { fresh.put(it) }
            saveArray("social", fresh); showPage("social")
        }.show()
    }

    // ---------------------------------------------------------------------
    // MISSION CONTROL
    // ---------------------------------------------------------------------
    private fun missionPage(b: LinearLayout) {
        header(b, "05 / MISSION CONTROL", "Execução diária")
        val activeToday = activeMissionsForToday()
        card(b) {
            addView(label("MISSÕES PRIORITÁRIAS DE HOJE", 11, AQUA, true))
            addView(label("${completedCount(activeToday)} de ${activeToday.length()} concluídas", 20, INK, true))
            addView(label("Limite recomendado: três missões realmente importantes por dia.", 10, MUTED))
        }
        val title = field("Nome da missão", false)
        val desc = field("Descrição / resultado esperado", false, true)
        val category = spinner(arrayOf("Negócio", "Corpo", "Conhecimento", "Finanças", "Pessoal"))
        val priority = spinner(arrayOf("CRÍTICA", "ALTA", "NORMAL", "BAIXA"))
        val difficulty = spinner(arrayOf("Fácil", "Média", "Difícil"))
        val due = datePickerControl("PRAZO DA MISSÃO", date)
        val dueTime = field("Horário (HH:mm) — opcional", false)
        b.addView(label("NOVA MISSÃO", 12, AQUA, true)); b.addView(title); b.addView(desc)
        addFormChoice(b, "CATEGORIA", category); addFormChoice(b, "PRIORIDADE", priority); addFormChoice(b, "DIFICULDADE", difficulty); b.addView(due); b.addView(dueTime)
        action(b, "＋  CRIAR MISSÃO") {
            if (title.text.isNullOrBlank()) { toast("Informe o título da missão"); return@action }
            val selectedDue = due.tag as? String ?: date
            if (!validDate(selectedDue)) { toast("Selecione um prazo válido"); return@action }
            if (dueTime.text.isNotBlank() && !validTime(dueTime.text.toString().trim())) { toast("Horário inválido. Use HH:mm"); return@action }
            val activePriority = countPriorityMissionsToday()
            if (priority.selectedItem.toString() in listOf("CRÍTICA", "ALTA") && activePriority >= 3) {
                toast("Você já tem três missões prioritárias. Conclua ou rebaixe uma delas antes de adicionar outra.")
                return@action
            }
            val missions = readArray("missions")
            missions.put(JSONObject().put("id", newId()).put("title", title.text.toString().trim()).put("description", desc.text.toString().trim())
                .put("category", category.selectedItem.toString()).put("priority", priority.selectedItem.toString()).put("difficulty", difficulty.selectedItem.toString())
                .put("due", selectedDue).put("dueTime", dueTime.text.toString().trim()).put("status", "pendente").put("createdAt", date).put("deferCount", 0).put("overdueNotified", false))
            saveArray("missions", missions); showPage("missions"); toast("Missão criada")
        }
        b.addView(label("MISSÕES ATIVAS E HISTÓRICO", 12, AQUA, true))
        val all = readArray("missions")
        for (i in all.length() - 1 downTo 0) {
            val m = all.optJSONObject(i) ?: continue
            val status = m.optString("status", "pendente")
            card(b) {
                val criticalColor = if (m.optString("priority") == "CRÍTICA") WARNING else AQUA
                addView(label("${m.optString("priority").uppercase(Locale("pt", "BR"))}  /  ${m.optString("category").uppercase(Locale("pt", "BR"))}", 10, criticalColor, true))
                addView(label(m.optString("title"), 16, INK, true))
                if (m.optString("description").isNotBlank()) addView(label(m.optString("description"), 11, MUTED))
                addView(label("${m.optString("due")}${if (m.optString("dueTime").isNotBlank()) " ${m.optString("dueTime")}" else ""}  •  ${m.optString("difficulty")}  •  ${status.uppercase(Locale("pt", "BR"))}", 10, if (status == "concluída") AQUA else MUTED))
                if (m.optInt("deferCount") > 0) addView(label("Adiada ${m.optInt("deferCount")} vez(es)", 10, WARNING))
                val buttons = horizontal()
                if (status != "concluída" && status != "cancelada") {
                    smallButton(buttons, "CONCLUIR", AQUA) { completeMission(m.optString("id")) }
                    smallButton(buttons, "REPROGRAMAR", 0xFF302612.toInt()) { deferMission(m.optString("id")) }
                    smallButton(buttons, "INICIAR", PANEL) { startMission(m.optString("id")) }
                    smallButton(buttons, "EDITAR", PANEL) { editMission(m.optString("id")) }
                    smallButton(buttons, "ABANDONAR", 0xFF301216.toInt()) { abandonMission(m.optString("id")) }
                }
                addView(buttons)
            }
        }
        if (all.length() == 0) b.addView(label("Nenhuma missão cadastrada. Escolha até três prioridades para hoje.", 12, MUTED))
    }

    private fun startMission(id: String) {
        val arr = readArray("missions"); val m = findById(arr, id) ?: return
        m.put("status", "em execução").put("startedAt", date)
        saveArray("missions", arr)
        if (m.optString("difficulty") == "Difícil" && !m.optBoolean("startedXp", false)) {
            m.put("startedXp", true); saveArray("missions", arr); awardXp(30, "Início de missão difícil")
        }
        showPage("missions")
    }

    private fun completeMission(id: String) {
        val arr = readArray("missions"); val m = findById(arr, id) ?: return
        if (m.optString("status") == "concluída") return
        m.put("status", "concluída").put("completedAt", date)
        saveArray("missions", arr)
        awardXp(if (m.optString("priority") == "CRÍTICA") 100 else 50, "Missão concluída: ${m.optString("title")}")
        syncAchievements(); showPage("missions"); toast("Missão concluída. Execução registrada.")
    }

    private fun deferMission(id: String) {
        val mission = findById(readArray("missions"), id) ?: return
        val nextDue = datePickerControl("NOVO PRAZO", dateAfterDays(1))
        val reason = spinner(avoidanceReasons)
        val rating = RatingBar(this).apply { numStars = 10; stepSize = 1f; rating = 5f; progressTintList = android.content.res.ColorStateList.valueOf(WARNING) }
        val box = verticalBox(
            label("MISSÃO: ${mission.optString("title")}", 12, INK, true),
            label("NOVO PRAZO", 10, MUTED, true), nextDue,
            label("MOTIVO DO ADIAMENTO", 10, MUTED, true), reason,
            label("INTENSIDADE DA RESISTÊNCIA  /  1 A 10", 10, MUTED, true), rating
        )
        AlertDialog.Builder(this).setTitle("Reprogramar missão")
            .setMessage("O adiamento será registrado no histórico. Escolha um prazo concreto para a retomada.")
            .setView(box).setNegativeButton("Voltar", null)
            .setPositiveButton("REGISTRAR E REPROGRAMAR") { _, _ ->
                val newDate = nextDue.tag as? String ?: ""
                if (!validDate(newDate) || newDate < date) { toast("Informe um prazo válido a partir de hoje"); return@setPositiveButton }
                saveAvoidance(mission.optString("title"), reason.selectedItem.toString(), rating.rating.toInt().coerceIn(1, 10), id)
                val latest = readArray("missions")
                findById(latest, id)?.apply {
                    if (!has("originalDue")) put("originalDue", optString("due"))
                    put("due", newDate).put("rescheduledAt", date).put("status", "adiada").put("overdueNotified", false)
                }
                saveArray("missions", latest)
                showPage("missions"); toast("Missão reprogramada para $newDate")
            }.show()
    }

    private fun editMission(id: String) {
        val missions = readArray("missions")
        val mission = findById(missions, id) ?: return
        val title = field("Nome da missão", false).apply { setText(mission.optString("title")) }
        val description = field("Descrição / resultado esperado", false, true).apply { setText(mission.optString("description")) }
        val categoryItems = arrayOf("Negócio", "Corpo", "Conhecimento", "Finanças", "Pessoal")
        val priorityItems = arrayOf("CRÍTICA", "ALTA", "NORMAL", "BAIXA")
        val difficultyItems = arrayOf("Fácil", "Média", "Difícil")
        val category = spinner(categoryItems).apply { setSelection(categoryItems.indexOf(mission.optString("category")).coerceAtLeast(0)) }
        val priority = spinner(priorityItems).apply { setSelection(priorityItems.indexOf(mission.optString("priority")).coerceAtLeast(0)) }
        val difficulty = spinner(difficultyItems).apply { setSelection(difficultyItems.indexOf(mission.optString("difficulty")).coerceAtLeast(0)) }
        val due = datePickerControl("PRAZO DA MISSÃO", mission.optString("due", date))
        val dueTime = field("Horário (HH:mm) — opcional", false).apply { setText(mission.optString("dueTime")) }
        val box = verticalBox(title, description, label("CATEGORIA", 10, MUTED, true), category,
            label("PRIORIDADE", 10, MUTED, true), priority, label("DIFICULDADE", 10, MUTED, true), difficulty, due, dueTime)
        AlertDialog.Builder(this).setTitle("Editar missão").setView(box).setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar alterações") { _, _ ->
                val newDate = due.tag as? String ?: ""; val newTime = dueTime.text.toString().trim()
                if (title.text.isNullOrBlank()) { toast("Informe o título da missão"); return@setPositiveButton }
                if (newDate.isBlank() || !validDate(newDate)) { toast("Selecione um prazo válido"); return@setPositiveButton }
                if (newTime.isNotBlank() && !validTime(newTime)) { toast("Horário inválido"); return@setPositiveButton }
                if (priority.selectedItem.toString() in listOf("CRÍTICA", "ALTA") &&
                    (mission.optString("priority") !in listOf("CRÍTICA", "ALTA") || mission.optString("due") != newDate) &&
                    (newDate == date || mission.optString("createdAt") == date) && countPriorityMissionsToday(id) >= 3) {
                    toast("O limite de três missões prioritárias para hoje já foi atingido"); return@setPositiveButton
                }
                val previousDue = mission.optString("due")
                val previousTime = mission.optString("dueTime")
                mission.put("title", title.text.toString().trim()).put("description", description.text.toString().trim())
                    .put("category", category.selectedItem.toString()).put("priority", priority.selectedItem.toString())
                    .put("difficulty", difficulty.selectedItem.toString()).put("due", newDate).put("dueTime", newTime)
                if (previousDue != newDate || previousTime != newTime) mission.put("overdueNotified", false)
                saveArray("missions", missions); showPage("missions")
            }.show()
    }

    private fun abandonMission(id: String) {
        val arr = readArray("missions"); val m = findById(arr, id) ?: return
        val reason = field("Justificativa (opcional)", false, true)
        AlertDialog.Builder(this).setTitle("Abandonar missão?").setMessage("Uma missão sem justificativa reduz 20 XP.").setView(reason)
            .setNegativeButton("Voltar", null).setPositiveButton("Confirmar") { _, _ ->
                m.put("status", "cancelada").put("cancelReason", reason.text.toString().trim()).put("cancelledAt", date)
                saveArray("missions", arr)
                if (reason.text.isNullOrBlank()) awardXp(-20, "Missão abandonada sem justificativa")
                showPage("missions")
            }.show()
    }

    private fun countPriorityMissionsToday(exceptId: String? = null): Int {
        val a = readArray("missions"); var count = 0
        for (i in 0 until a.length()) {
            val m = a.optJSONObject(i) ?: continue
            if (m.optString("id") == exceptId) continue
            val isForToday = m.optString("due", m.optString("createdAt")) == date
            if (isForToday && m.optString("priority") in listOf("CRÍTICA", "ALTA") && m.optString("status") !in listOf("concluída", "cancelada")) count++
        }
        return count
    }

    private fun activeMissionsForToday(): JSONArray {
        val all = readArray("missions"); val out = JSONArray()
        for (i in 0 until all.length()) {
            val m = all.optJSONObject(i) ?: continue
            if (m.optString("due", m.optString("createdAt")) == date && m.optString("status") !in listOf("cancelada", "concluída")) out.put(m)
        }
        return out
    }

    private fun completedCount(missions: JSONArray): Int = (0 until missions.length()).count {
        missions.optJSONObject(it)?.optString("status") == "concluída"
    }

    // ---------------------------------------------------------------------
    // HABIT SYSTEM
    // ---------------------------------------------------------------------
    private fun habitsPage(b: LinearLayout) {
        header(b, "06 / HABIT SYSTEM", "Consistência diária")
        val all = readArray("habits")
        val done = countHabitsDoneToday()
        card(b) {
            addView(label("EXECUÇÃO DE HOJE", 11, AQUA, true))
            addView(label("$done / ${all.length()} hábitos", 22, INK, true))
            addView(progressBar(if (all.length() == 0) 0 else done * 100 / all.length()))
            addView(label("A meta mínima mantém a continuidade sem transformar um dia ruim em desistência.", 10, MUTED))
        }
        val name = field("Nome do hábito", false)
        val category = spinner(arrayOf("Corpo", "Conhecimento", "Negócio", "Finanças", "Pessoal"))
        val frequency = spinner(arrayOf("Diário", "Dias úteis", "Semanal"))
        val weekdayNames = arrayOf("Segunda-feira", "Terça-feira", "Quarta-feira", "Quinta-feira", "Sexta-feira", "Sábado", "Domingo")
        val weekdayValues = intArrayOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY)
        val weeklyDay = spinner(weekdayNames)
        val normal = field("Meta normal (número)", true)
        val minimum = field("Meta mínima (número)", true)
        val unit = field("Unidade (ex.: flexões, minutos, páginas)", false)
        val reminder = field("Lembrete local (HH:mm) — opcional", false)
        b.addView(label("NOVO HÁBITO", 12, AQUA, true)); b.addView(name)
        addFormChoice(b, "CATEGORIA", category); addFormChoice(b, "FREQUÊNCIA", frequency)
        addFormChoice(b, "DIA DO LEMBRETE (QUANDO SEMANAL)", weeklyDay)
        b.addView(normal); b.addView(minimum); b.addView(unit); b.addView(reminder)
        action(b, "＋  CADASTRAR HÁBITO") {
            val normalValue = normal.text.toString().toDoubleOrNull()
            val minValue = minimum.text.toString().toDoubleOrNull()
            val reminderValue = reminder.text.toString().trim()
            if (name.text.isNullOrBlank() || normalValue == null || minValue == null || normalValue <= 0 || minValue < 0 || minValue > normalValue) {
                toast("Confira nome, meta normal e meta mínima"); return@action
            }
            if (reminderValue.isNotBlank() && !validTime(reminderValue)) { toast("Use o formato de horário HH:mm"); return@action }
            val habits = readArray("habits")
            val item = JSONObject().put("id", newId()).put("name", name.text.toString().trim()).put("category", category.selectedItem.toString())
                .put("frequency", frequency.selectedItem.toString()).put("weeklyDay", weekdayValues[weeklyDay.selectedItemPosition]).put("normal", normalValue).put("minimum", minValue)
                .put("unit", unit.text.toString().ifBlank { "unidades" }).put("reminder", reminderValue).put("createdAt", date).put("logs", JSONArray())
            habits.put(item); saveArray("habits", habits)
            if (reminderValue.isNotBlank()) {
                setHabitReminder(item)
                requestNotificationPermissionIfNeeded()
            }
            showPage("habits"); toast("Hábito cadastrado")
        }

        b.addView(label("HÁBITOS ATIVOS", 12, AQUA, true))
        for (i in all.length() - 1 downTo 0) {
            val habit = all.optJSONObject(i) ?: continue
            val logs = habit.optJSONArray("logs") ?: JSONArray()
            val todayLog = findLogForDate(logs, date)
            val streak = habitStreak(habit)
            val weekly = habitWeeklyConsistency(habit)
            card(b) {
                addView(label("${habit.optString("category").uppercase(Locale("pt", "BR"))}  /  ${habit.optString("frequency")}", 10, AQUA, true))
                addView(label(habit.optString("name"), 16, INK, true))
                addView(label("Meta: ${habit.optString("normal")} ${habit.optString("unit")}  •  Mínima: ${habit.optString("minimum")} ${habit.optString("unit")}", 10, MUTED))
                addView(label("Sequência: $streak dia(s)  •  melhor: ${bestHabitStreak(habit)}  •  7d: $weekly%", 10, MUTED))
                if (habit.optString("reminder").isNotBlank()) {
                    val dayName = weekdayNamesFromValue(habit.optInt("weeklyDay", Calendar.MONDAY))
                    addView(label("LEMBRETE: ${habit.optString("reminder")}${if (habit.optString("frequency") == "Semanal") " • $dayName" else ""}", 10, MUTED))
                }
                if (logs.length() == 0 || latestHabitLog(habit) < dateDaysAgo(7)) addView(label("SEM EXECUÇÃO RECENTE / RETOMADA RECOMENDADA", 10, WARNING, true))
                if (todayLog != null) addView(label("HOJE: ${todayLog.optString("kind").uppercase(Locale("pt", "BR"))}  /  ${todayLog.optString("amount")} ${habit.optString("unit")}", 11, AQUA, true))
                else addView(label("AINDA NÃO REGISTRADO HOJE", 10, WARNING, true))
                val buttons = horizontal()
                smallButton(buttons, "META NORMAL", AQUA) { logHabit(habit.optString("id"), false) }
                smallButton(buttons, "META MÍNIMA", PANEL) { logHabit(habit.optString("id"), true) }
                smallButton(buttons, "OUTRA DATA", PANEL) { logHabitOnOtherDate(habit.optString("id")) }
                smallButton(buttons, "EDITAR", PANEL) { editHabit(habit.optString("id")) }
                smallButton(buttons, "EXCLUIR", 0xFF301216.toInt()) { deleteHabit(habit.optString("id")) }
                addView(buttons)
            }
        }
        if (all.length() == 0) b.addView(label("Nenhum hábito cadastrado ainda.", 12, MUTED))
        b.addView(label("CALENDÁRIO / ÚLTIMOS 28 DIAS", 12, AQUA, true))
        if (all.length() > 0) habitCalendar(b, all)
    }

    private fun logHabit(id: String, minimum: Boolean, selectedDate: String = date) {
        val habits = readArray("habits"); val habit = findById(habits, id) ?: return
        if (!validDate(selectedDate) || selectedDate > date) { toast("Escolha hoje ou uma data anterior"); return }
        val logs = habit.optJSONArray("logs") ?: JSONArray()
        val amount = if (minimum) habit.optDouble("minimum") else habit.optDouble("normal")
        val existing = findLogForDate(logs, selectedDate)
        val entry = JSONObject().put("date", selectedDate).put("amount", amount).put("kind", if (minimum) "mínima" else "normal")
        if (existing != null) {
            val fresh = JSONArray(); for (i in 0 until logs.length()) { val item = logs.optJSONObject(i) ?: continue; if (item.optString("date") == selectedDate) fresh.put(entry) else fresh.put(item) }
            habit.put("logs", fresh)
        } else { logs.put(entry); habit.put("logs", logs) }
        saveArray("habits", habits)
        awardXp(if (minimum) 15 else 25, "Hábito ${if (minimum) "mínimo" else "normal"}: ${habit.optString("name")}")
        syncAchievements(); showPage("habits")
    }

    private fun logHabitOnOtherDate(id: String) {
        val habit = findById(readArray("habits"), id) ?: return
        val selectedDate = datePickerControl("DATA DO REGISTRO", date)
        val kind = spinner(arrayOf("Meta normal", "Meta mínima"))
        val box = verticalBox(
            label("HÁBITO: ${habit.optString("name")}", 11, AQUA, true),
            selectedDate,
            label("TIPO DE REGISTRO", 10, MUTED, true), kind
        )
        AlertDialog.Builder(this).setTitle("Registrar em outra data").setView(box)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("REGISTRAR") { _, _ ->
                val value = selectedDate.tag as? String ?: ""
                if (!validDate(value) || value > date) { toast("Escolha hoje ou uma data anterior"); return@setPositiveButton }
                logHabit(id, kind.selectedItemPosition == 1, value)
            }.show()
    }

    private fun editHabit(id: String) {
        val arr = readArray("habits"); val h = findById(arr, id) ?: return
        val name = field("Nome", false).apply { setText(h.optString("name")) }
        val categoryItems = arrayOf("Corpo", "Conhecimento", "Negócio", "Finanças", "Pessoal")
        val frequencyItems = arrayOf("Diário", "Dias úteis", "Semanal")
        val weekdayNames = arrayOf("Segunda-feira", "Terça-feira", "Quarta-feira", "Quinta-feira", "Sexta-feira", "Sábado", "Domingo")
        val weekdayValues = intArrayOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY)
        val category = spinner(categoryItems).apply { setSelection(categoryItems.indexOf(h.optString("category")).coerceAtLeast(0)) }
        val frequency = spinner(frequencyItems).apply { setSelection(frequencyItems.indexOf(h.optString("frequency", "Diário")).coerceAtLeast(0)) }
        val weeklyDay = spinner(weekdayNames).apply { setSelection(weekdayValues.indexOf(h.optInt("weeklyDay", Calendar.MONDAY)).coerceAtLeast(0)) }
        val normal = field("Meta normal", true).apply { setText(h.optString("normal")) }
        val minValue = field("Meta mínima", true).apply { setText(h.optString("minimum")) }
        val unit = field("Unidade", false).apply { setText(h.optString("unit")) }
        val reminder = field("Lembrete HH:mm (vazio remove)", false).apply { setText(h.optString("reminder")) }
        val box = verticalBox(name, label("CATEGORIA", 10, MUTED, true), category,
            label("FREQUÊNCIA", 10, MUTED, true), frequency, label("DIA SEMANAL", 10, MUTED, true), weeklyDay,
            normal, minValue, unit, reminder)
        AlertDialog.Builder(this).setTitle("Editar hábito").setView(box).setNegativeButton("Cancelar", null).setPositiveButton("Salvar") { _, _ ->
            val n = normal.text.toString().toDoubleOrNull(); val m = minValue.text.toString().toDoubleOrNull(); val time = reminder.text.toString().trim()
            if (name.text.isNullOrBlank() || n == null || m == null || n <= 0 || m < 0 || m > n || (time.isNotBlank() && !validTime(time))) { toast("Confira os dados do hábito"); return@setPositiveButton }
            h.put("name", name.text.toString().trim()).put("category", category.selectedItem.toString())
                .put("frequency", frequency.selectedItem.toString()).put("weeklyDay", weekdayValues[weeklyDay.selectedItemPosition])
                .put("normal", n).put("minimum", m).put("unit", unit.text.toString()).put("reminder", time)
            saveArray("habits", arr)
            if (time.isBlank()) cancelHabitReminder(id) else { setHabitReminder(h); requestNotificationPermissionIfNeeded() }
            showPage("habits")
        }.show()
    }

    private fun deleteHabit(id: String) {
        AlertDialog.Builder(this).setTitle("Excluir hábito?").setNegativeButton("Cancelar", null).setPositiveButton("Excluir") { _, _ ->
            val old = readArray("habits"); val fresh = JSONArray()
            for (i in 0 until old.length()) { val h = old.optJSONObject(i) ?: continue; if (h.optString("id") != id) fresh.put(h) }
            saveArray("habits", fresh); cancelHabitReminder(id); showPage("habits")
        }.show()
    }

    private fun habitCalendar(parent: LinearLayout, habits: JSONArray) {
        val dates = mutableMapOf<String, Int>()
        for (i in 0 until habits.length()) {
            val h = habits.optJSONObject(i) ?: continue; val logs = h.optJSONArray("logs") ?: JSONArray()
            for (j in 0 until logs.length()) { val item = logs.optJSONObject(j) ?: continue; dates[item.optString("date")] = (dates[item.optString("date")] ?: 0) + 1 }
        }
        val cal = Calendar.getInstance(); cal.add(Calendar.DAY_OF_YEAR, -27)
        repeat(4) {
            val row = horizontal()
            repeat(7) {
                val key = dateOf(cal)
                val count = dates[key] ?: 0
                val day = TextView(this).apply {
                    text = "${cal.get(Calendar.DAY_OF_MONTH)}\n${if (count > 0) "●" else "·"}"
                    gravity = Gravity.CENTER
                    textSize = 10f
                    typeface = android.graphics.Typeface.MONOSPACE
                    setTextColor(if (count > 0) AQUA else MUTED)
                    setPadding(2, 8, 2, 8)
                    background = rounded(if (count > 0) 0xFF0C3320.toInt() else PANEL, GRID, 8f)
                }
                row.addView(day, LinearLayout.LayoutParams(0, 46, 1f).apply { setMargins(2, 2, 2, 2) })
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            parent.addView(row)
        }
        parent.addView(label("Cada ponto representa pelo menos um hábito realizado naquele dia.", 10, MUTED))
    }

    private fun habitStreak(habit: JSONObject): Int {
        val logs = habit.optJSONArray("logs") ?: JSONArray(); val done = HashSet<String>()
        for (i in 0 until logs.length()) logs.optJSONObject(i)?.let { done.add(it.optString("date")) }
        val cal = Calendar.getInstance(); if (!done.contains(date)) cal.add(Calendar.DAY_OF_YEAR, -1)
        var count = 0
        val frequency = habit.optString("frequency", "Diário")
        if (frequency == "Semanal") {
            repeat(52) {
                val weekEnd = cal.clone() as Calendar
                weekEnd.add(Calendar.DAY_OF_YEAR, -6)
                val hasLog = done.any { d -> d >= dateOf(weekEnd) && d <= dateOf(cal) }
                if (hasLog) { count++; cal.add(Calendar.DAY_OF_YEAR, -7) } else return count
            }
            return count
        }
        while (count < 366) {
            val day = cal.get(Calendar.DAY_OF_WEEK)
            val isPlanned = frequency != "Dias úteis" || day != Calendar.SATURDAY && day != Calendar.SUNDAY
            if (isPlanned) { if (done.contains(dateOf(cal))) count++ else return count }
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return count
    }

    private fun bestHabitStreak(habit: JSONObject): Int {
        val logs = habit.optJSONArray("logs") ?: JSONArray()
        val done = HashSet<String>()
        for (i in 0 until logs.length()) logs.optJSONObject(i)?.let { done.add(it.optString("date")) }
        if (done.isEmpty()) return 0
        val frequency = habit.optString("frequency", "Diário")
        var cal = parseDate(done.maxOrNull() ?: return 0)?.let { Calendar.getInstance().apply { time = it } } ?: return 0
        var best = 0
        if (frequency == "Semanal") {
            var streak = 0
            repeat(260) {
                val end = cal.clone() as Calendar
                val start = cal.clone() as Calendar
                start.add(Calendar.DAY_OF_YEAR, -6)
                if (done.any { it >= dateOf(start) && it <= dateOf(end) }) { streak++; best = max(best, streak); cal.add(Calendar.DAY_OF_YEAR, -7) }
                else return best
            }
            return best
        }
        var streak = 0
        while (streak < 3660) {
            val day = cal.get(Calendar.DAY_OF_WEEK)
            val planned = frequency != "Dias úteis" || day != Calendar.SATURDAY && day != Calendar.SUNDAY
            if (planned) {
                if (done.contains(dateOf(cal))) streak++ else { best = max(best, streak); streak = 0 }
            }
            cal.add(Calendar.DAY_OF_YEAR, -1)
            best = max(best, streak)
        }
        return best
    }

    private fun habitWeeklyConsistency(habit: JSONObject): Int {
        val logs = habit.optJSONArray("logs") ?: JSONArray(); val done = HashSet<String>()
        for (i in 0 until logs.length()) logs.optJSONObject(i)?.let { done.add(it.optString("date")) }
        val frequency = habit.optString("frequency", "Diário")
        var expected = 0; var count = 0; val cal = Calendar.getInstance()
        if (frequency == "Semanal") {
            expected = 1
            repeat(7) { if (done.contains(dateOf(cal))) count = 1; cal.add(Calendar.DAY_OF_YEAR, -1) }
        } else {
            repeat(7) {
                val planned = frequency != "Dias úteis" || cal.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY && cal.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY
                if (planned) { expected++; if (done.contains(dateOf(cal))) count++ }
                cal.add(Calendar.DAY_OF_YEAR, -1)
            }
        }
        return if (expected == 0) 0 else count * 100 / expected
    }

    private fun latestHabitLog(habit: JSONObject): String {
        val logs = habit.optJSONArray("logs") ?: JSONArray(); var latest = ""
        for (i in 0 until logs.length()) { val d = logs.optJSONObject(i)?.optString("date") ?: ""; if (d > latest) latest = d }
        return latest
    }

    private fun dateDaysAgo(days: Int): String { val cal = Calendar.getInstance(); cal.add(Calendar.DAY_OF_YEAR, -days); return dateOf(cal) }
    private fun dateAfterDays(days: Int): String { val cal = Calendar.getInstance(); cal.add(Calendar.DAY_OF_YEAR, days); return dateOf(cal) }

    private fun countHabitsDoneToday(): Int {
        val habits = readArray("habits"); var count = 0
        for (i in 0 until habits.length()) { val h = habits.optJSONObject(i) ?: continue; if (findLogForDate(h.optJSONArray("logs") ?: JSONArray(), date) != null) count++ }
        return count
    }

    // ---------------------------------------------------------------------
    // FOCUS MODE: tempo persistido por timestamp, recuperável após encerrar o app.
    // ---------------------------------------------------------------------
    private fun focusPage(b: LinearLayout) {
        header(b, "07 / FOCUS MODE", "Modo execução")
        val active = activeFocus()
        if (active == null) {
            card(b) {
                addView(label("INICIAR UMA SESSÃO", 12, AQUA, true))
                addView(label("Escolha uma tarefa e uma duração. O tempo fica salvo mesmo se o aplicativo for fechado.", 11, MUTED))
            }
            val task = field("Qual tarefa será executada?", false)
            val duration = spinner(arrayOf("5 minutos", "25 minutos", "45 minutos", "60 minutos"))
            b.addView(task); b.addView(label("DURAÇÃO", 10, MUTED, true)); b.addView(duration)
            action(b, "▶  INICIAR FOCO") {
                if (task.text.isNullOrBlank()) { toast("Escolha a tarefa da sessão"); return@action }
                val mins = duration.selectedItem.toString().substringBefore(" ").toIntOrNull() ?: 25
                val state = JSONObject().put("id", newId()).put("task", task.text.toString().trim()).put("durationMinutes", mins)
                    .put("elapsedMs", 0L).put("startedAt", System.currentTimeMillis()).put("status", "running")
                prefs.edit().putString("focus_active", state.toString()).apply()
                showPage("focus"); toast("Sessão iniciada. Faça a próxima ação.")
            }
        } else {
            card(b) {
                addView(label("SESSÃO ${active.optString("status").uppercase(Locale("pt", "BR"))}", 11, AQUA, true))
                addView(label(active.optString("task"), 17, INK, true))
                val clock = label("00:00", 46, AQUA, true).apply { gravity = Gravity.CENTER; typeface = android.graphics.Typeface.MONOSPACE }
                focusClock = clock; addView(clock)
                val progress = ProgressBar(this@MainActivity, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = 0; progressTintList = android.content.res.ColorStateList.valueOf(AQUA); progressBackgroundTintList = android.content.res.ColorStateList.valueOf(GRID) }
                focusBar = progress; addView(progress, LinearLayout.LayoutParams(-1, 10))
                addView(label("${active.optInt("durationMinutes")} MINUTOS PLANEJADOS", 10, MUTED))
            }
            val buttons = horizontal()
            if (active.optString("status") == "running") smallButton(buttons, "PAUSAR", WARNING) { pauseFocus() }
            else smallButton(buttons, "RETOMAR", AQUA) { resumeFocus() }
            smallButton(buttons, "ENCERRAR", PANEL) { finishFocusEarly() }
            smallButton(buttons, "CANCELAR", 0xFF301216.toInt()) { cancelFocus() }
            b.addView(buttons)
            updateFocusDisplay()
        }
        b.addView(label("HISTÓRICO DE SESSÕES", 12, AQUA, true))
        val sessions = readArray("focus_sessions")
        for (i in sessions.length() - 1 downTo max(0, sessions.length() - 8)) {
            val s = sessions.optJSONObject(i) ?: continue
            rowCard(b, "${s.optString("task")}  •  ${s.optString("date")}", "${s.optInt("minutes")} min")
        }
        if (sessions.length() == 0) b.addView(label("Nenhuma sessão concluída.", 11, MUTED))
    }

    private fun activeFocus(): JSONObject? = try { prefs.getString("focus_active", null)?.let { JSONObject(it) } } catch (_: Exception) { null }

    private fun focusElapsed(active: JSONObject): Long {
        val stored = active.optLong("elapsedMs", 0L)
        return if (active.optString("status") == "running") stored + (System.currentTimeMillis() - active.optLong("startedAt", System.currentTimeMillis())).coerceAtLeast(0L) else stored
    }

    private fun updateFocusDisplay() {
        val active = activeFocus() ?: return
        val durationMs = active.optInt("durationMinutes", 25) * 60_000L
        val elapsed = focusElapsed(active)
        val remaining = (durationMs - elapsed).coerceAtLeast(0L)
        val minutes = remaining / 60_000L; val seconds = (remaining % 60_000L) / 1000L
        focusClock?.text = String.format(Locale.US, "%02d:%02d", minutes, seconds)
        focusBar?.progress = if (durationMs <= 0) 0 else (elapsed.coerceAtMost(durationMs) * 100 / durationMs).toInt()
        if (remaining <= 0L) completeFocusSession()
    }

    private fun startFocusTicker() {
        timerHandler.removeCallbacks(focusTick)
        if (activeFocus() != null) timerHandler.post(focusTick)
    }

    private fun pauseFocus() {
        val a = activeFocus() ?: return
        a.put("elapsedMs", focusElapsed(a)).put("startedAt", 0L).put("status", "paused")
        prefs.edit().putString("focus_active", a.toString()).apply(); showPage("focus")
    }

    private fun resumeFocus() {
        val a = activeFocus() ?: return
        a.put("startedAt", System.currentTimeMillis()).put("status", "running")
        prefs.edit().putString("focus_active", a.toString()).apply(); showPage("focus")
    }

    private fun finishFocusEarly() {
        AlertDialog.Builder(this).setTitle("Concluir sessão agora?").setMessage("O tempo efetivo será registrado.")
            .setNegativeButton("Continuar", null).setPositiveButton("Concluir") { _, _ -> completeFocusSession(true) }.show()
    }

    private fun completeFocusSession(early: Boolean = false) {
        val a = activeFocus() ?: return
        val elapsed = focusElapsed(a)
        val minutes = (elapsed / 60_000L).toInt()
        if (minutes <= 0) { prefs.edit().remove("focus_active").apply(); if (page == "focus") showPage("focus"); return }
        val sessions = readArray("focus_sessions")
        sessions.put(JSONObject().put("id", a.optString("id")).put("task", a.optString("task")).put("date", date).put("minutes", minutes).put("plannedMinutes", a.optInt("durationMinutes")).put("completedAt", System.currentTimeMillis()).put("early", early))
        saveArray("focus_sessions", sessions); prefs.edit().remove("focus_active").apply()
        awardXp(25, "Sessão de foco concluída")
        syncAchievements()
        timerHandler.removeCallbacks(focusTick)
        if (page == "focus") { toast("Sessão registrada: $minutes minuto(s) de foco"); showPage("focus") }
    }

    private fun checkFocusCompletion() {
        val active = activeFocus() ?: return
        if (active.optString("status") == "running") {
            val duration = active.optInt("durationMinutes", 25) * 60_000L
            if (focusElapsed(active) >= duration) completeFocusSession()
        }
    }

    private fun cancelFocus() {
        AlertDialog.Builder(this).setTitle("Cancelar sessão?").setNegativeButton("Voltar", null).setPositiveButton("Cancelar sessão") { _, _ ->
            prefs.edit().remove("focus_active").apply(); showPage("focus")
        }.show()
    }

    // ---------------------------------------------------------------------
    // ANTI-PROCRASTINATION PROTOCOL
    // ---------------------------------------------------------------------
    private val avoidanceReasons = arrayOf(
        "Não quero sentir o desconforto de começar", "Tenho medo de falhar", "Não sei por onde começar",
        "A tarefa é muito grande", "Estou cansado", "Estou distraído", "Estou esperando o momento perfeito"
    )

    private fun avoidancePage(b: LinearLayout) {
        header(b, "08 / ANTI-PROCRASTINATION", "Protocolo de confronto")
        card(b) {
            addView(label("RESISTÊNCIA NÃO É ORDEM", 12, WARNING, true))
            addView(label("Identifique o bloqueio, reduza a primeira ação e comece por cinco minutos.", 12, INK))
        }
        val task = field("Qual tarefa você está evitando?", false)
        val reason = spinner(avoidanceReasons)
        val rating = RatingBar(this).apply { numStars = 10; stepSize = 1f; rating = 5f; progressTintList = android.content.res.ColorStateList.valueOf(WARNING) }
        b.addView(task); b.addView(label("MOTIVO DA RESISTÊNCIA", 10, MUTED, true)); b.addView(reason)
        b.addView(label("INTENSIDADE  /  1 A 10", 10, MUTED, true)); b.addView(rating)
        action(b, "IDENTIFICAR E DEFINIR PRIMEIRA AÇÃO", WARNING) {
            if (task.text.isNullOrBlank()) { toast("Informe a tarefa que está evitando"); return@action }
            saveAvoidance(task.text.toString().trim(), reason.selectedItem.toString(), rating.rating.toInt().coerceIn(1, 10), "")
            showPage("avoidance")
        }
        b.addView(label("HISTÓRICO DE EVITAÇÃO", 12, AQUA, true))
        val logs = readArray("avoidance")
        for (i in logs.length() - 1 downTo 0) {
            val item = logs.optJSONObject(i) ?: continue
            card(b) {
                addView(label(item.optString("date") + "  /  RESISTÊNCIA ${item.optInt("intensity")}/10", 10, WARNING, true))
                addView(label(item.optString("task"), 15, INK, true))
                addView(label(item.optString("reason"), 11, MUTED))
                addView(label("PRIMEIRA AÇÃO: ${item.optString("firstAction")}", 11, AQUA))
                addView(label(if (item.optBoolean("started")) "STATUS: ENFRENTADA" else "STATUS: AGUARDANDO INÍCIO", 10, if (item.optBoolean("started")) AQUA else WARNING, true))
                if (!item.optBoolean("started")) action(this, "MARCAR COMO INICIADA  +30 XP", AQUA) { markAvoidanceStarted(item.optString("id")) }
            }
        }
        if (logs.length() == 0) b.addView(label("Nenhum registro de evitação. Use o protocolo quando perceber resistência.", 11, MUTED))
    }

    private fun logAvoidanceDialog(task: String, missionId: String) {
        val reason = spinner(avoidanceReasons)
        val rating = RatingBar(this).apply { numStars = 10; stepSize = 1f; rating = 5f; progressTintList = android.content.res.ColorStateList.valueOf(WARNING) }
        val box = verticalBox(label("TAREFA: $task", 13, INK, true), label("Por que está adiando?", 10, MUTED, true), reason, label("Intensidade da resistência", 10, MUTED, true), rating)
        AlertDialog.Builder(this).setTitle("Registrar adiamento").setView(box).setNegativeButton("Cancelar", null)
            .setPositiveButton("Registrar e confrontar") { _, _ ->
                saveAvoidance(task, reason.selectedItem.toString(), rating.rating.toInt().coerceIn(1, 10), missionId)
                showPage(if (missionId.isBlank()) "avoidance" else "missions")
            }.show()
    }

    private fun saveAvoidance(task: String, reason: String, intensity: Int, missionId: String) {
        val first = firstActionFor(reason)
        val arr = readArray("avoidance")
        arr.put(JSONObject().put("id", newId()).put("task", task).put("reason", reason).put("intensity", intensity).put("date", date)
            .put("firstAction", first).put("started", false).put("missionId", missionId))
        saveArray("avoidance", arr)
        if (missionId.isNotBlank()) {
            val missions = readArray("missions"); findById(missions, missionId)?.let { it.put("status", "adiada").put("deferCount", it.optInt("deferCount") + 1) }; saveArray("missions", missions)
        }
        toast("Primeira ação: $first")
    }

    private fun firstActionFor(reason: String): String = when {
        reason.contains("por onde") -> "Escreva a primeira etapa física e executável da tarefa."
        reason.contains("grande") -> "Escolha uma ação que leve no máximo cinco minutos."
        reason.contains("falhar") -> "Faça um experimento pequeno; o objetivo é aprender, não acertar de primeira."
        reason.contains("distraído") -> "Afaste o celular, prepare o ambiente e inicie cinco minutos de foco."
        reason.contains("cansado") -> "Reduza para a meta mínima e faça uma pausa breve depois."
        reason.contains("momento perfeito") -> "Defina um horário de início agora e execute a primeira etapa imperfeita."
        else -> "Comece por apenas cinco minutos, sem negociar com a resistência."
    }

    private fun markAvoidanceStarted(id: String) {
        val arr = readArray("avoidance"); val item = findById(arr, id) ?: return
        if (item.optBoolean("started")) return
        item.put("started", true).put("startedAt", date); saveArray("avoidance", arr)
        val missionId = item.optString("missionId")
        if (missionId.isNotBlank()) { val missions = readArray("missions"); findById(missions, missionId)?.put("status", "em execução").put("startedAt", date); saveArray("missions", missions) }
        awardXp(30, "Enfrentamento de tarefa difícil"); showPage("avoidance")
    }

    // ---------------------------------------------------------------------
    // PROJECT FREEDOM / GOALS
    // ---------------------------------------------------------------------
    private fun goalsPage(b: LinearLayout) {
        header(b, "09 / PROJECT FREEDOM", "Objetivos e etapas")
        val title = field("Nome do objetivo", false)
        val detail = field("Por que isso importa?", false, true)
        val steps = field("Etapas — uma por linha", false, true)
        b.addView(title); b.addView(detail); b.addView(steps)
        action(b, "＋  CRIAR OBJETIVO") {
            if (title.text.isNullOrBlank()) { toast("Informe o objetivo"); return@action }
            val list = JSONArray()
            steps.text.toString().lines().map { it.trim() }.filter { it.isNotBlank() }.forEach { list.put(JSONObject().put("title", it).put("done", false)) }
            val goals = readArray("goals")
            goals.put(JSONObject().put("id", newId()).put("title", title.text.toString().trim()).put("detail", detail.text.toString().trim()).put("steps", list).put("createdAt", date))
            saveArray("goals", goals); showPage("goals")
        }
        val goals = readArray("goals")
        b.addView(label("OBJETIVOS EM ANDAMENTO", 12, AQUA, true))
        for (i in goals.length() - 1 downTo 0) {
            val goal = goals.optJSONObject(i) ?: continue
            val list = goal.optJSONArray("steps") ?: JSONArray()
            val progress = goalProgress(goal)
            card(b) {
                addView(label("${progress}% CONCLUÍDO", 10, AQUA, true))
                addView(label(goal.optString("title"), 17, INK, true))
                if (goal.optString("detail").isNotBlank()) addView(label(goal.optString("detail"), 11, MUTED))
                addView(progressBar(progress))
                if (list.length() == 0) addView(label("Adicione etapas para acompanhar a execução.", 10, MUTED))
                for (j in 0 until list.length()) {
                    val step = list.optJSONObject(j) ?: continue
                    val check = CheckBox(this@MainActivity).apply {
                        text = step.optString("title")
                        textSize = 11f
                        setTextColor(if (step.optBoolean("done")) MUTED else INK)
                        isChecked = step.optBoolean("done")
                        if (Build.VERSION.SDK_INT >= 21) buttonTintList = android.content.res.ColorStateList.valueOf(AQUA)
                        setOnCheckedChangeListener { _, checked -> setGoalStep(goal.optString("id"), j, checked) }
                    }
                    addView(check)
                }
                val buttons = horizontal()
                smallButton(buttons, "＋ ETAPA", PANEL) { addGoalStep(goal.optString("id")) }
                smallButton(buttons, "EDITAR", PANEL) { editGoal(goal.optString("id")) }
                smallButton(buttons, "EXCLUIR", 0xFF301216.toInt()) { deleteGoal(goal.optString("id")) }
                addView(buttons)
            }
        }
        if (goals.length() == 0) b.addView(label("Nenhum objetivo cadastrado. Transforme sua visão em etapas.", 11, MUTED))
        b.addView(label("DIÁRIO DE AVANÇOS", 12, AQUA, true))
        action(b, "REGISTRAR AVANÇO  ↗", AQUA) { showPage("social") }
    }

    private fun goalProgress(goal: JSONObject): Int {
        val steps = goal.optJSONArray("steps") ?: JSONArray()
        if (steps.length() == 0) return 0
        var done = 0; for (i in 0 until steps.length()) if (steps.optJSONObject(i)?.optBoolean("done") == true) done++
        return done * 100 / steps.length()
    }

    private fun setGoalStep(id: String, index: Int, done: Boolean) {
        val goals = readArray("goals"); val goal = findById(goals, id) ?: return
        val steps = goal.optJSONArray("steps") ?: JSONArray(); val step = steps.optJSONObject(index) ?: return
        val wasDone = step.optBoolean("done"); step.put("done", done); goal.put("steps", steps); saveArray("goals", goals)
        if (done && !wasDone) awardXp(15, "Etapa de objetivo concluída")
        syncAchievements(); showPage("goals")
    }

    private fun addGoalStep(id: String) {
        val input = field("Nome da nova etapa", false)
        AlertDialog.Builder(this).setTitle("Adicionar etapa").setView(input).setNegativeButton("Cancelar", null).setPositiveButton("Adicionar") { _, _ ->
            if (input.text.isNullOrBlank()) return@setPositiveButton
            val goals = readArray("goals"); val goal = findById(goals, id) ?: return@setPositiveButton
            val steps = goal.optJSONArray("steps") ?: JSONArray(); steps.put(JSONObject().put("title", input.text.toString().trim()).put("done", false)); goal.put("steps", steps)
            saveArray("goals", goals); showPage("goals")
        }.show()
    }

    private fun editGoal(id: String) {
        val goals = readArray("goals")
        val goal = findById(goals, id) ?: return
        val title = field("Nome do objetivo", false).apply { setText(goal.optString("title")) }
        val detail = field("Por que isso importa?", false, true).apply { setText(goal.optString("detail")) }
        AlertDialog.Builder(this).setTitle("Editar objetivo").setView(verticalBox(title, detail))
            .setNegativeButton("Cancelar", null).setPositiveButton("Salvar") { _, _ ->
                if (title.text.isNullOrBlank()) { toast("Informe o nome do objetivo"); return@setPositiveButton }
                goal.put("title", title.text.toString().trim()).put("detail", detail.text.toString().trim())
                saveArray("goals", goals); showPage("goals")
            }.show()
    }

    private fun deleteGoal(id: String) {
        AlertDialog.Builder(this).setTitle("Excluir objetivo?").setNegativeButton("Cancelar", null).setPositiveButton("Excluir") { _, _ ->
            val old = readArray("goals"); val fresh = JSONArray()
            for (i in 0 until old.length()) { val g = old.optJSONObject(i) ?: continue; if (g.optString("id") != id) fresh.put(g) }
            saveArray("goals", fresh); showPage("goals")
        }.show()
    }

    // ---------------------------------------------------------------------
    // ANALYTICS AND WEEKLY REPORT
    // ---------------------------------------------------------------------
    private fun analyticsPage(b: LinearLayout) {
        header(b, "10 / ANALYTICS", "Relatório operacional")
        val missions = readArray("missions")
        val habits = readArray("habits")
        val avoid = readArray("avoidance")
        val sessions = readArray("focus_sessions")
        val workouts = readArray("workouts")
        val start = weekStartDate(); val end = date
        var planned = 0; var completed = 0; var deferred = 0
        for (i in 0 until missions.length()) {
            val m = missions.optJSONObject(i) ?: continue
            if (m.optString("createdAt") in start..end) { planned++; if (m.optString("status") == "concluída") completed++; if (m.optInt("deferCount") > 0) deferred += m.optInt("deferCount") }
        }
        var focusMins = 0
        for (i in 0 until sessions.length()) { val s = sessions.optJSONObject(i) ?: continue; if (s.optString("date") in start..end) focusMins += s.optInt("minutes") }
        var habitDone = 0
        for (i in 0 until habits.length()) {
            val h = habits.optJSONObject(i) ?: continue; val logs = h.optJSONArray("logs") ?: JSONArray()
            for (j in 0 until logs.length()) if (logs.optJSONObject(j)?.optString("date") in start..end) habitDone++
        }
        var avoidanceCount = 0; var started = 0; var intensitySum = 0
        val reasons = linkedMapOf<String, Int>()
        for (i in 0 until avoid.length()) {
            val a = avoid.optJSONObject(i) ?: continue
            if (a.optString("date") in start..end) {
                avoidanceCount++; if (a.optBoolean("started")) started++; intensitySum += a.optInt("intensity")
                val reason = a.optString("reason"); reasons[reason] = (reasons[reason] ?: 0) + 1
            }
        }
        var push = 0; var pull = 0
        for (i in 0 until workouts.length()) { val w = workouts.optJSONObject(i) ?: continue; if (w.optString("date") in start..end) { push += w.optInt("push"); pull += w.optInt("pull") } }
        val weekNet = sumBetween("income", start, end) - sumBetween("expenses", start, end)
        val monthCalendar = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
        val monthStart = dateOf(monthCalendar)
        val monthNet = sumBetween("income", monthStart, end) - sumBetween("expenses", monthStart, end)
        val monthTaskPush = sumWorkout(monthStart, end, "push")
        val monthTaskPull = sumWorkout(monthStart, end, "pull")
        val difficultPlanned = (0 until missions.length()).count { val m = missions.optJSONObject(it); m != null && m.optString("createdAt") in start..end && m.optString("difficulty") == "Difícil" }
        val difficultStarted = (0 until missions.length()).count { val m = missions.optJSONObject(it); m != null && m.optString("createdAt") in start..end && m.optString("difficulty") == "Difícil" && m.optString("startedAt").isNotBlank() }
        val facingRate = if (difficultPlanned == 0) null else difficultStarted * 100 / difficultPlanned
        card(b) {
            addView(label("SEMANA  /  $start ATÉ $end", 10, MUTED, true))
            addView(label("MISSÕES  $completed / $planned", 20, INK, true))
            addView(progressBar(if (planned == 0) 0 else completed * 100 / planned))
            addView(label("Hábitos realizados: $habitDone  •  adiamentos: $deferred", 11, MUTED))
            addView(label("Foco efetivo: ${focusMins / 60}h ${focusMins % 60}min", 11, MUTED))
            addView(label("Financeiro semanal: ${if (weekNet >= 0) "+" else "−"} R$ ${money(kotlin.math.abs(weekNet))}", 11, MUTED))
            addView(label("Financeiro no mês: ${if (monthNet >= 0) "+" else "−"} R$ ${money(kotlin.math.abs(monthNet))}", 11, MUTED))
            addView(label("Físico na semana: $push flexões  •  $pull barras", 11, MUTED))
            addView(label("Físico no mês: $monthTaskPush flexões  •  $monthTaskPull barras", 11, MUTED))
            addView(label("Evitações: $avoidanceCount  •  resistência média: ${if (avoidanceCount == 0) "—" else String.format(Locale.US, "%.1f", intensitySum.toDouble() / avoidanceCount)}", 11, MUTED))
            addView(label("Taxa de enfrentamento: ${if (avoidanceCount == 0) "—" else "${started * 100 / avoidanceCount}%"}", 11, AQUA, true))
            addView(label("Taxa de enfrentamento difícil: ${facingRate?.let { "$it%" } ?: "dados insuficientes"}", 11, AQUA, true))
        }
        b.addView(label("EVOLUÇÃO FINANCEIRA / 7 DIAS", 12, AQUA, true))
        val finance = lastSevenDaysNet()
        if (readArray("income").length() + readArray("expenses").length() == 0) b.addView(label("Sem dados financeiros para gerar gráfico.", 10, MUTED))
        else b.addView(IncomeChart(this, finance).apply { layoutParams = LinearLayout.LayoutParams(-1, 150) })
        b.addView(label("EVOLUÇÃO FÍSICA / REGISTROS", 12, AQUA, true))
        val physicalPoints = mutableListOf<Triple<String, Int, Int>>()
        for (i in max(0, workouts.length() - 7) until workouts.length()) {
            val w = workouts.optJSONObject(i) ?: continue
            physicalPoints.add(Triple(w.optString("date").takeLast(5), w.optInt("push"), w.optInt("pull")))
        }
        if (physicalPoints.isEmpty()) b.addView(label("Sem registros físicos ainda.", 10, MUTED)) else b.addView(ProgressChart(this, physicalPoints).apply { layoutParams = LinearLayout.LayoutParams(-1, 180) })
        b.addView(label("MOTIVOS MAIS FREQUENTES DE EVITAÇÃO", 12, AQUA, true))
        if (reasons.isEmpty()) b.addView(label("Sem registros de evitação nesta semana.", 10, MUTED))
        else reasons.entries.sortedByDescending { it.value }.forEach { rowCard(b, it.key, "${it.value}x") }
        b.addView(label("RELATÓRIO E PRÓXIMA SEMANA", 12, AQUA, true))
        card(b) {
            addView(label(weeklyRecommendation(planned, completed, avoidanceCount, started, focusMins, habits.length()), 12, INK))
            addView(label("Os indicadores usam somente os registros salvos no dispositivo. Onde não há dados, o resultado aparece como indisponível.", 10, MUTED))
        }
        b.addView(label("RELATÓRIOS SEMANAIS SALVOS", 12, AQUA, true))
        val reports = readArray("weekly_reports")
        for (i in reports.length() - 1 downTo max(0, reports.length() - 8)) {
            val r = reports.optJSONObject(i) ?: continue
            card(b) {
                addView(label("${r.optString("start")}  →  ${r.optString("end")}", 10, MUTED, true))
                addView(label("Missões ${r.optInt("completed")}/${r.optInt("planned")}  •  Foco ${r.optInt("focusMinutes")} min", 12, INK))
                addView(label("Hábitos ${r.optInt("habitLogs")}  •  Adiamentos ${r.optInt("postponed")}  •  Enfrentadas ${r.optInt("faced")}/${r.optInt("avoidance")}", 10, MUTED))
                addView(label("Financeiro: R$ ${money(r.optDouble("weekNet"))}  •  ${r.optInt("push")} flexões  /  ${r.optInt("pull")} barras", 10, MUTED))
            }
        }
        if (reports.length() == 0) b.addView(label("O primeiro relatório histórico será salvo automaticamente quando o app for aberto após a virada de semana.", 10, MUTED))
    }

    private fun weeklyRecommendation(planned: Int, completed: Int, avoidance: Int, started: Int, focus: Int, habitCount: Int): String {
        val out = mutableListOf<String>()
        if (planned == 0) out.add("Defina até três missões importantes para a próxima semana.")
        else if (completed * 100 / planned < 60) out.add("Reduza o volume de missões e proteja a primeira ação da prioridade principal.")
        else out.add("Mantenha o volume atual e aumente a dificuldade gradualmente.")
        if (avoidance > 0 && started * 100 / avoidance < 50) out.add("Ao adiar, transforme a tarefa em uma ação de até cinco minutos e marque o início.")
        if (focus < 100) out.add("Reserve blocos curtos de foco na agenda e registre o tempo efetivo.")
        if (habitCount == 0) out.add("Cadastre hábitos com metas mínimas para facilitar a retomada.")
        return out.joinToString("\n\n")
    }

    private fun xpBetween(from: String, to: String): Int {
        val history = readArray("xp_history"); var total = 0
        for (i in 0 until history.length()) { val item = history.optJSONObject(i) ?: continue; if (item.optString("date") in from..to) total += item.optInt("points") }
        return total
    }

    /** Gera um snapshot local da semana anterior na primeira abertura após a virada. */
    private fun generateWeeklyReportIfNeeded() {
        val currentStart = weekStartDate()
        val previousStart = prefs.getString("last_week_start", null)
        if (previousStart == null) { prefs.edit().putString("last_week_start", currentStart).apply(); return }
        if (previousStart == currentStart) return
        val endCal = parseDate(currentStart)?.let { Calendar.getInstance().apply { time = it; add(Calendar.DAY_OF_YEAR, -1) } } ?: return
        val end = dateOf(endCal)
        val missions = readArray("missions"); var planned = 0; var completed = 0; var postponed = 0
        for (i in 0 until missions.length()) { val m = missions.optJSONObject(i) ?: continue; if (m.optString("createdAt") in previousStart..end) { planned++; if (m.optString("status") == "concluída") completed++; postponed += m.optInt("deferCount") } }
        val sessions = readArray("focus_sessions"); var focus = 0
        for (i in 0 until sessions.length()) { val s = sessions.optJSONObject(i) ?: continue; if (s.optString("date") in previousStart..end) focus += s.optInt("minutes") }
        val habits = readArray("habits"); var habitLogs = 0
        for (i in 0 until habits.length()) { val logs = habits.optJSONObject(i)?.optJSONArray("logs") ?: continue; for (j in 0 until logs.length()) if (logs.optJSONObject(j)?.optString("date") in previousStart..end) habitLogs++ }
        val avoidance = readArray("avoidance"); var avoided = 0; var faced = 0
        for (i in 0 until avoidance.length()) { val item = avoidance.optJSONObject(i) ?: continue; if (item.optString("date") in previousStart..end) { avoided++; if (item.optBoolean("started")) faced++ } }
        val report = JSONObject().put("id", newId()).put("start", previousStart).put("end", end).put("planned", planned).put("completed", completed)
            .put("postponed", postponed).put("focusMinutes", focus).put("habitLogs", habitLogs).put("weekNet", sumBetween("income", previousStart, end) - sumBetween("expenses", previousStart, end))
            .put("push", sumWorkout(previousStart, end, "push")).put("pull", sumWorkout(previousStart, end, "pull"))
            .put("avoidance", avoided).put("faced", faced).put("createdAt", date)
        val reports = readArray("weekly_reports"); reports.put(report); saveArray("weekly_reports", reports)
        prefs.edit().putString("last_week_start", currentStart).apply()
    }

    private fun sumWorkout(from: String, to: String, key: String): Int {
        val workouts = readArray("workouts"); var total = 0
        for (i in 0 until workouts.length()) { val item = workouts.optJSONObject(i) ?: continue; if (item.optString("date") in from..to) total += item.optInt(key) }
        return total
    }

    private fun sumBetween(key: String, from: String, to: String): Double {
        val a = readArray(key); var sum = 0.0
        for (i in 0 until a.length()) { val o = a.optJSONObject(i) ?: continue; val d = o.optString("date"); if (d in from..to) sum += transactionAmount(o) }
        return sum
    }

    // ---------------------------------------------------------------------
    // XP, ACHIEVEMENTS AND RANKING
    // ---------------------------------------------------------------------
    private fun achievementsPage(b: LinearLayout) {
        header(b, "11 / ACHIEVEMENTS", "Conquistas e níveis")
        val xp = totalXp(); val level = xpLevel(xp)
        card(b) {
            addView(label("NÍVEL $level", 12, AQUA, true))
            addView(label("$xp XP", 34, INK, true))
            addView(progressBar((xp % 500L).toInt() * 100 / 500))
            addView(label("${500 - (xp % 500L)} XP para subir de nível", 10, MUTED))
        }
        b.addView(label("CONQUISTAS DESBLOQUEÁVEIS", 12, AQUA, true))
        val stats = achievementStats()
        val achievements = listOf(
            LifeAchievement("first_mission", "Primeira missão", stats.first >= 1, "Conclua sua primeira missão."),
            LifeAchievement("mission_10", "Executor", stats.first >= 10, "Conclua dez missões."),
            LifeAchievement("focus_5", "Em estado de foco", stats.second >= 5, "Conclua cinco sessões de foco."),
            LifeAchievement("habit_7", "Consistência", stats.third >= 7, "Mantenha um hábito por sete dias."),
            LifeAchievement("goal_3", "Projeto em movimento", completedGoalSteps() >= 3, "Conclua três etapas de objetivos."),
            LifeAchievement("physical_10", "Corpo em evolução", totalExerciseEntries() >= 10, "Registre dez treinos.")
        )
        achievements.forEach { achievement ->
            card(b) {
                addView(label(if (achievement.unlocked) "◇ DESBLOQUEADA  /  ${achievement.title}" else "◈ BLOQUEADA  /  ${achievement.title}", 13, if (achievement.unlocked) AQUA else MUTED, true))
                addView(label(achievement.description, 11, MUTED))
            }
        }
        val todayCal = Calendar.getInstance(); todayCal.set(Calendar.DAY_OF_MONTH, 1)
        val monthStart = dateOf(todayCal)
        b.addView(label("RANKING PESSOAL", 12, AQUA, true))
        rowCard(b, "XP nesta semana", "${xpBetween(weekStartDate(), date)} XP")
        rowCard(b, "XP neste mês", "${xpBetween(monthStart, date)} XP")
        b.addView(label("XP RECENTE", 12, AQUA, true))
        val history = readArray("xp_history")
        for (i in history.length() - 1 downTo max(0, history.length() - 10)) {
            val h = history.optJSONObject(i) ?: continue
            rowCard(b, "${h.optString("date")}  •  ${h.optString("reason")}", "${if (h.optInt("points") >= 0) "+" else ""}${h.optInt("points")} XP")
        }
        if (history.length() == 0) b.addView(label("Seu histórico de XP aparecerá conforme concluir ações.", 11, MUTED))
    }

    private fun totalXp(): Long = prefs.getLong("xp_total", 0L)
    private fun xpLevel(xp: Long): Int = (max(0L, xp) / 500L).toInt() + 1

    private fun awardXp(points: Int, reason: String) {
        val current = totalXp()
        val updated = max(0L, current + points)
        prefs.edit().putLong("xp_total", updated).apply()
        val history = readArray("xp_history")
        history.put(JSONObject().put("id", newId()).put("date", date).put("points", points).put("reason", reason).put("total", updated))
        saveArray("xp_history", history)
    }

    private fun achievementStats(): Triple<Int, Int, Int> {
        val missions = readArray("missions"); val doneMissions = (0 until missions.length()).count { missions.optJSONObject(it)?.optString("status") == "concluída" }
        val focus = readArray("focus_sessions").length()
        val habits = readArray("habits"); var best = 0
        for (i in 0 until habits.length()) habits.optJSONObject(i)?.let { best = max(best, habitStreak(it)) }
        return Triple(doneMissions, focus, best)
    }

    private fun completedGoalSteps(): Int {
        val goals = readArray("goals"); var done = 0
        for (i in 0 until goals.length()) { val steps = goals.optJSONObject(i)?.optJSONArray("steps") ?: continue; for (j in 0 until steps.length()) if (steps.optJSONObject(j)?.optBoolean("done") == true) done++ }
        return done
    }

    private fun totalExerciseEntries() = readArray("workouts").length()

    private fun syncAchievements() {
        val stats = achievementStats()
        val achieved = mutableSetOf<String>()
        if (stats.first >= 1) achieved.add("first_mission")
        if (stats.first >= 10) achieved.add("mission_10")
        if (stats.second >= 5) achieved.add("focus_5")
        if (stats.third >= 7) achieved.add("habit_7")
        if (completedGoalSteps() >= 3) achieved.add("goal_3")
        if (totalExerciseEntries() >= 10) achieved.add("physical_10")
        val old = prefs.getStringSet("achievements", emptySet())?.toMutableSet() ?: mutableSetOf()
        val fresh = achieved - old
        if (fresh.isNotEmpty()) {
            prefs.edit().putStringSet("achievements", old + achieved).apply()
            fresh.forEach { awardXp(50, "Conquista desbloqueada: $it") }
        }
    }

    // ---------------------------------------------------------------------
    // SETTINGS / LOCAL BACKUP / NOTIFICATIONS
    // ---------------------------------------------------------------------
    private fun settingsPage(b: LinearLayout) {
        header(b, "12 / SYSTEM CONFIG", "Configurações")
        card(b) {
            addView(label("ARMAZENAMENTO LOCAL", 12, AQUA, true))
            addView(label("Seus dados permanecem no aparelho. Faça backup antes de trocar de dispositivo ou reinstalar o app.", 11, MUTED))
        }
        val monthlyGoal = field("Meta de renda mensal (R$)", true).apply { setText(prefs.getString("monthly_income_goal", "")) }
        val pushGoal = field("Meta diária de flexões", true).apply { setText(prefs.getString("push_goal", "30")) }
        val pullGoal = field("Meta diária de barras", true).apply { setText(prefs.getString("pull_goal", "5")) }
        b.addView(label("METAS PESSOAIS", 12, AQUA, true)); b.addView(monthlyGoal); b.addView(pushGoal); b.addView(pullGoal)
        action(b, "SALVAR METAS") {
            prefs.edit().putString("monthly_income_goal", monthlyGoal.text.toString()).putString("push_goal", pushGoal.text.toString()).putString("pull_goal", pullGoal.text.toString()).apply()
            toast("Metas atualizadas")
        }
        val notify = CheckBox(this).apply {
            text = "Habilitar lembretes de hábitos e avisos de prazos"
            textSize = 12f
            setTextColor(INK)
            isChecked = prefs.getBoolean("notifications_enabled", true)
            if (Build.VERSION.SDK_INT >= 21) buttonTintList = android.content.res.ColorStateList.valueOf(AQUA)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("notifications_enabled", checked).apply()
                if (checked) {
                    requestNotificationPermissionIfNeeded()
                    scheduleAllHabitReminders()
                    DeadlineReminderScheduler.syncAll(this@MainActivity)
                } else {
                    cancelAllHabitReminders()
                    DeadlineReminderScheduler.syncAll(this@MainActivity)
                }
            }
        }
        b.addView(notify)
        action(b, "EXPORTAR BACKUP JSON") { exportBackup() }
        action(b, "RESTAURAR BACKUP JSON", PANEL) { restoreBackup() }
        action(b, "ABRIR ANALYTICS  ↗", AQUA) { showPage("analytics") }
        action(b, "ABRIR PROTOCOLO ANTI-PROCRASTINAÇÃO  ↗", WARNING) { showPage("avoidance") }
        b.addView(label("LIFE.EXE  •  DISCIPLINE PROTOCOL  •  v2.0", 10, MUTED, true))
        b.addView(label("Android nativo / armazenamento local / sem conta e sem servidor.", 10, MUTED))
    }

    private fun exportBackup() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "life-exe-backup-$date.json")
        }
        startActivityForResult(intent, 7101)
    }

    private fun restoreBackup() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "application/json" }
        startActivityForResult(intent, 7102)
    }

    @Deprecated("Use the Android document picker")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) return
        val uri = data?.data ?: return
        try {
            when (requestCode) {
                7101 -> {
                    val backup = JSONObject()
                    val all = prefs.all
                    for ((key, value) in all) {
                        val wrapped = JSONObject()
                        when (value) {
                            is String -> wrapped.put("type", "string").put("value", value)
                            is Boolean -> wrapped.put("type", "boolean").put("value", value)
                            is Int -> wrapped.put("type", "int").put("value", value)
                            is Long -> wrapped.put("type", "long").put("value", value)
                            is Float -> wrapped.put("type", "float").put("value", value.toDouble())
                            is Set<*> -> { val values = JSONArray(); value.forEach { values.put(it.toString()) }; wrapped.put("type", "set").put("value", values) }
                        }
                        backup.put(key, wrapped)
                    }
                    contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(backup.toString(2)) }
                    toast("Backup exportado")
                }
                7102 -> {
                    val raw = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: throw IllegalStateException("Arquivo vazio")
                    val backup = JSONObject(raw); val editor = prefs.edit().clear(); val keys = backup.keys()
                    while (keys.hasNext()) {
                        val key = keys.next(); val value = backup.optJSONObject(key) ?: continue
                        when (value.optString("type")) {
                            "string" -> editor.putString(key, value.optString("value"))
                            "boolean" -> editor.putBoolean(key, value.optBoolean("value"))
                            "int" -> editor.putInt(key, value.optInt("value"))
                            "long" -> editor.putLong(key, value.optLong("value"))
                            "float" -> editor.putFloat(key, value.optDouble("value").toFloat())
                            "set" -> { val list = value.optJSONArray("value") ?: JSONArray(); val set = mutableSetOf<String>(); for (i in 0 until list.length()) set.add(list.optString(i)); editor.putStringSet(key, set) }
                        }
                    }
                    editor.apply(); refreshWidgets(); showPage("home"); toast("Backup restaurado")
                }
            }
        } catch (e: Exception) { toast("Falha no backup: ${e.message ?: "arquivo inválido"}") }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7201)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 7201) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            toast(if (granted) "Notificações habilitadas" else "Permissão de notificações não concedida")
            if (granted) DeadlineReminderScheduler.syncAll(this)
        }
    }

    private fun setHabitReminder(habit: JSONObject) {
        if (!prefs.getBoolean("notifications_enabled", true)) return
        val time = habit.optString("reminder")
        if (!validTime(time)) return
        val parts = time.split(":"); val hour = parts[0].toInt(); val minute = parts[1].toInt()
        HabitReminderScheduler.schedule(this, habit.optString("id"), habit.optString("name"), hour, minute,
            habit.optString("frequency", "Diário"), habit.optInt("weeklyDay", Calendar.MONDAY))
    }

    private fun cancelHabitReminder(id: String) {
        val intent = Intent(this, HabitReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(this, id.hashCode(), intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) ?: return
        (getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending); pending.cancel()
    }

    private fun scheduleAllHabitReminders() {
        val habits = readArray("habits")
        for (i in 0 until habits.length()) habits.optJSONObject(i)?.let { if (it.optString("reminder").isNotBlank()) setHabitReminder(it) }
    }

    private fun cancelAllHabitReminders() {
        val habits = readArray("habits"); for (i in 0 until habits.length()) habits.optJSONObject(i)?.let { cancelHabitReminder(it.optString("id")) }
    }

    // ---------------------------------------------------------------------
    // Existing record editing (kept compatible with the original app keys).
    // ---------------------------------------------------------------------
    private fun editableRecord(parent: LinearLayout, title: String, value: String, buttonText: String, onEdit: () -> Unit, onDelete: () -> Unit) {
        card(parent) {
            val line = horizontal()
            line.addView(label(title, 12, INK, true), LinearLayout.LayoutParams(0, -2, 1f))
            line.addView(label(value, 12, AQUA, true)); addView(line)
            val buttons = horizontal()
            smallButton(buttons, "EDITAR", PANEL, onEdit)
            smallButton(buttons, "EXCLUIR", 0xFF301216.toInt(), onDelete)
            addView(buttons)
        }
    }

    private fun editMoneyRecord(key: String, index: Int) {
        val arr = readArray(key); val item = arr.optJSONObject(index) ?: return
        val amount = field("Valor", true).apply { setText(transactionAmount(item).toString()) }
        val desc = field("Descrição", false).apply { setText(item.optString("desc")) }
        val dateField = datePickerControl("DATA DO REGISTRO", item.optString("date", date))
        val box = verticalBox(amount, desc, dateField)
        AlertDialog.Builder(this).setTitle(if (key == "income") "Editar receita" else "Editar gasto").setView(box)
            .setNegativeButton("Cancelar", null).setPositiveButton("Salvar") { _, _ ->
                val parsed = parseAmount(amount.text.toString())
                val selectedDate = dateField.tag as? String ?: date
                if (parsed == null || parsed <= 0 || !validDate(selectedDate)) { toast("Confira valor e data"); return@setPositiveButton }
                item.put("amount", parsed).put("desc", desc.text.toString().ifBlank { if (key == "income") "Receita" else "Gasto" }).put("date", selectedDate)
                saveArray(key, arr); refreshWidgets(); showPage("money"); toast("Registro atualizado")
            }.show()
    }

    private fun editWorkoutRecord(index: Int) {
        val arr = readArray("workouts"); val item = arr.optJSONObject(index) ?: return
        val push = field("Quantidade de flexões", true).apply { setText(item.optString("push")) }
        val pull = field("Quantidade de barras", true).apply { setText(item.optString("pull")) }
        val note = field("Observação", false, true).apply { setText(item.optString("note")) }
        val dateField = datePickerControl("DATA DO TREINO", item.optString("date", date))
        val box = verticalBox(push, pull, note, dateField)
        AlertDialog.Builder(this).setTitle("Editar treino").setView(box).setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar") { _, _ ->
                val p = push.text.toString().toIntOrNull(); val q = pull.text.toString().toIntOrNull()
                val selectedDate = dateField.tag as? String ?: ""
                if (p == null || q == null || p < 0 || q < 0 || (p == 0 && q == 0) || !validDate(selectedDate)) { toast("Confira quantidades e data"); return@setPositiveButton }
                item.put("push", p).put("pull", q).put("note", note.text.toString()).put("date", selectedDate)
                saveArray("workouts", arr); showPage("body"); toast("Treino atualizado")
            }.show()
    }

    private fun editSocialRecord(index: Int) {
        val arr = readArray("social"); val item = arr.optJSONObject(index) ?: return
        val title = field("Título", false).apply { setText(item.optString("title")) }
        val detail = field("Descrição / evolução", false, true).apply { setText(item.optString("detail")) }
        val dateField = datePickerControl("DATA DO AVANÇO", item.optString("date", date))
        val box = verticalBox(title, detail, dateField)
        AlertDialog.Builder(this).setTitle("Editar evolução").setView(box).setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar") { _, _ ->
                val selectedDate = dateField.tag as? String ?: ""
                if (title.text.isNullOrBlank() || detail.text.isNullOrBlank() || !validDate(selectedDate)) { toast("Preencha título, descrição e data válida"); return@setPositiveButton }
                item.put("title", title.text.toString().trim()).put("detail", detail.text.toString().trim()).put("date", selectedDate)
                saveArray("social", arr); showPage("social"); toast("Evolução atualizada")
            }.show()
    }

    // ---------------------------------------------------------------------
    // UI and data helpers.
    // ---------------------------------------------------------------------
    private fun refreshWidgets() { IncomeWidgetProvider.refreshAll(this); TodoWidgetProvider.refreshAll(this) }
    private fun datePickerControl(title: String, initialDate: String, optional: Boolean = false): TextView {
        val initial = initialDate.takeIf { validDate(it) } ?: if (optional) "" else date
        val control = TextView(this).apply {
            tag = initial
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(INK)
            setPadding(12, 13, 12, 13)
            background = rounded(PANEL_2, GRID, 10f)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 5, 0, 5) }
            isClickable = true
            isFocusable = true
        }
        fun updateLabel() {
            val raw = control.tag as? String ?: ""
            val shown = if (raw.isBlank()) "Sem data definida" else
                parseDate(raw)?.let { SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(it) } ?: raw
            control.text = "$title  ·  $shown   ▾"
        }
        updateLabel()
        control.setOnClickListener {
            val selected = (control.tag as? String)?.takeIf { it.isNotBlank() }?.let { parseDate(it) }
            val calendar = Calendar.getInstance().apply { if (selected != null) time = selected }
            val dialog = DatePickerDialog(
                this,
                { _, year, month, day ->
                    val chosen = Calendar.getInstance().apply { set(year, month, day, 12, 0, 0); set(Calendar.MILLISECOND, 0) }
                    control.tag = dateOf(chosen)
                    updateLabel()
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            if (optional) dialog.setButton(android.content.DialogInterface.BUTTON_NEUTRAL, "Limpar") { _, _ ->
                control.tag = ""
                updateLabel()
            }
            dialog.show()
        }
        return control
    }

    private fun transactionAmount(item: JSONObject): Double {
        val raw = when {
            !item.isNull("amount") -> item.opt("amount")
            !item.isNull("value") -> item.opt("value")
            !item.isNull("valor") -> item.opt("valor")
            else -> null
        }
        if (raw is Number) return raw.toDouble()
        val text = raw?.toString()?.replace("R$", "")?.replace(" ", "") ?: return 0.0
        val normalized = if (text.contains(",")) text.replace(".", "").replace(",", ".") else text
        return normalized.toDoubleOrNull() ?: 0.0
    }

    private fun parseAmount(raw: String): Double? = raw.trim().replace("R$", "").replace(" ", "").let { if (it.contains(",")) it.replace(".", "").replace(",", ".").toDoubleOrNull() else it.toDoubleOrNull() }
    private fun header(b: LinearLayout, tag: String, title: String) { b.addView(label(tag, 10, AQUA, true)); b.addView(label(title, 26, INK, true)) }
    private fun label(s: String, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size.toFloat(); setTextColor(color); typeface = android.graphics.Typeface.MONOSPACE
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, 5, 0, 7)
    }
    private fun rounded(color: Int, stroke: Int = GRID, radius: Float = 14f) = android.graphics.drawable.GradientDrawable().apply { setColor(color); cornerRadius = radius; setStroke(1, stroke) }
    private fun pill(color: Int) = rounded(color, color, 13f)
    private fun card(parent: LinearLayout, content: LinearLayout.() -> Unit) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(15, 12, 15, 12)
            background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF0B2116.toInt(), 0xFF041009.toInt())).apply { cornerRadius = 15f; setStroke(1, 0xFF167747.toInt()) }
            content()
        }
        parent.addView(box, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 7, 0, 7) })
    }
    private fun field(hint: String, numeric: Boolean, multi: Boolean = false) = EditText(this).apply {
        this.hint = hint; textSize = 13f; setTextColor(INK); setHintTextColor(MUTED)
        inputType = if (numeric) (InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED) else (InputType.TYPE_CLASS_TEXT or (if (multi) InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0))
        if (multi) { minLines = 3; gravity = Gravity.TOP }
        setPadding(13, 11, 13, 11); background = rounded(0xFF0B1B12.toInt(), GRID, 10f)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 5, 0, 5) }
    }
    private fun action(parent: LinearLayout, text: String, color: Int = AQUA, onClick: () -> Unit) {
        val btn = Button(this).apply { this.text = text; textSize = 11f; isAllCaps = false; setTextColor(if (color == AQUA) 0xFF001B0D.toInt() else INK); background = pill(if (color == AQUA) AQUA else 0xFF10291B.toInt()); setOnClickListener { onClick() } }
        parent.addView(btn, LinearLayout.LayoutParams(-1, 48).apply { setMargins(0, 5, 0, 5) })
    }
    private fun horizontal() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun verticalBox(vararg views: View) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(8, 4, 8, 0); views.forEach { addView(it) } }
    private fun spinner(items: Array<String>) = Spinner(this).apply {
        adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, items).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        background = rounded(PANEL, GRID, 9f)
    }
    private fun addFormChoice(parent: LinearLayout, title: String, control: View) { parent.addView(label(title, 10, MUTED, true)); parent.addView(control, LinearLayout.LayoutParams(-1, 46).apply { setMargins(0, 0, 0, 6) }) }
    private fun progressBar(progress: Int) = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 100; this.progress = progress.coerceIn(0, 100)
        if (Build.VERSION.SDK_INT >= 21) { progressTintList = android.content.res.ColorStateList.valueOf(AQUA); progressBackgroundTintList = android.content.res.ColorStateList.valueOf(GRID) }
        layoutParams = LinearLayout.LayoutParams(-1, 8).apply { setMargins(0, 5, 0, 5) }
    }
    private fun smallStat(parent: LinearLayout, title: String, value: String, color: Int, weight: Float, onClick: () -> Unit) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12, 11, 8, 10); background = rounded(PANEL, GRID, 13f); setOnClickListener { onClick() }; addView(label(title, 9, MUTED, true)); addView(label(value, 18, color, true)) }
        parent.addView(box, LinearLayout.LayoutParams(0, -2, weight).apply { setMargins(3, 3, 3, 3) })
    }
    private fun smallButton(parent: LinearLayout, text: String, color: Int, onClick: () -> Unit) {
        val btn = Button(this).apply { this.text = text; textSize = 9f; isAllCaps = false; minHeight = 38; setTextColor(if (color == AQUA) 0xFF001B0D.toInt() else INK); background = rounded(if (color == AQUA) AQUA else color, GRID, 9f); setOnClickListener { onClick() } }
        parent.addView(btn, LinearLayout.LayoutParams(0, 42, 1f).apply { setMargins(2, 3, 2, 3) })
    }
    private fun rowCard(parent: LinearLayout, left: String, right: String) {
        card(parent) { val r = horizontal(); r.addView(label(left, 11, INK, true), LinearLayout.LayoutParams(0, -2, 1f)); r.addView(label(right, 11, AQUA, true)); addView(r) }
    }
    private fun readArray(key: String) = try { JSONArray(prefs.getString(key, "[]")) } catch (_: Exception) { JSONArray() }
    private fun saveArray(key: String, a: JSONArray) {
        prefs.edit().putString(key, a.toString()).apply()
        if (key == "missions" || key == "todos") {
            if (key == "missions") TodoWidgetProvider.refreshAll(this)
            if (prefs.getBoolean("notifications_enabled", true)) {
                DeadlineReminderScheduler.syncAll(this)
                if (hasActiveDeadlines()) requestNotificationPermissionIfNeeded()
            }
        }
    }
    private fun hasActiveDeadlines(): Boolean {
        val tasks = readArray("todos")
        for (i in 0 until tasks.length()) {
            val item = tasks.optJSONObject(i) ?: continue
            if (!item.optBoolean("done") && item.optString("due").isNotBlank() && !item.optBoolean("overdueNotified")) return true
        }
        val missions = readArray("missions")
        for (i in 0 until missions.length()) {
            val item = missions.optJSONObject(i) ?: continue
            if (item.optString("status", "pendente") !in listOf("concluída", "cancelada") && item.optString("due").isNotBlank() && !item.optBoolean("overdueNotified")) return true
        }
        return false
    }
    private fun findById(array: JSONArray, id: String): JSONObject? { for (i in 0 until array.length()) { val item = array.optJSONObject(i) ?: continue; if (item.optString("id") == id) return item }; return null }
    private fun findLogForDate(logs: JSONArray, d: String): JSONObject? { for (i in 0 until logs.length()) { val item = logs.optJSONObject(i) ?: continue; if (item.optString("date") == d) return item }; return null }
    private fun newId() = UUID.randomUUID().toString()
    private fun totalIncome(): Double { val a = readArray("income"); var s = 0.0; for (i in 0 until a.length()) s += a.optJSONObject(i)?.let { transactionAmount(it) } ?: 0.0; return s }
    private fun totalExpenses(): Double { val a = readArray("expenses"); var s = 0.0; for (i in 0 until a.length()) s += a.optJSONObject(i)?.let { transactionAmount(it) } ?: 0.0; return s }
    private fun sumMonthIncome(): Double {
        val cal = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }; return sumBetween("income", dateOf(cal), date)
    }
    private fun sumToday(key: String): Double { val a = readArray(key); var s = 0.0; for (i in 0 until a.length()) { val o = a.optJSONObject(i) ?: continue; if (o.optString("date") == date) s += transactionAmount(o) }; return s }
    private fun todayIncome() = sumToday("income")
    private fun todayExpenses() = sumToday("expenses")
    private fun todayNet() = todayIncome() - todayExpenses()
    private fun pendingTodos(): JSONArray { val all = readArray("todos"); val out = JSONArray(); for (i in 0 until all.length()) { val o = all.optJSONObject(i) ?: continue; if (!o.optBoolean("done")) out.put(o) }; return out }
    private fun todayExercise(): Pair<Int, Int> { val a = readArray("workouts"); var p = 0; var q = 0; for (i in 0 until a.length()) { val o = a.optJSONObject(i) ?: continue; if (o.optString("date") == date) { p += o.optInt("push"); q += o.optInt("pull") } }; return p to q }
    private fun allExerciseTotals(): Pair<Int, Int> { val a = readArray("workouts"); var p = 0; var q = 0; for (i in 0 until a.length()) { val o = a.optJSONObject(i) ?: continue; p += o.optInt("push"); q += o.optInt("pull") }; return p to q }
    private fun personalExerciseRecord(key: String): Pair<Int, String> {
        val workouts = readArray("workouts")
        var best = 0
        var bestDate = ""
        for (i in 0 until workouts.length()) {
            val item = workouts.optJSONObject(i) ?: continue
            val value = item.optInt(key)
            if (value > best) { best = value; bestDate = item.optString("date") }
        }
        val formatted = if (bestDate.isBlank()) "" else try { SimpleDateFormat("dd/MM/yy", Locale("pt", "BR")).format(parseDate(bestDate)!!) } catch (_: Exception) { bestDate }
        return best to formatted
    }
    private fun exerciseDayTrend(key: String): Triple<String, String, Int> {
        val byDay = sortedMapOf<String, Int>()
        val workouts = readArray("workouts")
        for (i in 0 until workouts.length()) {
            val item = workouts.optJSONObject(i) ?: continue
            val d = item.optString("date")
            if (validDate(d)) byDay[d] = (byDay[d] ?: 0) + item.optInt(key)
        }
        if (byDay.size < 2) return Triple("●", "SEM COMPARAÇÃO", MUTED)
        val values = byDay.values.toList()
        val previous = values[values.lastIndex - 1]
        val latest = values.last()
        return when {
            latest > previous -> Triple("▲", "MELHOR QUE O ÚLTIMO TREINO", AQUA)
            latest < previous -> Triple("▼", "ABAIXO DO ÚLTIMO TREINO", DANGER)
            else -> Triple("◆", "IGUAL AO ÚLTIMO TREINO", WARNING)
        }
    }
    private fun monthlyPatrimonySeries(): List<Pair<String, Double>> {
        val calendar = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
        val start = dateOf(calendar)
        val netByDate = sortedMapOf<String, Double>()
        listOf("income" to 1.0, "expenses" to -1.0).forEach { (key, sign) ->
            val entries = readArray(key)
            for (i in 0 until entries.length()) {
                val item = entries.optJSONObject(i) ?: continue
                val d = item.optString("date")
                if (d >= start && d <= date) netByDate[d] = (netByDate[d] ?: 0.0) + sign * transactionAmount(item)
            }
        }
        var running = totalBeforeDate(start)
        val result = mutableListOf<Pair<String, Double>>()
        val day = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
        while (dateOf(day) <= date) {
            val d = dateOf(day)
            running += netByDate[d] ?: 0.0
            result.add(d.substring(8, 10) to running)
            day.add(Calendar.DAY_OF_MONTH, 1)
        }
        return result
    }
    private fun totalBeforeDate(before: String): Double {
        var total = 0.0
        listOf("income" to 1.0, "expenses" to -1.0).forEach { (key, sign) ->
            val entries = readArray(key)
            for (i in 0 until entries.length()) {
                val item = entries.optJSONObject(i) ?: continue
                if (item.optString("date") < before) total += sign * transactionAmount(item)
            }
        }
        return total
    }
    private fun workoutDirection(): Pair<String, String> {
        val a = readArray("workouts"); val cal = Calendar.getInstance(); val current = cal.timeInMillis; val week = 7L * 24L * 60L * 60L * 1000L
        var recent = 0; var previous = 0
        for (i in 0 until a.length()) { val o = a.optJSONObject(i) ?: continue; val d = parseDate(o.optString("date")) ?: continue; val age = current - d.time; val count = o.optInt("push") + o.optInt("pull"); if (age in 0..week) recent += count else if (age in week..(2 * week)) previous += count }
        return when { recent > previous -> "▲ MELHORANDO" to "Mais repetições nos últimos 7 dias"; recent < previous -> "▼ PIORANDO" to "Menos repetições nos últimos 7 dias"; else -> "◆ ESTÁVEL" to "Sem variação clara nos últimos 7 dias" }
    }
    private fun lastSevenDaysNet(): List<Pair<String, Double>> {
        val totals = linkedMapOf<String, Double>(); val calendar = Calendar.getInstance(); calendar.add(Calendar.DAY_OF_YEAR, -6)
        repeat(7) { totals[dateOf(calendar)] = 0.0; calendar.add(Calendar.DAY_OF_YEAR, 1) }
        listOf("income" to 1.0, "expenses" to -1.0).forEach { (key, sign) -> val entries = readArray(key); for (i in 0 until entries.length()) { val item = entries.optJSONObject(i) ?: continue; val d = item.optString("date"); if (totals.containsKey(d)) totals[d] = (totals[d] ?: 0.0) + sign * transactionAmount(item) } }
        return totals.map { it.key.takeLast(5) to it.value }
    }
    private fun money(v: Double) = String.format(Locale("pt", "BR"), "%,.2f", v)
    private fun toast(s: String) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show() }
    private fun dateOf(cal: Calendar) = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    private fun parseDate(raw: String): Date? = try { SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(raw) } catch (_: Exception) { null }
    private fun validDate(raw: String) = parseDate(raw) != null
    private fun validTime(raw: String): Boolean = Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d$").matches(raw)
    private fun weekdayNamesFromValue(value: Int): String = when (value) {
        Calendar.MONDAY -> "Segunda-feira"; Calendar.TUESDAY -> "Terça-feira"; Calendar.WEDNESDAY -> "Quarta-feira"
        Calendar.THURSDAY -> "Quinta-feira"; Calendar.FRIDAY -> "Sexta-feira"; Calendar.SATURDAY -> "Sábado"
        Calendar.SUNDAY -> "Domingo"; else -> "Segunda-feira"
    }
    private fun weekStartDate(): String { val c = Calendar.getInstance(); c.firstDayOfWeek = Calendar.MONDAY; c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY); return dateOf(c) }
}

private class ProgressChart(context: Context, private val points: List<Triple<String, Int, Int>>) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas); val w = width.toFloat(); val h = height.toFloat(); val left = 30f; val right = w - 12f; val top = 22f; val bottom = h - 32f
        paint.color = GRID; paint.strokeWidth = 1f; for (i in 0..3) { val y = top + i * (bottom - top) / 3f; canvas.drawLine(left, y, right, y, paint) }
        paint.textSize = 21f; paint.color = AQUA; canvas.drawText("— Flexões", left, 15f, paint); paint.color = MUTED; canvas.drawText("— Barras", left + 125f, 15f, paint)
        if (points.isEmpty()) return
        val maxValue = points.maxOf { max(it.second, it.third) }.coerceAtLeast(1).toFloat(); val slot = (right - left) / points.size.coerceAtLeast(1)
        fun drawSeries(value: (Triple<String, Int, Int>) -> Int, color: Int) {
            paint.color = color; paint.strokeWidth = 4f; paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND
            val path = Path(); points.forEachIndexed { index, point -> val x = left + slot * (index + .5f); val y = bottom - (value(point) / maxValue) * (bottom - top); if (index == 0) path.moveTo(x, y) else path.lineTo(x, y); paint.style = Paint.Style.FILL; canvas.drawCircle(x, y, 4f, paint); paint.style = Paint.Style.STROKE }; canvas.drawPath(path, paint); paint.style = Paint.Style.FILL
        }
        drawSeries({ it.second }, AQUA); drawSeries({ it.third }, MUTED)
        paint.color = MUTED; paint.textSize = 18f; points.forEachIndexed { index, p -> canvas.drawText(p.first, left + slot * (index + .5f) - 18f, h - 4f, paint) }
    }
}

private class IncomeChart(context: Context, private val points: List<Pair<String, Double>>) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.isEmpty()) return
        val w = width.toFloat(); val h = height.toFloat()
        val left = 42f; val right = w - 12f; val top = 20f; val bottom = h - 24f
        val values = points.map { it.second }
        val minValue = values.minOrNull() ?: 0.0
        val maxValue = values.maxOrNull() ?: 1.0
        val range = (maxValue - minValue).coerceAtLeast(1.0)
        val up = (values.lastOrNull() ?: 0.0) >= (values.firstOrNull() ?: 0.0)
        val lineColor = if (up) AQUA else DANGER
        fun y(value: Double) = bottom - (((value - minValue) / range).toFloat() * (bottom - top))
        paint.color = GRID; paint.strokeWidth = 1f
        for (i in 0..3) {
            val yy = top + i * (bottom - top) / 3f
            canvas.drawLine(left, yy, right, yy, paint)
        }
        val slot = if (points.size <= 1) 0f else (right - left) / (points.size - 1).toFloat()
        val line = Path()
        points.forEachIndexed { index, point ->
            val x = if (points.size == 1) (left + right) / 2f else left + slot * index
            val yy = y(point.second)
            if (index == 0) line.moveTo(x, yy) else line.lineTo(x, yy)
        }
        val firstX = if (points.size == 1) (left + right) / 2f else left
        val lastX = if (points.size == 1) (left + right) / 2f else right
        val fill = Path().apply { addPath(line); lineTo(lastX, bottom); lineTo(firstX, bottom); close() }
        paint.color = (lineColor and 0x00FFFFFF) or (0x30 shl 24); paint.style = Paint.Style.FILL
        canvas.drawPath(fill, paint)
        paint.color = lineColor; paint.strokeWidth = 3.5f; paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
        canvas.drawPath(line, paint); paint.style = Paint.Style.FILL
        points.forEachIndexed { index, point ->
            if (index == points.lastIndex || index == 0) {
                val x = if (points.size == 1) (left + right) / 2f else left + slot * index
                paint.color = lineColor; canvas.drawCircle(x, y(point.second), if (index == points.lastIndex) 5f else 3f, paint)
            }
        }
        paint.color = MUTED; paint.textSize = 10f
        val firstLabel = points.first().first
        val lastLabel = points.last().first
        canvas.drawText(firstLabel, left, h - 5f, paint)
        val labelWidth = paint.measureText(lastLabel)
        canvas.drawText(lastLabel, right - labelWidth, h - 5f, paint)
        paint.color = lineColor; paint.textSize = 11f
        val finalValue = values.last()
        val formatted = "R$ " + String.format(Locale("pt", "BR"), "%,.0f", finalValue)
        canvas.drawText(formatted, left, 13f, paint)
    }
}
