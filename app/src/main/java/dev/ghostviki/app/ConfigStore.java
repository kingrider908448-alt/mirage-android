package dev.ghostviki.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import dev.ghostviki.core.Coordinates;
import dev.ghostviki.core.Identity;

public final class ConfigStore {
    public static final String PACKAGE = "dev.ghostviki.app";
    public static final String PREFS = "runtime";
    public final SharedPreferences preferences;
    public final boolean bridgeAvailable;

    @SuppressWarnings({"deprecation", "unchecked"})
    public ConfigStore(Context context) {
        SharedPreferences draft = context.getSharedPreferences("draft", Context.MODE_PRIVATE);
        SharedPreferences selected;
        boolean ready;
        try {
            selected = context.getSharedPreferences(PREFS, Context.MODE_WORLD_READABLE);
            ready = true;
            if (!selected.contains("schema")) {
                SharedPreferences.Editor editor = selected.edit();
                for (Map.Entry<String, ?> e : draft.getAll().entrySet()) {
                    Object value = e.getValue();
                    if (value instanceof String) editor.putString(e.getKey(), (String) value);
                    else if (value instanceof Boolean) editor.putBoolean(e.getKey(), (Boolean) value);
                    else if (value instanceof Long) editor.putLong(e.getKey(), (Long) value);
                    else if (value instanceof Integer) editor.putInt(e.getKey(), (Integer) value);
                    else if (value instanceof Set<?>) editor.putStringSet(e.getKey(), new HashSet<>((Set<String>) value));
                }
                if (!editor.putInt("schema", 1).commit()) ready = false;
            }
        } catch (SecurityException e) {
            selected = draft;
            ready = false;
        }
        preferences = selected;
        bridgeAvailable = ready;
    }

    public Set<String> targets() {
        return new HashSet<>(preferences.getStringSet("targets", java.util.Collections.emptySet()));
    }

    public boolean setTargets(Set<String> packages) {
        SharedPreferences.Editor editor = preferences.edit().putStringSet("targets", new HashSet<>(packages));
        // A newly selected app gets its own independently generated values.
        for (String pkg : packages) {
            if (!preferences.contains("android_id:" + pkg)) putIdentity(editor, pkg, Identity.generate());
        }
        return editor.commit();
    }

    public boolean rotateIdentity() {
        SharedPreferences.Editor editor = preferences.edit();
        for (String pkg : targets()) putIdentity(editor, pkg, Identity.generate());
        return editor.putBoolean("identity_enabled", true)
                .putLong("generation", preferences.getLong("generation", 0) + 1)
                .putLong("changed_at", System.currentTimeMillis()).commit();
    }

    private static void putIdentity(SharedPreferences.Editor editor, String pkg, Identity identity) {
        editor.putString("android_id:" + pkg, identity.androidId).putString("serial:" + pkg, identity.serial);
    }

    public boolean setFlag(String name, boolean value) { return preferences.edit().putBoolean(name, value).commit(); }

    public boolean setLocation(String pkg, Coordinates location, boolean enabled) {
        if (!targets().contains(pkg)) throw new IllegalArgumentException("Select this target app first.");
        return preferences.edit().putString("latitude:" + pkg, Double.toString(location.latitude))
                .putString("longitude:" + pkg, Double.toString(location.longitude))
                .putBoolean("location:" + pkg, enabled).commit();
    }
    public boolean stopLocation(String pkg) { return preferences.edit().putBoolean("location:" + pkg, false).commit(); }
}
