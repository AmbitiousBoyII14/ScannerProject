package myscanne.com;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Simple JSON-backed history persistence using SharedPreferences.
 * Stores every completed scan session so the Dashboard + History screen can read it back.
 * Java 7 compatible – no lambdas / streams.
 */
public class HistoryStore {

    private static final String PREFS = "scanner_history";
    private static final String KEY = "entries";
    private static final int MAX_ENTRIES = 200;

    public static class Entry {
        public long id;
        public String type;    // TLS, SNI, PROXY, PORT, DNS, DPI, SPLIT
        public String target;  // domain / file name
        public String date;    // human readable
        public int total;      // hosts scanned
        public int found;      // hits
        public String results; // full text log
    }

    public static void add(Context ctx, String type, String target, int total, int found, String results) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONArray arr = readArray(p);
        try {
            JSONObject o = new JSONObject();
            long id = System.currentTimeMillis();
            o.put("id", id);
            o.put("type", type);
            o.put("target", target);
            o.put("date", new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(id)));
            o.put("total", total);
            o.put("found", found);
            o.put("results", results);
            // newest first
            JSONArray out = new JSONArray();
            out.put(o);
            for (int i = 0; i < arr.length() && i < MAX_ENTRIES - 1; i++) {
                out.put(arr.get(i));
            }
            p.edit().putString(KEY, out.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static List<Entry> getAll(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONArray arr = readArray(p);
        List<Entry> list = new ArrayList<Entry>();
        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject o = arr.getJSONObject(i);
                Entry e = new Entry();
                e.id = o.optLong("id");
                e.type = o.optString("type");
                e.target = o.optString("target");
                e.date = o.optString("date");
                e.total = o.optInt("total");
                e.found = o.optInt("found");
                e.results = o.optString("results");
                list.add(e);
            } catch (Exception ignored) {}
        }
        return list;
    }

    public static void clear(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply();
    }

    public static int totalScans(Context ctx) {
        return readArray(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)).length();
    }

    public static int totalHits(Context ctx) {
        JSONArray arr = readArray(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE));
        int sum = 0;
        for (int i = 0; i < arr.length(); i++) {
            sum += arr.optJSONObject(i) != null ? arr.optJSONObject(i).optInt("found") : 0;
        }
        return sum;
    }

    private static JSONArray readArray(SharedPreferences p) {
        String raw = p.getString(KEY, "[]");
        try {
            return new JSONArray(raw);
        } catch (Exception e) {
            return new JSONArray();
        }
    }
}
