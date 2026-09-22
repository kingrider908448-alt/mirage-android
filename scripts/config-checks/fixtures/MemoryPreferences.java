package fixtures;

import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class MemoryPreferences implements SharedPreferences {
    public final int openingMode;
    public boolean failWrites;
    private final Map<String, Object> disk;
    private final Map<String, Object> data;

    public MemoryPreferences(int mode, Map<String, Object> disk) {
        openingMode = mode;
        this.disk = disk;
        data = new HashMap<>(disk);
    }
    @Override public Map<String, ?> getAll() { return new HashMap<>(data); }
    @Override public String getString(String key, String fallback) { return (String) data.getOrDefault(key, fallback); }
    @SuppressWarnings("unchecked")
    @Override public Set<String> getStringSet(String key, Set<String> fallback) {
        return new HashSet<>((Set<String>) data.getOrDefault(key, fallback));
    }
    @Override public int getInt(String key, int fallback) { return (Integer) data.getOrDefault(key, fallback); }
    @Override public long getLong(String key, long fallback) { return (Long) data.getOrDefault(key, fallback); }
    @Override public boolean getBoolean(String key, boolean fallback) { return (Boolean) data.getOrDefault(key, fallback); }
    @Override public boolean contains(String key) { return data.containsKey(key); }
    @Override public Editor edit() {
        return new Editor() {
            private final Map<String, Object> changes = new HashMap<>();
            @Override public Editor putString(String key, String value) { changes.put(key, value); return this; }
            @Override public Editor putStringSet(String key, Set<String> value) { changes.put(key, new HashSet<>(value)); return this; }
            @Override public Editor putInt(String key, int value) { changes.put(key, value); return this; }
            @Override public Editor putLong(String key, long value) { changes.put(key, value); return this; }
            @Override public Editor putBoolean(String key, boolean value) { changes.put(key, value); return this; }
            @Override public boolean commit() {
                data.putAll(changes); // Android publishes memory even when the disk write fails.
                if (failWrites) return false;
                disk.putAll(changes);
                return true;
            }
        };
    }
}
