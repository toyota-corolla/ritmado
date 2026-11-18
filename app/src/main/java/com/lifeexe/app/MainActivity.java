package com.lifeexe.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.lifeexe.app.data.DatabaseHelper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** LIFE.EXE — native Android personal execution dashboard. */
public class MainActivity extends Activity {
    private static final int GREEN = Color.rgb(0, 255, 136);
    private static final int GREEN_DARK = Color.rgb(0, 201, 107);
    private static final int BG = Color.rgb(2, 8, 5);
    private static final int PANEL = Color.rgb(7, 26, 18);
    private static final int PANEL_ALT = Color.rgb(10, 34, 24);
    private static final int TEXT = Color.rgb(205, 235, 218);
    private static final int MUTED = Color.rgb(115, 151, 130);
    private static final int RED = Color.rgb(255, 93, 93);
    private static final int YELLOW = Color.rgb(255, 207, 86);

    private DatabaseHelper db;
    private LinearLayout root, body, header, bottomNav;
    private String tab = "home";
    private String section = "";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int focusDuration = 25;
    private int focusRemaining = 25 * 60;
    private boolean focusRunning = false;
    private long focusStartedAt = 0;
    private String focusTask = "Sessão de foco";
    private String focusStartTime = DatabaseHelper.now();
    private Runnable timerRunnable;
    private TextView timerText;
    private ProgressBar timerProgress;
    private int fileAction = 0; // 1 export, 2 import

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        db = new DatabaseHelper(this);
        buildShell();
        render();
    }

    @Override protected void onDestroy() {
        if (handler != null && timerRunnable != null) handler.removeCallbacks(timerRunnable);
        super.onDestroy();
    }

    private void buildShell() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG);
        header = new LinearLayout(this); header.setOrientation(LinearLayout.VERTICAL); header.setPadding(dp(18), dp(15), dp(18), dp(12));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(false); scroll.setClipToPadding(false);
        body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(16), dp(4), dp(16), dp(24));
        scroll.addView(body); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        bottomNav = new LinearLayout(this); bottomNav.setOrientation(LinearLayout.HORIZONTAL); bottomNav.setGravity(Gravity.CENTER); bottomNav.setPadding(dp(5), dp(8), dp(5), dp(8));
        bottomNav.setBackgroundColor(Color.rgb(4, 15, 10)); root.addView(bottomNav, new LinearLayout.LayoutParams(-1, dp(64)));
        setContentView(root);
    }

    private void render() {
        renderHeader(); body.removeAllViews(); bottomNav.removeAllViews();
        if (!section.isEmpty()) renderSection();
        else if ("home".equals(tab)) renderHome();
        else if ("stats".equals(tab)) renderStats();
        else if ("awards".equals(tab)) renderAwards();
        else renderSettings();
        renderBottomNav();
    }

    private void renderHeader() {
        header.removeAllViews();
        TextView logo = text("LIFE.EXE", 20, GREEN, true); logo.setLetterSpacing(0.13f); header.addView(logo);
        LinearLayout line = new LinearLayout(this); line.setOrientation(LinearLayout.HORIZONTAL); line.setGravity(Gravity.CENTER_VERTICAL);
        TextView protocol = text("DISCIPLINE PROTOCOL  //  ONLINE", 10, MUTED, true); protocol.setLetterSpacing(0.08f);
        line.addView(protocol, new LinearLayout.LayoutParams(0, -2, 1));
        TextView xp = text("LVL " + level() + "  •  " + db.getXpTotal() + " XP", 10, GREEN, true); line.addView(xp);
        header.addView(line);
        View divider = new View(this); divider.setBackgroundColor(Color.rgb(20, 75, 49)); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(1)); p.topMargin = dp(11); header.addView(divider, p);
    }

    private void renderBottomNav() {
        String[] labels = {"INÍCIO", "DADOS", "+", "RANK", "SISTEMA"};
        String[] keys = {"home", "stats", "add", "awards", "settings"};
        for (int i = 0; i < labels.length; i++) {
            final String key = keys[i];
            TextView b = text(labels[i], i == 2 ? 23 : 9, i == 2 ? BG : (tab.equals(key) ? GREEN : MUTED), true);
            b.setGravity(Gravity.CENTER); b.setLetterSpacing(i == 2 ? 0 : 0.04f);
            if (i == 2) { b.setBackground(round(GREEN, GREEN, 40)); }
            else if (tab.equals(key)) b.setBackground(round(PANEL, GREEN_DARK, 9));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(46), i == 2 ? 0.8f : 1f); p.setMargins(dp(3), 0, dp(3), 0);
            bottomNav.addView(b, p);
            b.setOnClickListener(v -> { if ("add".equals(key)) quickAdd(); else { section = ""; tab = key; render(); } });
        }
    }

    private void renderHome() {
        sectionTitle("PAINEL DE CONTROLE", "Seu estado operacional de hoje.");
        addText("FINANÇAS  /  CAPITAL", 11, GREEN, true, 10, 7);
        LinearLayout finance = row();
        double income = db.sumAmount("Receita", null), expense = db.sumAmount("Despesa", null), balance = income - expense;
        double todayIn = db.sumAmount("Receita", DatabaseHelper.today());
        double todayOut = db.sumAmount("Despesa", DatabaseHelper.today());
        finance.addView(metricCard("SALDO TOTAL", money(balance), "Receitas " + money(income) + "  •  Despesas " + money(expense), GREEN), weighted());
        finance.addView(metricCard("SALDO DE HOJE", money(todayIn - todayOut), "+ " + money(todayIn) + "  /  − " + money(todayOut), todayIn - todayOut >= 0 ? GREEN : RED), weighted());
        body.addView(finance);

        addText("DESEMPENHO FÍSICO  /  OUTPUT", 11, GREEN, true, 15, 7);
        LinearLayout fitness = row();
        int push = db.sumWorkout("Flexões", null), pull = db.sumWorkout("Barras", null);
        fitness.addView(metricCard("FLEXÕES", String.valueOf(push), "Total registrado", GREEN), weighted());
        fitness.addView(metricCard("BARRAS", String.valueOf(pull), "Total registrado", GREEN), weighted());
        body.addView(fitness);
        int weekPush = db.sumWorkout("Flexões", weekStart()), weekPull = db.sumWorkout("Barras", weekStart());
        addText("SEMANA ATUAL  •  FLEXÕES " + weekPush + "  /  BARRAS " + weekPull, 10, MUTED, true, 4, 5);

        sectionHeader("PROJETO LIBERDADE", "Objetivos em movimento", "OBJETIVOS", () -> openSection("goals"));
        ArrayList<Map<String,String>> goals = db.getRecords("goal");
        if (goals.isEmpty()) emptyHint("Nenhum objetivo cadastrado. Defina o primeiro marco do seu Projeto Liberdade.");
        else for (int i = 0; i < Math.min(2, goals.size()); i++) addGoalCard(goals.get(i));

        sectionHeader("MISSION CONTROL", "Até três missões prioritárias", "VER TODAS", () -> openSection("missions"));
        ArrayList<Map<String,String>> missions = db.getRecords("mission");
        sortPriority(missions);
        int shown = 0;
        for (Map<String,String> m : missions) if (!"done".equals(m.get("status")) && shown < 3) { addCompactRecord(m, "mission"); shown++; }
        if (shown == 0) emptyHint("Sem missões abertas. Crie até três prioridades para hoje.");

        sectionHeader("TO-DO", "Próximas tarefas", "ABRIR LISTA", () -> openSection("tasks"));
        ArrayList<Map<String,String>> tasks = db.getRecords("task"); int n = 0;
        for (Map<String,String> task : tasks) if (!"done".equals(task.get("status")) && n < 3) { addCompactRecord(task, "task"); n++; }
        if (n == 0) emptyHint("Tudo limpo por aqui. Adicione tarefas quando necessário.");

        addText("DISCIPLINE PROTOCOL", 11, GREEN, true, 15, 7);
        LinearLayout discipline = row();
        discipline.addView(metricCard("HÁBITOS HOJE", habitCompletion() + "/" + db.countType("habit"), "Registros de hoje", GREEN), weighted());
        discipline.addView(metricCard("FOCO TOTAL", db.focusMinutes() + " min", db.focusSessions() + " sessões concluídas", GREEN), weighted());
        body.addView(discipline);

        addText("ACESSO RÁPIDO", 11, GREEN, true, 16, 7);
        shortcutGrid();
    }

    private void shortcutGrid() {
        String[][] actions = {{"HÁBITOS", "habits"}, {"FOCUS MODE", "focus"}, {"FINANÇAS", "finance"}, {"ANTI-PROCRASTINAÇÃO", "anti"}, {"TREINO", "workout"}, {"MISSÕES", "missions"}};
        for (int i = 0; i < actions.length; i += 2) {
            LinearLayout r = row();
            for (int j = i; j < Math.min(i + 2, actions.length); j++) {
                final String dest = actions[j][1]; TextView b = button(actions[j][0]); b.setTextSize(11); b.setOnClickListener(v -> openSection(dest));
                LinearLayout.LayoutParams p = weighted(); p.setMargins(dp(3), dp(3), dp(3), dp(3)); r.addView(b, p);
            }
            body.addView(r);
        }
    }

    private void renderStats() {
        sectionTitle("ANALYTICS", "Indicadores calculados somente com seus registros.");
        int missions = db.countType("mission"), missionsDone = db.count("mission", "done");
        int tasks = db.countType("task"), tasksDone = db.count("task", "done");
        int habits = db.countType("habit"), logs = db.countType("habit_log");
        int avoided = db.countType("avoidance"), difficultStarted = countDifficultyStarted();
        int difficultPlanned = countDifficultPlanned();
        double rate = missions == 0 ? 0 : (100.0 * missionsDone / missions);
        double facing = difficultPlanned == 0 ? 0 : (100.0 * difficultStarted / difficultPlanned);
        addAnalyticsCard("EXECUÇÃO", "Missões concluídas", missionsDone + " / " + missions, rate, GREEN);
        addAnalyticsCard("EXECUÇÃO", "Tarefas concluídas", tasksDone + " / " + tasks, tasks == 0 ? 0 : 100.0 * tasksDone / tasks, GREEN);
        addAnalyticsCard("DISCIPLINA", "Hábitos registrados", String.valueOf(logs), habits == 0 ? 0 : Math.min(100, 100.0 * logs / Math.max(1, habits * 7)), GREEN);
        addAnalyticsCard("FOCO", "Tempo efetivo", db.focusMinutes() + " minutos em " + db.focusSessions() + " sessões", Math.min(100, db.focusMinutes() / 3.0), GREEN);
        addAnalyticsCard("CONFRONTO", "Taxa de enfrentamento", String.format(Locale.getDefault(), "%.0f%%", facing), facing, YELLOW);
        addAnalyticsCard("CONFRONTO", "Tarefas evitadas registradas", String.valueOf(avoided), 0, YELLOW);
        addAnalyticsCard("CORPO", "Flexões / Barras", db.sumWorkout("Flexões", null) + " / " + db.sumWorkout("Barras", null), 0, GREEN);
        double in = db.sumAmount("Receita", null), out = db.sumAmount("Despesa", null);
        addAnalyticsCard("FINANÇAS", "Receitas / Despesas", money(in) + " / " + money(out), 0, GREEN);
        addAnalyticsCard("FINANÇAS", "Saldo acumulado", money(in - out), 0, (in - out) >= 0 ? GREEN : RED);
        addText("LEITURA OPERACIONAL", 11, GREEN, true, 16, 7);
        panel("SEM DADOS INVENTADOS", "Os gráficos e percentuais só aparecem quando existem registros suficientes. A taxa de enfrentamento usa tarefas difíceis iniciadas ÷ tarefas difíceis planejadas × 100.");
        addText("RESUMO SEMANAL", 11, GREEN, true, 16, 7);
        panel("ÚLTIMOS 7 DIAS", "Missões: " + db.countType("mission") + " cadastradas · Hábitos: " + db.getRecords("habit_log").size() + " registros · Foco: " + db.focusMinutes() + " min · Treino: " + (db.sumWorkout("Flexões", weekStart()) + db.sumWorkout("Barras", weekStart())) + " repetições.");
        addAction("GERAR RELATÓRIO SEMANAL", this::showWeeklyReport);
    }

    private void renderAwards() {
        sectionTitle("CENTRAL DE CONQUISTAS", "XP recompensa execução, não substitui resultados.");
        int xp = db.getXpTotal();
        int level = level(); int into = Math.max(0, xp) % 500;
        panel("NÍVEL " + level + "  //  " + xp + " XP", "Próximo nível em " + Math.max(0, 500 - into) + " XP.");
        ProgressBar p = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); p.setMax(500); p.setProgress(into); p.setProgressTintList(android.content.res.ColorStateList.valueOf(GREEN)); p.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(18, 48, 33))); body.addView(p, new LinearLayout.LayoutParams(-1, dp(8)));
        addText("MEDALHAS", 11, GREEN, true, 16, 7);
        achievement("PRIMEIRO PASSO", "Conclua sua primeira missão", db.count("mission", "done") >= 1);
        achievement("MODO PROFUNDO", "Acumule 60 minutos de foco", db.focusMinutes() >= 60);
        achievement("CONSISTÊNCIA", "Registre 7 execuções de hábitos", db.countType("habit_log") >= 7);
        achievement("MÃO NA MASSA", "Conclua 10 missões", db.count("mission", "done") >= 10);
        achievement("MIL XP", "Alcance 1.000 XP", xp >= 1000);
        addText("HISTÓRICO DE XP", 11, GREEN, true, 16, 7);
        for (Map<String,String> item : db.getXpHistory()) panel(item.get("reason"), signed(item.get("points")) + " XP  •  " + item.get("created_at"));
        if (db.getXpHistory().isEmpty()) emptyHint("Nenhum XP registrado ainda. Complete missões, hábitos e sessões de foco.");
    }

    private void renderSettings() {
        sectionTitle("CONFIGURAÇÕES", "Controle local dos seus dados e preferências.");
        panel("ARMAZENAMENTO LOCAL", "Seus registros ficam no dispositivo. Nenhuma conta ou servidor externo é necessário.");
        addAction("EXPORTAR BACKUP JSON", () -> { fileAction = 1; Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("application/json"); i.putExtra(Intent.EXTRA_TITLE, "life-exe-backup.json"); startActivityForResult(i, 8101); });
        addAction("RESTAURAR BACKUP JSON", () -> { fileAction = 2; Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("application/json"); startActivityForResult(i, 8102); });
        addText("SOBRE", 11, GREEN, true, 16, 7);
        panel("LIFE.EXE  •  V2.0", "Android nativo · Armazenamento SQLite · Interface Cyber Core · Build Debug/Release via GitHub Actions.");
        panel("DADOS", "Registros salvos: " + db.getAllRecords().size() + " · XP total: " + db.getXpTotal() + " · Sessões de foco: " + db.focusSessions());
    }

    private void renderSection() {
        addAction("← VOLTAR AO PAINEL", () -> { section = ""; render(); });
        switch (section) {
            case "missions": renderRecordSection("MISSION CONTROL", "mission", "missão"); break;
            case "tasks": renderRecordSection("TO-DO LIST", "task", "tarefa"); break;
            case "habits": renderHabits(); break;
            case "focus": renderFocus(); break;
            case "finance": renderFinance(); break;
            case "workout": renderWorkout(); break;
            case "goals": renderGoals(); break;
            case "anti": renderAnti(); break;
            default: panel("MÓDULO NÃO ENCONTRADO", "Este módulo não está disponível."); break;
        }
    }

    private void renderRecordSection(String title, String type, String singular) {
        sectionTitle(title, "Registre, conclua, reprograme e revise seu histórico.");
        addAction("+ NOVA " + singular.toUpperCase(Locale.ROOT), () -> showRecordForm(type, null));
        ArrayList<Map<String,String>> rows = db.getRecords(type);
        if ("mission".equals(type)) sortPriority(rows);
        if (rows.isEmpty()) emptyHint("Nenhum registro ainda. Use o botão acima para criar o primeiro.");
        for (Map<String,String> item : rows) addFullRecord(item, type);
    }

    private void renderHabits() {
        sectionTitle("HABIT SYSTEM", "Meta mínima mantém o vínculo. Um dia perdido não apaga seu histórico.");
        addAction("+ NOVO HÁBITO", () -> showRecordForm("habit", null));
        ArrayList<Map<String,String>> habits = db.getRecords("habit");
        if (habits.isEmpty()) emptyHint("Cadastre um hábito com meta normal e mínima.");
        for (Map<String,String> h : habits) {
            String name = h.get("title"); int done = db.habitDoneToday(name); int streak = db.habitStreak(name);
            LinearLayout card = card();
            addTo(card, name, 15, GREEN, true); addTo(card, h.get("category") + "  •  " + h.get("frequency"), 10, MUTED, false);
            addTo(card, "META " + h.get("normal_goal") + "  /  MÍNIMA " + h.get("min_goal"), 11, TEXT, true);
            addTo(card, "Hoje: " + done + " registro(s)  •  Histórico: " + streak + " dias registrados", 10, MUTED, false);
            LinearLayout actions = row();
            TextView normal = button("META NORMAL"); normal.setOnClickListener(v -> logHabit(name, "normal", parseDouble(h.get("normal_goal")), 25)); actions.addView(normal, weighted());
            TextView minimum = button("META MÍNIMA"); minimum.setOnClickListener(v -> logHabit(name, "mínima", parseDouble(h.get("min_goal")), 15)); actions.addView(minimum, weighted());
            TextView edit = button("EDITAR"); edit.setOnClickListener(v -> showRecordForm("habit", h)); actions.addView(edit, weighted());
            TextView del = button("EXCLUIR"); del.setOnClickListener(v -> confirmDelete(h)); actions.addView(del, weighted());
            card.addView(actions); body.addView(card);
        }
        panel("RETOMADA", "A sequência exibida é um histórico de dias com execução; não há punição automática nem perda de progresso por faltar um dia.");
    }

    private void renderFocus() {
        sectionTitle("FOCUS MODE", "Uma tarefa. Uma sessão. Sem distrações.");
        LinearLayout timerCard = card(); timerCard.setGravity(Gravity.CENTER);
        timerText = text(formatTime(focusRemaining), 48, GREEN, true); timerText.setGravity(Gravity.CENTER); timerCard.addView(timerText);
        TextView task = text(focusTask, 12, TEXT, true); task.setGravity(Gravity.CENTER); timerCard.addView(task);
        timerProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); timerProgress.setMax(Math.max(1, focusDuration * 60)); timerProgress.setProgress(Math.max(0, focusDuration * 60 - focusRemaining)); timerProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(GREEN)); timerCard.addView(timerProgress, new LinearLayout.LayoutParams(-1, dp(8)));
        body.addView(timerCard);
        addText("DURAÇÃO", 11, GREEN, true, 12, 6);
        LinearLayout durations = row();
        for (int m : new int[]{5,25,45,60}) { TextView b = button(m + " MIN"); b.setOnClickListener(v -> { if (!focusRunning) { focusDuration=m; focusRemaining=m*60; render(); } }); durations.addView(b, weighted()); }
        body.addView(durations);
        addAction(focusRunning ? "PAUSAR SESSÃO" : "INICIAR / RETOMAR", () -> { if (focusRunning) pauseFocus(); else startFocus(); });
        addAction("CANCELAR E REINICIAR", () -> { pauseFocus(); focusRemaining=focusDuration*60; render(); });
        addAction("DEFINIR TAREFA DA SESSÃO", this::chooseFocusTask);
        addText("HISTÓRICO", 11, GREEN, true, 16, 7);
        panel("TOTAL ACUMULADO", db.focusMinutes() + " minutos em " + db.focusSessions() + " sessões concluídas.");
    }

    private void renderFinance() {
        sectionTitle("FINANCE CORE", "Receitas e despesas registradas manualmente.");
        double income = db.sumAmount("Receita", null), expense = db.sumAmount("Despesa", null);
        LinearLayout totals = row(); totals.addView(metricCard("RECEITAS", money(income), "Acumulado", GREEN), weighted()); totals.addView(metricCard("DESPESAS", money(expense), "Acumulado", RED), weighted()); body.addView(totals);
        panel("SALDO LÍQUIDO", money(income - expense));
        addAction("+ REGISTRAR RECEITA", () -> showFinanceForm("Receita", null));
        addAction("+ REGISTRAR DESPESA", () -> showFinanceForm("Despesa", null));
        addText("LANÇAMENTOS", 11, GREEN, true, 16, 7);
        for (Map<String,String> item : db.getRecords("finance")) addFullRecord(item, "finance");
    }

    private void renderWorkout() {
        sectionTitle("BODY OUTPUT", "Registre repetições reais e acompanhe o volume.");
        int push = db.sumWorkout("Flexões", null), pull = db.sumWorkout("Barras", null);
        LinearLayout r = row(); r.addView(metricCard("FLEXÕES", String.valueOf(push), "Total", GREEN), weighted()); r.addView(metricCard("BARRAS", String.valueOf(pull), "Total", GREEN), weighted()); body.addView(r);
        addAction("+ REGISTRAR FLEXÕES", () -> showWorkoutForm("Flexões"));
        addAction("+ REGISTRAR BARRAS", () -> showWorkoutForm("Barras"));
        addText("HISTÓRICO DE TREINO", 11, GREEN, true, 16, 7);
        for (Map<String,String> item : db.getRecords("workout")) addFullRecord(item, "workout");
    }

    private void renderGoals() {
        sectionTitle("PROJETO LIBERDADE", "Transforme visão em etapas mensuráveis.");
        addAction("+ NOVO OBJETIVO", () -> showRecordForm("goal", null));
        ArrayList<Map<String,String>> goals = db.getRecords("goal");
        if (goals.isEmpty()) emptyHint("Exemplo: aumentar renda mensal, viajar, desenvolver um negócio ou alcançar independência financeira.");
        for (Map<String,String> g : goals) addGoalCard(g);
    }

    private void renderAnti() {
        sectionTitle("ANTI-PROCRASTINATION", "Identifique a barreira. Escolha uma primeira ação concreta.");
        addAction("INICIAR PROTOCOLO DE CONFRONTO", this::showAvoidanceForm);
        ArrayList<Map<String,String>> entries = db.getRecords("avoidance");
        Map<String,Integer> reasons = new HashMap<>();
        int totalRes = 0;
        for (Map<String,String> e : entries) { String reason=e.get("reason"); reasons.put(reason, reasons.containsKey(reason)?reasons.get(reason)+1:1); totalRes += parseInt(e.get("resistance")); }
        panel("REGISTROS DE EVITAÇÃO", entries.size() + " evento(s)  •  Resistência média: " + (entries.isEmpty()?"—":String.format(Locale.getDefault(),"%.1f",(double)totalRes/entries.size()) + "/10"));
        addText("PADRÕES OBSERVADOS", 11, GREEN, true, 16, 7);
        if (reasons.isEmpty()) emptyHint("Ainda não há dados suficientes para identificar padrões.");
        else for (String k : reasons.keySet()) panel(k, reasons.get(k) + " ocorrência(s)");
        addText("HISTÓRICO", 11, GREEN, true, 16, 7);
        for (Map<String,String> e : entries) {
            LinearLayout c = card(); addTo(c,e.get("title"),14,GREEN,true); addTo(c,"Motivo: " + e.get("reason") + "  •  Resistência " + e.get("resistance") + "/10",10,MUTED,false); addTo(c,"Primeira ação: " + e.get("first_action"),11,TEXT,false); addTo(c,e.get("created_at"),10,MUTED,false);
            LinearLayout actions=row(); TextView edit=button("EDITAR"); edit.setOnClickListener(v -> showAvoidanceEditForm(e)); actions.addView(edit,weighted()); TextView del=button("EXCLUIR"); del.setOnClickListener(v -> confirmDelete(e)); actions.addView(del,weighted()); c.addView(actions); body.addView(c);
        }
    }

    private void addGoalCard(Map<String,String> g) {
        LinearLayout c = card(); String name=g.get("title"); double target=parseDouble(g.get("normal_goal")); double progress=parseDouble(g.get("quantity"));
        double pct=target<=0?0:Math.min(100,progress/target*100);
        addTo(c,name,14,GREEN,true); addTo(c,g.get("description"),10,MUTED,false);
        addTo(c,String.format(Locale.getDefault(),"%.0f%%  •  %s / %s",pct,g.get("quantity"),g.get("normal_goal")),11,TEXT,true);
        ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); bar.setMax(100); bar.setProgress((int)pct); bar.setProgressTintList(android.content.res.ColorStateList.valueOf(GREEN)); c.addView(bar,new LinearLayout.LayoutParams(-1,dp(7)));
        LinearLayout actions=row(); TextView advance=button("+ AVANÇO"); advance.setOnClickListener(v -> showGoalProgress(g)); actions.addView(advance,weighted()); TextView edit=button("EDITAR"); edit.setOnClickListener(v -> showRecordForm("goal",g)); actions.addView(edit,weighted()); TextView del=button("EXCLUIR"); del.setOnClickListener(v -> confirmDelete(g)); actions.addView(del,weighted()); c.addView(actions); body.addView(c);
    }

    private void addCompactRecord(Map<String,String> m, String type) {
        LinearLayout c = card();
        addTo(c,m.get("title"),14,GREEN,true);
        String meta = m.get("category") + "  •  " + m.get("priority") + (empty(m.get("due_at"))?"":"  •  " + m.get("due_at"));
        addTo(c,meta,10,MUTED,false);
        TextView done = button("CONCLUIR  +XP"); done.setOnClickListener(v -> completeRecord(m,type)); c.addView(done);
        body.addView(c);
    }

    private void addFullRecord(Map<String,String> m, String type) {
        LinearLayout c = card();
        addTo(c,m.get("title"),14,GREEN,true);
        String desc = m.get("description"); if (!empty(desc)) addTo(c,desc,11,TEXT,false);
        String meta = m.get("category") + "  •  " + m.get("priority") + "  •  " + m.get("status");
        if (!empty(m.get("due_at"))) meta += "  •  " + m.get("due_at");
        addTo(c,meta,10,MUTED,false);
        if ("finance".equals(type)) addTo(c,("Receita".equals(m.get("category"))?"+ ":"− ") + money(parseDouble(m.get("amount"))),12,"Receita".equals(m.get("category"))?GREEN:RED,true);
        if ("workout".equals(type)) addTo(c,m.get("quantity") + " repetições",12,TEXT,true);
        LinearLayout actions = row();
        if (!"done".equals(m.get("status")) && ("mission".equals(type) || "task".equals(type))) {
            TextView done=button("CONCLUIR"); done.setOnClickListener(v -> completeRecord(m,type)); actions.addView(done,weighted());
            TextView postpone=button("ADIAR"); postpone.setOnClickListener(v -> postponeRecord(m)); actions.addView(postpone,weighted());
        }
        TextView edit=button("EDITAR"); edit.setOnClickListener(v -> { if ("finance".equals(type)) showFinanceForm(m.get("category"),m); else if ("workout".equals(type)) showWorkoutEditForm(m); else showRecordForm(type,m); }); actions.addView(edit,weighted());
        TextView del=button("EXCLUIR"); del.setOnClickListener(v -> confirmDelete(m)); actions.addView(del,weighted());
        c.addView(actions); body.addView(c);
    }

    private void showRecordForm(String type, Map<String,String> old) {
        LinearLayout form = formLayout();
        EditText title = input(form,"Nome / título",old==null?"":old.get("title"),InputType.TYPE_CLASS_TEXT);
        EditText desc = input(form,"Descrição / próxima etapa",old==null?"":old.get("description"),InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        EditText category = input(form,"Categoria",old==null?defaultCategory(type):old.get("category"),InputType.TYPE_CLASS_TEXT);
        EditText due = input(form,"Prazo (ex.: 2026-11-20 18:00)",old==null?"":old.get("due_at"),InputType.TYPE_CLASS_TEXT);
        EditText goal = null, min = null;
        if ("habit".equals(type)) { goal=input(form,"Meta normal",old==null?"30":old.get("normal_goal"),InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL); min=input(form,"Meta mínima",old==null?"5":old.get("min_goal"),InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL); }
        if ("goal".equals(type)) { goal=input(form,"Meta / alvo numérico (opcional)",old==null?"0":old.get("normal_goal"),InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL); min=input(form,"Progresso atual",old==null?"0":old.get("quantity"),InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL); }
        final EditText goalF=goal, minF=min;
        EditText difficulty=input(form,"Dificuldade (Fácil / Normal / Difícil)",old==null?"Normal":old.get("difficulty"),InputType.TYPE_CLASS_TEXT);
        EditText priority=input(form,"Prioridade (Crítica / Alta / Normal)",old==null?"Normal":old.get("priority"),InputType.TYPE_CLASS_TEXT);
        EditText frequency=input(form,"Frequência",old==null?"Diário":old.get("frequency"),InputType.TYPE_CLASS_TEXT);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle((old==null?"NOVO ":"EDITAR ")+typeLabel(type)).setView(form).setNegativeButton("CANCELAR",null).setPositiveButton("SALVAR",null).create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name=title.getText().toString().trim(); if(name.isEmpty()){ title.setError("Informe um título"); return; }
            Map<String,Object> d=new HashMap<>(); d.put("title",name); d.put("description",desc.getText().toString().trim()); d.put("category",category.getText().toString().trim());
            d.put("due_at",due.getText().toString().trim()); d.put("difficulty",difficulty.getText().toString().trim()); d.put("priority",priority.getText().toString().trim()); d.put("frequency",frequency.getText().toString().trim());
            if(goalF!=null) d.put("normal_goal",parseDouble(goalF.getText().toString())); if(minF!=null) { if("goal".equals(type))d.put("quantity",(int)parseDouble(minF.getText().toString())); else d.put("min_goal",parseDouble(minF.getText().toString())); }
            if(old==null){ d.put("type",type); d.put("status","open"); db.addRecord(d); } else db.updateRecord(parseLong(old.get("id")),d);
            dialog.dismiss(); render(); toast("Registro salvo.");
        })); dialog.show();
    }

    private void showFinanceForm(String kind, Map<String,String> old) {
        LinearLayout form=formLayout(); EditText title=input(form,"Descrição",old==null?"":old.get("title"),InputType.TYPE_CLASS_TEXT);
        EditText amount=input(form,"Valor em R$",old==null?"":old.get("amount"),InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText detail=input(form,"Categoria / observação",old==null?"":old.get("description"),InputType.TYPE_CLASS_TEXT);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("REGISTRO FINANCEIRO · "+kind.toUpperCase(Locale.ROOT)).setView(form).setNegativeButton("CANCELAR",null).setPositiveButton("SALVAR",null).create();
        d.setOnShowListener(v -> d.getButton(-1).setOnClickListener(w->{ if(title.getText().toString().trim().isEmpty()||parseDouble(amount.getText().toString())<=0){amount.setError("Informe valor maior que zero");return;} Map<String,Object> data=new HashMap<>(); data.put("title",title.getText().toString());data.put("amount",parseDouble(amount.getText().toString().replace(',','.')));data.put("category",kind);data.put("description",detail.getText().toString());data.put("type","finance");data.put("status","saved"); if(old==null)db.addRecord(data);else db.updateRecord(parseLong(old.get("id")),data);d.dismiss();render();toast("Lançamento salvo.");})); d.show();
    }

    private void showWorkoutForm(String exercise) {
        LinearLayout f=formLayout(); EditText quantity=input(f,"Quantidade de repetições", "", InputType.TYPE_CLASS_NUMBER); EditText notes=input(f,"Observação (opcional)","",InputType.TYPE_CLASS_TEXT);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("REGISTRAR " + exercise.toUpperCase(Locale.ROOT)).setView(f).setNegativeButton("CANCELAR",null).setPositiveButton("SALVAR",null).create();
        d.setOnShowListener(v -> d.getButton(-1).setOnClickListener(w->{int q=parseInt(quantity.getText().toString());if(q<=0){quantity.setError("Informe uma quantidade válida");return;}Map<String,Object> data=new HashMap<>();data.put("type","workout");data.put("title",exercise);data.put("category",exercise);data.put("quantity",q);data.put("description",notes.getText().toString());data.put("status","saved");db.addRecord(data);d.dismiss();render();toast("Treino registrado.");}));d.show();
    }

    private void showWorkoutEditForm(Map<String,String> old) {
        LinearLayout f=formLayout(); EditText quantity=input(f,"Quantidade de repetições",old.get("quantity"),InputType.TYPE_CLASS_NUMBER); EditText notes=input(f,"Observação (opcional)",old.get("description"),InputType.TYPE_CLASS_TEXT);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("EDITAR TREINO").setView(f).setNegativeButton("CANCELAR",null).setPositiveButton("SALVAR",null).create();
        d.setOnShowListener(v -> d.getButton(-1).setOnClickListener(w->{int q=parseInt(quantity.getText().toString());if(q<=0){quantity.setError("Informe uma quantidade válida");return;}Map<String,Object> data=new HashMap<>();data.put("quantity",q);data.put("description",notes.getText().toString());db.updateRecord(parseLong(old.get("id")),data);d.dismiss();render();toast("Treino atualizado.");}));d.show();
    }

    private void showGoalProgress(Map<String,String> g) {
        EditText amount=new EditText(this);amount.setSingleLine(true);amount.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);amount.setHint("Quanto avançou?");
        new AlertDialog.Builder(this).setTitle("REGISTRAR AVANÇO").setMessage(g.get("title")).setView(amount).setNegativeButton("CANCELAR",null).setPositiveButton("REGISTRAR",(d,w)->{double add=parseDouble(amount.getText().toString());if(add>0){Map<String,Object> m=new HashMap<>();m.put("quantity",(int)(parseDouble(g.get("quantity"))+add));db.updateRecord(parseLong(g.get("id")),m);db.addXp("Avanço no Projeto Liberdade",10,parseLong(g.get("id")));render();toast("Avanço registrado · +10 XP");}}).show();
    }

    private void logHabit(String name, String level, double amount, int xp) {
        Map<String,Object> data=new HashMap<>();data.put("type","habit_log");data.put("title",name);data.put("category",level);data.put("quantity",(int)amount);data.put("status","done");db.addRecord(data);db.addXp("Hábito " + level + ": " + name,xp,0);render();toast("Hábito registrado · +"+xp+" XP");
    }

    private void showAvoidanceEditForm(Map<String,String> old) {
        LinearLayout f=formLayout();
        EditText task=input(f,"Tarefa",old.get("title"),InputType.TYPE_CLASS_TEXT);
        EditText reason=input(f,"Motivo",old.get("reason"),InputType.TYPE_CLASS_TEXT);
        EditText intensity=input(f,"Resistência (1–10)",old.get("resistance"),InputType.TYPE_CLASS_NUMBER);
        EditText action=input(f,"Primeira ação",old.get("first_action"),InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("EDITAR PROTOCOLO").setView(f).setNegativeButton("CANCELAR",null).setPositiveButton("SALVAR",null).create();
        d.setOnShowListener(v->d.getButton(-1).setOnClickListener(w->{if(task.getText().toString().trim().isEmpty()){task.setError("Informe a tarefa");return;}Map<String,Object> x=new HashMap<>();x.put("title",task.getText().toString().trim());x.put("reason",reason.getText().toString());x.put("resistance",Math.max(1,Math.min(10,parseInt(intensity.getText().toString()))));x.put("first_action",action.getText().toString());db.updateRecord(parseLong(old.get("id")),x);d.dismiss();render();toast("Registro atualizado.");}));d.show();
    }

    private void showAvoidanceForm() {
        LinearLayout f=formLayout(); EditText task=input(f,"Tarefa que estou evitando","",InputType.TYPE_CLASS_TEXT);
        EditText reason=input(f,"Motivo", "Não quero sentir o desconforto de começar",InputType.TYPE_CLASS_TEXT);
        EditText intensity=input(f,"Intensidade da resistência (1–10)","5",InputType.TYPE_CLASS_NUMBER);
        EditText action=input(f,"Primeira ação de até 5 minutos","Abrir a tarefa e trabalhar nela por 5 minutos",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        String[] reasons={"Não quero sentir o desconforto de começar","Tenho medo de falhar","Não sei por onde começar","A tarefa é muito grande","Estou cansado","Estou distraído","Estou esperando o momento perfeito"};
        TextView choose=button("ESCOLHER MOTIVO PADRÃO"); choose.setOnClickListener(v->new AlertDialog.Builder(this).setItems(reasons,(a,b)->{reason.setText(reasons[b]);String suggestion;switch(b){case 1:suggestion="Defina um experimento pequeno em que errar seja permitido.";break;case 2:suggestion="Escreva a primeira etapa física e execute somente essa etapa.";break;case 3:suggestion="Quebre a tarefa em uma ação que leve no máximo cinco minutos.";break;case 4:suggestion="Reduza o escopo e faça a versão mínima por cinco minutos.";break;case 5:suggestion="Afaste o celular, feche abas e inicie um bloco de foco.";break;case 6:suggestion="Comece agora por cinco minutos, sem esperar condições perfeitas.";break;default:suggestion="Abra a tarefa e trabalhe nela por cinco minutos.";}action.setText(suggestion);}).show()); f.addView(choose);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("PROTOCOLO DE CONFRONTO").setView(f).setNegativeButton("CANCELAR",null).setPositiveButton("REGISTRAR E INICIAR",null).create();
        d.setOnShowListener(v->d.getButton(-1).setOnClickListener(w->{String t=task.getText().toString().trim();int r=Math.max(1,Math.min(10,parseInt(intensity.getText().toString())));if(t.isEmpty()){task.setError("Informe a tarefa");return;}String act=action.getText().toString().trim();Map<String,Object> x=new HashMap<>();x.put("type","avoidance");x.put("title",t);x.put("reason",reason.getText().toString());x.put("resistance",r);x.put("first_action",act);x.put("description","Protocolo de confronto iniciado");x.put("status","started");long id=db.addRecord(x);db.addXp("Iniciou tarefa difícil: "+t,30,id);d.dismiss();render();toast("Primeiro passo registrado · +30 XP");}));d.show();
    }

    private void postponeRecord(Map<String,String> m) {
        EditText why=new EditText(this);why.setHint("Por que está adiando?");
        new AlertDialog.Builder(this).setTitle("JUSTIFICATIVA DE ADIAMENTO").setMessage(m.get("title")).setView(why).setNegativeButton("VOLTAR",null).setPositiveButton("REPROGRAMAR",(d,w)->{String reason=why.getText().toString().trim();if(reason.isEmpty()){toast("Registre uma justificativa para adiar.");return;}Map<String,Object>x=new HashMap<>();x.put("status","postponed");x.put("description",(m.get("description")+"\nAdiamento: "+reason).trim());db.updateRecord(parseLong(m.get("id")),x);render();toast("Adiamento registrado · 0 XP");}).show();
    }

    private void completeRecord(Map<String,String> m,String type) {
        long id=parseLong(m.get("id"));db.setStatus(id,"done");int points="Crítica".equalsIgnoreCase(m.get("priority"))?100:50;db.addXp(("mission".equals(type)?"Missão":"Tarefa")+" concluída: "+m.get("title"),points,id);render();toast("Execução concluída · +"+points+" XP");
    }

    private void confirmDelete(Map<String,String> m) {
        new AlertDialog.Builder(this).setTitle("EXCLUIR REGISTRO?").setMessage(m.get("title")+"\nEsta ação não pode ser desfeita.").setNegativeButton("CANCELAR",null).setPositiveButton("EXCLUIR",(d,w)->{db.deleteRecord(parseLong(m.get("id")));render();toast("Registro excluído.");}).show();
    }

    private void chooseFocusTask() {
        ArrayList<Map<String,String>> items=db.getRecords("task"); items.addAll(db.getRecords("mission"));
        String[] names=new String[items.size()+1];names[0]="Sessão de foco livre";for(int i=0;i<items.size();i++)names[i+1]=items.get(i).get("title");
        new AlertDialog.Builder(this).setTitle("VINCULAR TAREFA").setItems(names,(d,which)->{focusTask=names[which];if(timerText!=null)((TextView)((LinearLayout)timerText.getParent()).getChildAt(1)).setText(focusTask);}).show();
    }

    private void startFocus() {
        if(focusRemaining<=0)focusRemaining=focusDuration*60;
        if(focusRunning)return;
        focusRunning=true;focusStartedAt=System.currentTimeMillis();if(focusRemaining==focusDuration*60)focusStartTime=DatabaseHelper.now();
        timerRunnable=new Runnable(){@Override public void run(){if(!focusRunning)return;focusRemaining--;if(timerText!=null)timerText.setText(formatTime(focusRemaining));if(timerProgress!=null)timerProgress.setProgress(focusDuration*60-focusRemaining);if(focusRemaining<=0){finishFocus(true);return;}handler.postDelayed(this,1000);}};
        handler.postDelayed(timerRunnable,1000);renderFocusControls();toast("Sessão iniciada.");
    }

    private void pauseFocus() {
        if(timerRunnable!=null)handler.removeCallbacks(timerRunnable);
        if(focusRunning){focusRunning=false;long elapsed=(System.currentTimeMillis()-focusStartedAt)/1000;focusRemaining=Math.max(0,focusRemaining);renderFocusControls();}
    }

    private void finishFocus(boolean completed) {
        if(timerRunnable!=null)handler.removeCallbacks(timerRunnable);focusRunning=false;
        int elapsed=completed?focusDuration:Math.max(1,(int)((focusDuration*60L-focusRemaining)/60L));
        if(elapsed>0){db.addFocusSession(focusTask,elapsed,focusStartTime,completed);if(completed)db.addXp("Sessão de foco concluída",25,0);}
        focusRemaining=completed?0:focusDuration*60;render();if(completed)toast("Sessão concluída · +25 XP");
    }

    private void renderFocusControls() { if(!section.equals("focus"))return; /* Timer state remains in memory while screen is active. */ }

    private void quickAdd() {
        String[] opts={"Registrar receita","Registrar despesa","Registrar treino","Adicionar tarefa","Registrar avanço","Criar missão","Registrar hábito","Iniciar sessão de foco","Protocolo de procrastinação"};
        new AlertDialog.Builder(this).setTitle("AÇÃO RÁPIDA").setItems(opts,(d,w)->{switch(w){case 0:showFinanceForm("Receita",null);break;case 1:showFinanceForm("Despesa",null);break;case 2:showWorkoutForm("Flexões");break;case 3:showRecordForm("task",null);break;case 4:openSection("goals");break;case 5:showRecordForm("mission",null);break;case 6:showRecordForm("habit",null);break;case 7:openSection("focus");break;case 8:openSection("anti");break;}}).show();
    }

    private void showWeeklyReport() {
        String since=weekStart();
        String report="RELATÓRIO SEMANAL LIFE.EXE\n\n"+
                "Missões: "+db.countCompletedSince("mission",since)+" concluídas / "+db.countTypeSince("mission",since)+" criadas\n"+
                "Hábitos: "+db.countTypeSince("habit_log",since)+" registros\n"+
                "Foco: "+db.focusMinutesSince(since)+" minutos\n"+
                "Financeiro: "+money(db.sumAmount("Receita",since)-db.sumAmount("Despesa",since))+" de saldo no período\n"+
                "Físico: "+db.sumWorkout("Flexões",since)+" flexões / "+db.sumWorkout("Barras",since)+" barras\n"+
                "Evitações: "+db.countTypeSince("avoidance",since)+" registros\n"+
                "\nRECOMENDAÇÃO: revise as missões adiadas, escolha uma tarefa difícil para iniciar cedo e mantenha a meta mínima dos hábitos nos dias de baixa energia.";
        new AlertDialog.Builder(this).setTitle("RELATÓRIO SEMANAL").setMessage(report).setPositiveButton("FECHAR",null).setNeutralButton("COMPARTILHAR",(d,w)->{Intent send=new Intent(Intent.ACTION_SEND);send.setType("text/plain");send.putExtra(Intent.EXTRA_TEXT,report);startActivity(Intent.createChooser(send,"Compartilhar relatório"));}).show();
    }

    private void achievement(String title,String desc,boolean unlocked) {
        LinearLayout c=card();addTo(c,(unlocked?"◆  DESBLOQUEADA  /  ":"◇  BLOQUEADA  /  ")+title,12,unlocked?GREEN:MUTED,true);addTo(c,desc,10,TEXT,false);body.addView(c);
    }

    private void addAnalyticsCard(String group,String label,String value,double pct,int color) {
        LinearLayout c=card();addTo(c,group,9,MUTED,true);addTo(c,label,12,TEXT,true);addTo(c,value,18,color,true);
        if(pct>0){ProgressBar p=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);p.setMax(100);p.setProgress((int)Math.max(0,Math.min(100,pct)));p.setProgressTintList(android.content.res.ColorStateList.valueOf(color));c.addView(p,new LinearLayout.LayoutParams(-1,dp(6)));}
        body.addView(c);
    }

    private void sectionHeader(String title,String subtitle,String action,Runnable click) {
        LinearLayout line=row();LinearLayout left=new LinearLayout(this);left.setOrientation(LinearLayout.VERTICAL);left.addView(text(title,11,GREEN,true));left.addView(text(subtitle,10,MUTED,false));line.addView(left,new LinearLayout.LayoutParams(0,-2,1));TextView b=button(action);b.setTextSize(9);b.setOnClickListener(v->click.run());line.addView(b);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(16);p.bottomMargin=dp(7);body.addView(line,p);
    }

    private void sectionTitle(String title,String subtitle) { addText(title,17,GREEN,true,8,4);addText(subtitle,11,MUTED,false,0,12); }
    private void addText(String value,int size,int color,boolean bold,int top,int bottom) { TextView t=text(value,size,color,bold);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);p.bottomMargin=dp(bottom);body.addView(t,p); }
    private void emptyHint(String value) { LinearLayout c=card();addTo(c,value,11,MUTED,false);body.addView(c); }
    private void panel(String title,String value) { LinearLayout c=card();addTo(c,title,12,GREEN,true);addTo(c,value,11,TEXT,false);body.addView(c); }
    private LinearLayout metricCard(String title,String value,String detail,int accent) { LinearLayout c=card();addTo(c,title,9,MUTED,true);addTo(c,value,18,accent,true);addTo(c,detail,9,TEXT,false);return c; }
    private LinearLayout card() { LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(13),dp(12),dp(13),dp(12));c.setBackground(round(PANEL,Color.rgb(17,70,45),11));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(8);body.addView(c,p);body.removeView(c);return c; }
    private void addTo(LinearLayout parent,String value,int size,int color,boolean bold) { TextView t=text(value==null?"":value,size,color,bold);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(4);parent.addView(t,p); }
    private LinearLayout row() { LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);return r; }
    private LinearLayout.LayoutParams weighted() { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(3),0,dp(3),0);return p; }
    private TextView text(String value,int size,int color,boolean bold) { TextView t=new TextView(this);t.setText(value);t.setTextColor(color);t.setTextSize(size);t.setTypeface(Typeface.MONOSPACE,bold?Typeface.BOLD:Typeface.NORMAL);t.setGravity(Gravity.CENTER_VERTICAL);return t; }
    private TextView button(String value) { TextView t=text(value,10,GREEN,true);t.setGravity(Gravity.CENTER);t.setPadding(dp(7),dp(11),dp(7),dp(11));t.setBackground(round(PANEL_ALT,GREEN_DARK,7));return t; }
    private void addAction(String label,Runnable action) { TextView b=button(label);b.setOnClickListener(v->action.run());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(7);body.addView(b,p); }
    private LinearLayout formLayout() { LinearLayout f=new LinearLayout(this);f.setOrientation(LinearLayout.VERTICAL);f.setPadding(dp(18),dp(6),dp(18),dp(6));return f; }
    private EditText input(LinearLayout parent,String hint,String value,int type) { EditText e=new EditText(this);e.setSingleLine((type&InputType.TYPE_TEXT_FLAG_MULTI_LINE)==0);e.setHint(hint);e.setText(value==null?"":value);e.setTextColor(TEXT);e.setHintTextColor(MUTED);e.setTextSize(13);e.setInputType(type);e.setPadding(dp(8),dp(5),dp(8),dp(5));parent.addView(e,new LinearLayout.LayoutParams(-1,-2));return e; }
    private GradientDrawable round(int fill,int stroke,int radius) { GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));d.setStroke(dp(1),stroke);return d; }
    private int dp(float v) { return (int)(v*getResources().getDisplayMetrics().density+0.5f); }
    private void openSection(String s) { section=s;render(); }
    private void toast(String s) { Toast.makeText(this,s,Toast.LENGTH_SHORT).show(); }
    private int level() { return Math.max(1,db.getXpTotal()/500+1); }
    private String money(double v) { return String.format(new Locale("pt","BR"),"R$ %,.2f",v); }
    private String signed(String s) { int n=parseInt(s);return n>0?"+"+n:String.valueOf(n); }
    private String formatTime(int sec) { return String.format(Locale.getDefault(),"%02d:%02d",Math.max(0,sec)/60,Math.max(0,sec)%60); }
    private String typeLabel(String t) { switch(t){case "mission":return "MISSÃO";case "task":return "TAREFA";case "habit":return "HÁBITO";case "goal":return "OBJETIVO";case "avoidance":return "REGISTRO DE EVITAÇÃO";default:return t.toUpperCase(Locale.ROOT);} }
    private String defaultCategory(String t) { switch(t){case "mission":return "Negócio";case "task":return "Pessoal";case "habit":return "Disciplina";case "goal":return "Projeto Liberdade";default:return "Geral";} }
    private boolean empty(String s) { return s==null||s.trim().isEmpty()||"null".equals(s); }
    private int parseInt(String s) { try{return (int)Double.parseDouble(s.replace(',','.'));}catch(Exception e){return 0;} }
    private long parseLong(String s) { try{return Long.parseLong(s);}catch(Exception e){return 0;} }
    private double parseDouble(String s) { try{return Double.parseDouble(s.replace(',','.'));}catch(Exception e){return 0;} }
    private String weekStart() { java.util.Calendar c=java.util.Calendar.getInstance();c.add(java.util.Calendar.DAY_OF_YEAR,-6);return new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US).format(c.getTime()); }
    private int habitCompletion() { int n=0;for(Map<String,String> h:db.getRecords("habit"))n+=db.habitDoneToday(h.get("title"));return n; }
    private int countDifficultyStarted() { int n=0;for(Map<String,String> m:db.getRecords("mission"))if("Difícil".equalsIgnoreCase(m.get("difficulty"))&&"done".equals(m.get("status")))n++;return n; }
    private int countDifficultPlanned() { int n=0;for(Map<String,String> m:db.getRecords("mission"))if("Difícil".equalsIgnoreCase(m.get("difficulty")))n++;return n; }
    private void sortPriority(ArrayList<Map<String,String>> items) { Collections.sort(items,new Comparator<Map<String,String>>(){public int compare(Map<String,String>a,Map<String,String>b){return priorityScore(b.get("priority"))-priorityScore(a.get("priority"));}}); }
    private int priorityScore(String p) { if("Crítica".equalsIgnoreCase(p))return 3;if("Alta".equalsIgnoreCase(p))return 2;return 1; }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();
        try {
            if(requestCode==8101){OutputStream out=getContentResolver().openOutputStream(uri);if(out==null)throw new Exception("Não foi possível abrir o destino.");out.write(db.exportJson().getBytes("UTF-8"));out.close();toast("Backup exportado.");}
            else if(requestCode==8102){BufferedReader r=new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri),"UTF-8"));StringBuilder b=new StringBuilder();String line;while((line=r.readLine())!=null)b.append(line);r.close();final String backup=b.toString();new AlertDialog.Builder(this).setTitle("SUBSTITUIR DADOS ATUAIS?").setMessage("A restauração substitui os registros que estão neste dispositivo. Exporte um backup atual antes de continuar.").setNegativeButton("CANCELAR",null).setPositiveButton("RESTAURAR",(dialog,which)->{try{db.importJson(backup);render();toast("Backup restaurado.");}catch(Exception ex){toast("Falha ao restaurar: "+ex.getMessage());}}).show();}
        } catch(Exception e){toast("Falha no backup: "+e.getMessage());}
    }
}
