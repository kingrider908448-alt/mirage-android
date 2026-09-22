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
        // MODE_WORLD_READABLE throws on modern Android. LSPosed's xposedsharedprefs
        // bridge reads this named private file on behalf of scoped target processes.
        SharedPreferences selected = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean ready = true;
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
            ready = editor.putInt("schema", 2).commit();
        }
        preferences = selected;
        bridgeAvailable = ready;
        backfillIdentityFields();
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

    private void backfillIdentityFields() {
        SharedPreferences.Editor editor = preferences.edit();
        boolean changed = false;
        for (String pkg : targets()) {
            Identity identity = Identity.generate();
            if (!preferences.contains("android_id:" + pkg)) { editor.putString("android_id:" + pkg, identity.androidId); changed = true; }
            if (!preferences.contains("serial:" + pkg)) { editor.putString("serial:" + pkg, identity.serial); changed = true; }
            if (!preferences.contains("advertising_id:" + pkg)) { editor.putString("advertising_id:" + pkg, identity.advertisingId); changed = true; }
            if (!preferences.contains("app_set_id:" + pkg)) { editor.putString("app_set_id:" + pkg, identity.appSetId); changed = true; }
            if (!preferences.contains("firebase_installation_id:" + pkg)) { editor.putString("firebase_installation_id:" + pkg, identity.firebaseInstallationId); changed = true; }
            if (!preferences.contains("fcm_token:" + pkg)) { editor.putString("fcm_token:" + pkg, identity.fcmToken); changed = true; }
            if (!preferences.contains("gsf_id:" + pkg)) { editor.putString("gsf_id:" + pkg, identity.gsfId); changed = true; }
            if (!preferences.contains("crashlytics_installation_id:" + pkg)) { editor.putString("crashlytics_installation_id:" + pkg, identity.crashlyticsInstallationId); changed = true; }
        }
        if (changed) editor.commit();
    }

    private static void putIdentity(SharedPreferences.Editor editor, String pkg, Identity identity) {
        editor.putString("android_id:" + pkg, identity.androidId)
                .putString("serial:" + pkg, identity.serial)
                .putString("advertising_id:" + pkg, identity.advertisingId)
                .putString("app_set_id:" + pkg, identity.appSetId)
                .putString("firebase_installation_id:" + pkg, identity.firebaseInstallationId)
                .putString("fcm_token:" + pkg, identity.fcmToken)
                .putString("gsf_id:" + pkg, identity.gsfId)
                .putString("crashlytics_installation_id:" + pkg, identity.crashlyticsInstallationId);
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
