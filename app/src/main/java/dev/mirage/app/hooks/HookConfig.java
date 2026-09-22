package dev.mirage.app.hooks;

import android.os.SystemClock;
import dev.mirage.app.ConfigStore;
import dev.mirage.core.Coordinates;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import java.util.Collections;

final class HookConfig {
    private final String packageName;
    private final XSharedPreferences preferences;
    private volatile Snapshot snapshot = Snapshot.OFF;
    private volatile long nextRefresh;
    private boolean loggedError;
    private final ThreadLocal<Boolean> loading = ThreadLocal.withInitial(() -> false);

    HookConfig(String packageName) {
        this.packageName = packageName;
        preferences = new XSharedPreferences(ConfigStore.PACKAGE, ConfigStore.PREFS);
    }

    Snapshot get() {
        if (loading.get()) return snapshot;
        long now = SystemClock.elapsedRealtime();
        if (now < nextRefresh) return snapshot;
        synchronized (this) {
            if (now < nextRefresh) return snapshot;
            loading.set(true);
            try {
                preferences.reload();
                if (!preferences.getFile().canRead()
                        || !preferences.getStringSet("targets", Collections.emptySet()).contains(packageName)) {
                    snapshot = Snapshot.OFF;
                } else {
                    String id = preferences.getString("android_id:" + packageName, "");
                    String serial = preferences.getString("serial:" + packageName, "");
                    boolean identity = preferences.getBoolean("identity_enabled", false)
                            && id.matches("[0-9a-f]{16}") && serial.matches("[0-9A-F]{16}");
                    Coordinates coordinates = null;
                    if (preferences.getBoolean("location:" + packageName, false)) {
                        try {
                            coordinates = Coordinates.parse(preferences.getString("latitude:" + packageName, ""),
                                    preferences.getString("longitude:" + packageName, ""));
                        } catch (IllegalArgumentException ignored) { /* Invalid settings disable replacement. */ }
                    }
                    snapshot = new Snapshot(identity, id, serial, preferences.getBoolean("hide_files", false),
                            preferences.getBoolean("hide_packages", false), coordinates);
                }
            } catch (RuntimeException e) {
                snapshot = Snapshot.OFF;
                if (!loggedError) {
                    loggedError = true;
                    XposedBridge.log("Mirage: settings unavailable in " + packageName + ": " + e.getClass().getSimpleName());
                }
            } finally {
                nextRefresh = now + 1000;
                loading.set(false);
            }
        }
        return snapshot;
    }

    static final class Snapshot {
        static final Snapshot OFF = new Snapshot(false, "", "", false, false, null);
        final boolean identity, hideFiles, hidePackages;
        final String androidId, serial;
        final Coordinates coordinates;
        Snapshot(boolean identity, String androidId, String serial, boolean hideFiles, boolean hidePackages, Coordinates coordinates) {
            this.identity = identity;
            this.androidId = androidId;
            this.serial = serial;
            this.hideFiles = hideFiles;
            this.hidePackages = hidePackages;
            this.coordinates = coordinates;
        }
    }
}
