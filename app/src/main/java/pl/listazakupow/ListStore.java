package pl.listazakupow;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Zapis listy w pamięci telefonu. */
public final class ListStore {
    private static final String PREFS = "lista";
    private static final String KEY = "items";

    public static final class Item {
        public String text;
        public boolean done;
        public long doneAt;

        public Item(String text) {
            this.text = text;
        }
    }

    private static final String KEY_LOCK = "show_on_lock";

    private ListStore() {}

    /** Czy pokazywać listę na ekranie blokady (domyślnie tak). */
    public static boolean showOnLock(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_LOCK, true);
    }

    /** Klucz Claude API, zapisany tylko w pamięci tego telefonu. */
    public static String apiKey(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("api_key", "");
    }

    public static void setApiKey(Context c, String key) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("api_key", key).apply();
    }

    public static void setShowOnLock(Context c, boolean on) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_LOCK, on).apply();
    }

    public static ArrayList<Item> load(Context c) {
        ArrayList<Item> out = new ArrayList<>();
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = p.getString(KEY, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Item it = new Item(o.optString("t", ""));
                it.done = o.optBoolean("d", false);
                it.doneAt = o.optLong("a", 0L);
                if (!it.text.isEmpty()) out.add(it);
            }
        } catch (JSONException ignored) {
        }
        return out;
    }

    public static void save(Context c, List<Item> items) {
        JSONArray arr = new JSONArray();
        try {
            for (Item it : items) {
                JSONObject o = new JSONObject();
                o.put("t", it.text);
                o.put("d", it.done);
                o.put("a", it.doneAt);
                arr.put(o);
            }
        } catch (JSONException ignored) {
        }
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY, arr.toString())
                .apply();
    }
}
