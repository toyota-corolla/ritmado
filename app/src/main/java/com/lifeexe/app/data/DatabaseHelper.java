package com.lifeexe.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** Local-first storage for LIFE.EXE. All user-entered metrics are stored as records. */
public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "life_exe.db";
    private static final int DB_VERSION = 1;

    public DatabaseHelper(Context context) { super(context, DB_NAME, null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE records (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "type TEXT NOT NULL, title TEXT NOT NULL, description TEXT DEFAULT ''," +
                "category TEXT DEFAULT '', priority TEXT DEFAULT 'Normal', due_at TEXT DEFAULT ''," +
                "status TEXT DEFAULT 'open', amount REAL DEFAULT 0, quantity INTEGER DEFAULT 0," +
                "normal_goal REAL DEFAULT 0, min_goal REAL DEFAULT 0, frequency TEXT DEFAULT 'Diário'," +
                "difficulty TEXT DEFAULT 'Normal', reason TEXT DEFAULT '', resistance INTEGER DEFAULT 0," +
                "first_action TEXT DEFAULT '', created_at TEXT NOT NULL, updated_at TEXT NOT NULL," +
                "completed_at TEXT DEFAULT '', xp INTEGER DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_records_type ON records(type)");
        db.execSQL("CREATE INDEX idx_records_created ON records(created_at)");
        db.execSQL("CREATE TABLE focus_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "task_title TEXT DEFAULT '', duration_minutes INTEGER NOT NULL," +
                "started_at TEXT NOT NULL, ended_at TEXT DEFAULT '', completed INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE xp_ledger (id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "reason TEXT NOT NULL, points INTEGER NOT NULL, created_at TEXT NOT NULL, ref_id INTEGER DEFAULT 0)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Keep this migration point explicit. Never silently discard a user's records.
    }

    public long addRecord(Map<String, Object> data) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        put(v, data, "type"); put(v, data, "title"); put(v, data, "description");
        put(v, data, "category"); put(v, data, "priority"); put(v, data, "due_at");
        put(v, data, "status"); put(v, data, "amount"); put(v, data, "quantity");
        put(v, data, "normal_goal"); put(v, data, "min_goal"); put(v, data, "frequency");
        put(v, data, "difficulty"); put(v, data, "reason"); put(v, data, "resistance");
        put(v, data, "first_action"); put(v, data, "xp");
        String now = now();
        v.put("created_at", now); v.put("updated_at", now);
        return db.insertOrThrow("records", null, v);
    }

    private void put(ContentValues v, Map<String, Object> data, String key) {
        if (!data.containsKey(key) || data.get(key) == null) return;
        Object value = data.get(key);
        if (value instanceof Integer) v.put(key, (Integer) value);
        else if (value instanceof Long) v.put(key, (Long) value);
        else if (value instanceof Double) v.put(key, (Double) value);
        else if (value instanceof Float) v.put(key, (Float) value);
        else if (value instanceof Boolean) v.put(key, (Boolean) value ? 1 : 0);
        else v.put(key, String.valueOf(value));
    }

    public boolean updateRecord(long id, Map<String, Object> data) {
        ContentValues v = new ContentValues();
        put(v, data, "title"); put(v, data, "description"); put(v, data, "category");
        put(v, data, "priority"); put(v, data, "due_at"); put(v, data, "amount");
        put(v, data, "quantity"); put(v, data, "normal_goal"); put(v, data, "min_goal");
        put(v, data, "frequency"); put(v, data, "difficulty"); put(v, data, "status");
        put(v, data, "reason"); put(v, data, "resistance"); put(v, data, "first_action");
        v.put("updated_at", now());
        return getWritableDatabase().update("records", v, "id=?", new String[]{String.valueOf(id)}) > 0;
    }

    public boolean setStatus(long id, String status) {
        ContentValues v = new ContentValues(); v.put("status", status); v.put("updated_at", now());
        if ("done".equals(status)) v.put("completed_at", now());
        return getWritableDatabase().update("records", v, "id=?", new String[]{String.valueOf(id)}) > 0;
    }

    public boolean deleteRecord(long id) {
        return getWritableDatabase().delete("records", "id=?", new String[]{String.valueOf(id)}) > 0;
    }

    public ArrayList<Map<String, String>> getRecords(String type) {
        ArrayList<Map<String, String>> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("records", null, "type=?", new String[]{type}, null, null, "created_at DESC, id DESC");
        try { while (c.moveToNext()) out.add(cursorMap(c)); } finally { c.close(); }
        return out;
    }

    public ArrayList<Map<String, String>> getAllRecords() {
        ArrayList<Map<String, String>> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("records", null, null, null, null, null, "created_at DESC, id DESC");
        try { while (c.moveToNext()) out.add(cursorMap(c)); } finally { c.close(); }
        return out;
    }

    public Map<String, String> getRecord(long id) {
        Cursor c = getReadableDatabase().query("records", null, "id=?", new String[]{String.valueOf(id)}, null, null, null);
        try { return c.moveToFirst() ? cursorMap(c) : new HashMap<String, String>(); } finally { c.close(); }
    }

    private Map<String, String> cursorMap(Cursor c) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < c.getColumnCount(); i++) m.put(c.getColumnName(i), c.isNull(i) ? "" : c.getString(i));
        return m;
    }

    public int count(String type, String status) {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM records WHERE type=? AND status=?", new String[]{type, status});
        try { c.moveToFirst(); return c.getInt(0); } finally { c.close(); }
    }

    public int countType(String type) {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM records WHERE type=?", new String[]{type});
        try { c.moveToFirst(); return c.getInt(0); } finally { c.close(); }
    }

    public int countTypeSince(String type, String since) {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM records WHERE type=? AND created_at>=?", new String[]{type, since});
        try { c.moveToFirst(); return c.getInt(0); } finally { c.close(); }
    }

    public int countCompletedSince(String type, String since) {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM records WHERE type=? AND status='done' AND completed_at>=?", new String[]{type, since});
        try { c.moveToFirst(); return c.getInt(0); } finally { c.close(); }
    }

    public int focusMinutesSince(String since) {
        Cursor c = getReadableDatabase().rawQuery("SELECT COALESCE(SUM(duration_minutes),0) FROM focus_sessions WHERE completed=1 AND started_at>=?", new String[]{since});
        try { c.moveToFirst(); return c.getInt(0); } finally { c.close(); }
    }

    public double sumAmount(String category, String since) {
        String sql = "SELECT COALESCE(SUM(amount),0) FROM records WHERE type='finance' AND category=?";
        if (since != null) sql += " AND created_at>=?";
        Cursor c = since == null ? getReadableDatabase().rawQuery(sql, new String[]{category}) : getReadableDatabase().rawQuery(sql, new String[]{category, since});
        try { c.moveToFirst(); return c.getDouble(0); } finally { c.close(); }
    }

    public int sumWorkout(String exercise, String since) {
        String sql = "SELECT COALESCE(SUM(quantity),0) FROM records WHERE type='workout' AND category=?";
        if (since != null) sql += " AND created_at>=?";
        Cursor c = since == null ? getReadableDatabase().rawQuery(sql, new String[]{exercise}) : getReadableDatabase().rawQuery(sql, new String[]{exercise, since});
        try { c.moveToFirst(); return c.getInt(0); } finally { c.close(); }
    }

    public void addXp(String reason, int points, long refId) {
        ContentValues v = new ContentValues(); v.put("reason", reason); v.put("points", points); v.put("created_at", now()); v.put("ref_id", refId);
        getWritableDatabase().insert("xp_ledger", null, v);
    }

    public int getXpTotal() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COALESCE(SUM(points),0) FROM xp_ledger", null);
        try { c.moveToFirst(); return c.getInt(0); } finally { c.close(); }
    }

    public ArrayList<Map<String, String>> getXpHistory() {
        ArrayList<Map<String, String>> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT id,reason,points,created_at FROM xp_ledger ORDER BY id DESC LIMIT 30", null);
        try { while (c.moveToNext()) { Map<String,String> m = new HashMap<>(); for(int i=0;i<c.getColumnCount();i++) m.put(c.getColumnName(i), c.getString(i)); out.add(m); } } finally { c.close(); }
        return out;
    }

    private ArrayList<Map<String, String>> getAllXpHistory() {
        ArrayList<Map<String, String>> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT id,reason,points,created_at,ref_id FROM xp_ledger ORDER BY id ASC", null);
        try { while (c.moveToNext()) { Map<String,String> m = new HashMap<>(); for(int i=0;i<c.getColumnCount();i++) m.put(c.getColumnName(i), c.getString(i)); out.add(m); } } finally { c.close(); }
        return out;
    }

    public long addFocusSession(String title, int minutes, String startedAt, boolean completed) {
        ContentValues v = new ContentValues(); v.put("task_title", title); v.put("duration_minutes", minutes);
        v.put("started_at", startedAt); v.put("ended_at", now()); v.put("completed", completed ? 1 : 0);
        return getWritableDatabase().insert("focus_sessions", null, v);
    }

    public int focusMinutes() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COALESCE(SUM(duration_minutes),0) FROM focus_sessions WHERE completed=1", null);
        try { c.moveToFirst(); return c.getInt(0); } finally { c.close(); }
    }

    public int focusSessions() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM focus_sessions WHERE completed=1", null);
        try { c.moveToFirst(); return c.getInt(0); } finally { c.close(); }
    }

    public int habitDoneToday(String title) {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM records WHERE type='habit_log' AND title=? AND substr(created_at,1,10)=?", new String[]{title, today()});
        try { c.moveToFirst(); return c.getInt(0); } finally { c.close(); }
    }

    public int habitStreak(String title) {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(DISTINCT substr(created_at,1,10)) FROM records WHERE type='habit_log' AND title=?", new String[]{title});
        try { c.moveToFirst(); return c.getInt(0); } finally { c.close(); }
    }

    public String exportJson() throws Exception {
        JSONObject root = new JSONObject(); root.put("app", "LIFE.EXE"); root.put("schema", DB_VERSION);
        JSONArray records = new JSONArray();
        for (Map<String,String> row : getAllRecords()) records.put(new JSONObject(row));
        root.put("records", records);
        JSONArray xp = new JSONArray();
        for (Map<String,String> row : getAllXpHistory()) xp.put(new JSONObject(row));
        root.put("xp_ledger", xp);
        JSONArray sessions = new JSONArray();
        Cursor c = getReadableDatabase().rawQuery("SELECT * FROM focus_sessions", null);
        try { while (c.moveToNext()) { JSONObject o = new JSONObject(); for(int i=0;i<c.getColumnCount();i++) o.put(c.getColumnName(i), c.getString(i)); sessions.put(o); } } finally { c.close(); }
        root.put("focus_sessions", sessions);
        return root.toString(2);
    }

    public void importJson(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        if (!"LIFE.EXE".equals(root.optString("app"))) throw new IllegalArgumentException("Arquivo não é um backup LIFE.EXE.");
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            db.delete("records", null, null); db.delete("xp_ledger", null, null); db.delete("focus_sessions", null, null);
            JSONArray records = root.optJSONArray("records");
            if (records != null) for(int i=0;i<records.length();i++) {
                JSONObject o = records.getJSONObject(i); ContentValues v = new ContentValues();
                String[] cols = {"type","title","description","category","priority","due_at","status","amount","quantity","normal_goal","min_goal","frequency","difficulty","reason","resistance","first_action","created_at","updated_at","completed_at","xp"};
                for(String key:cols) if(o.has(key) && !o.isNull(key)) v.put(key, o.getString(key));
                db.insert("records", null, v);
            }
            JSONArray xp = root.optJSONArray("xp_ledger");
            if(xp != null) for(int i=0;i<xp.length();i++) { JSONObject o=xp.getJSONObject(i); ContentValues v=new ContentValues(); v.put("reason",o.optString("reason")); v.put("points",o.optInt("points")); v.put("created_at",o.optString("created_at",now())); v.put("ref_id",o.optLong("ref_id")); db.insert("xp_ledger",null,v); }
            JSONArray sessions = root.optJSONArray("focus_sessions");
            if(sessions != null) for(int i=0;i<sessions.length();i++) { JSONObject o=sessions.getJSONObject(i); ContentValues v=new ContentValues(); v.put("task_title",o.optString("task_title")); v.put("duration_minutes",o.optInt("duration_minutes")); v.put("started_at",o.optString("started_at",now())); v.put("ended_at",o.optString("ended_at")); v.put("completed",o.optInt("completed")); db.insert("focus_sessions",null,v); }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public static String now() { return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(new java.util.Date()); }
    public static String today() { return new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date()); }
}
