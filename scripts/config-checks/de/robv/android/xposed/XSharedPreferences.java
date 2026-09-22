package de.robv.android.xposed;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** A service-readable snapshot with a deliberately non-directly-readable backing path. */
public final class XSharedPreferences {
    public static Map<String, ?> published = Map.of();
    public static boolean failRead;
    private Map<String, ?> data = Map.of();
    public XSharedPreferences(String pkg, String name) {}
    public void reload() {
        if (failRead) throw new IllegalStateException("Simulated bridge failure");
        data = new HashMap<>(published);
    }
    public File getFile() { return new File("not-a-real-ghostviki-preference-path") {
        @Override public boolean canRead() { return false; }
    }; }
    public Map<String, ?> getAll() { return new HashMap<>(data); }
    public String getString(String key, String fallback) { return data.containsKey(key) ? (String) data.get(key) : fallback; }
    @SuppressWarnings("unchecked")
    public Set<String> getStringSet(String key, Set<String> fallback) { return data.containsKey(key) ? (Set<String>) data.get(key) : fallback; }
    public long getLong(String key, long fallback) { return data.containsKey(key) ? (Long) data.get(key) : fallback; }
    public boolean getBoolean(String key, boolean fallback) { return data.containsKey(key) ? (Boolean) data.get(key) : fallback; }
}
