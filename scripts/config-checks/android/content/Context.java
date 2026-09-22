package android.content;

import fixtures.MemoryPreferences;
import java.util.HashMap;
import java.util.Map;

/** Models ContextImpl's filename cache and first-open mode check, not an Android runtime. */
public final class Context {
    public static final int MODE_PRIVATE = 0;
    public static final int MODE_WORLD_READABLE = 1;
    public final Map<String, MemoryPreferences> cache = new HashMap<>();
    public final Map<String, Map<String, Object>> disk;
    private final boolean bridge;
    public Context(boolean bridge) { this(bridge, new HashMap<>()); }
    public Context(boolean bridge, Map<String, Map<String, Object>> disk) { this.bridge = bridge; this.disk = disk; }
    public SharedPreferences getSharedPreferences(String name, int mode) {
        if (cache.containsKey(name)) return cache.get(name);
        if (mode == MODE_WORLD_READABLE && !bridge) throw new SecurityException("No framework bridge");
        MemoryPreferences prefs = new MemoryPreferences(mode, disk.computeIfAbsent(name, k -> new HashMap<>()));
        cache.put(name, prefs);
        return prefs;
    }
}
