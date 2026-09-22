package dev.ghostviki.app.hooks;

import android.os.SystemClock;
import dev.ghostviki.app.ConfigStore;
import dev.ghostviki.core.Coordinates;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import java.util.Collections;
import java.util.Set;

final class HookConfig {
    private final String packageName;
    private final XSharedPreferences preferences;
    private volatile Snapshot snapshot = Snapshot.OFF;
    private volatile long nextRefresh;
    private boolean loggedError;
    private boolean loggedState;
    private long loggedGeneration = Long.MIN_VALUE;
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
                boolean readable = preferences.getFile().canRead();
                Set<String> targets = preferences.getStringSet("targets", Collections.emptySet());
                boolean selected = targets.contains(packageName);
                long generation = preferences.getLong("generation", -1);
                if (!readable || !selected) {
                    snapshot = Snapshot.OFF;
                    if (!loggedState || generation != loggedGeneration) {
                        XposedBridge.log("GhostViki: config OFF for " + packageName
                                + " readable=" + readable + " selected=" + selected
                                + " targets=" + targets.size() + " generation=" + generation);
                        loggedState = true;
                        loggedGeneration = generation;
                    }
                } else {
                    String id = preferences.getString("android_id:" + packageName, "");
                    String serial = preferences.getString("serial:" + packageName, "");
                    boolean enabled = preferences.getBoolean("identity_enabled", false);
                    boolean validId = id.matches("[0-9a-f]{16}");
                    boolean validSerial = serial.matches("[0-9A-F]{16}");
                    boolean identity = enabled && validId && validSerial;
                    Coordinates coordinates = null;
                    if (preferences.getBoolean("location:" + packageName, false)) {
                        try {
                            coordinates = Coordinates.parse(preferences.getString("latitude:" + packageName, ""),
                                    preferences.getString("longitude:" + packageName, ""));
                        } catch (IllegalArgumentException ignored) { /* Invalid settings disable replacement. */ }
                    }
                    snapshot = new Snapshot(identity, id, serial,
                            value("device_id"), value("wifi_mac"), value("bluetooth_mac"),
                            value("imei1"), value("imei2"), value("imsi"), value("iccid"),
                            value("build_id"), value("hardware"), value("brand"), value("model"),
                            value("manufacturer"), value("device"), value("product"), value("fingerprint"),
                            preferences.getBoolean("hide_files", false),
                            preferences.getBoolean("hide_packages", false), coordinates);
                    if (!loggedState || generation != loggedGeneration) {
                        XposedBridge.log("GhostViki: config ON for " + packageName
                                + " identity=" + identity + " enabled=" + enabled
                                + " validId=" + validId + " validSerial=" + validSerial
                                + " generation=" + generation);
                        loggedState = true;
                        loggedGeneration = generation;
                    }
                }
            } catch (RuntimeException e) {
                snapshot = Snapshot.OFF;
                if (!loggedError) {
                    loggedError = true;
                    XposedBridge.log("GhostViki: settings unavailable in " + packageName + ": " + e.getClass().getSimpleName());
                }
            } finally {
                nextRefresh = now + 1000;
                loading.set(false);
            }
        }
        return snapshot;
    }

    private String value(String key) {
        return preferences.getString(key + ":" + packageName, "");
    }

    static final class Snapshot {
        static final Snapshot OFF = new Snapshot(false, "", "", "", "", "", "", "", "", "",
                "", "", "", "", "", "", "", "", false, false, null);
        final boolean identity, hideFiles, hidePackages;
        final String androidId, serial, deviceId, wifiMac, bluetoothMac;
        final String imei1, imei2, imsi, iccid;
        final String buildId, hardware, brand, model, manufacturer, device, product, fingerprint;
        final Coordinates coordinates;
        Snapshot(boolean identity, String androidId, String serial, String deviceId,
                 String wifiMac, String bluetoothMac, String imei1, String imei2,
                 String imsi, String iccid, String buildId, String hardware, String brand,
                 String model, String manufacturer, String device, String product,
                 String fingerprint, boolean hideFiles, boolean hidePackages,
                 Coordinates coordinates) {
            this.identity = identity;
            this.androidId = androidId;
            this.serial = serial;
            this.deviceId = deviceId;
            this.wifiMac = wifiMac;
            this.bluetoothMac = bluetoothMac;
            this.imei1 = imei1;
            this.imei2 = imei2;
            this.imsi = imsi;
            this.iccid = iccid;
            this.buildId = buildId;
            this.hardware = hardware;
            this.brand = brand;
            this.model = model;
            this.manufacturer = manufacturer;
            this.device = device;
            this.product = product;
            this.fingerprint = fingerprint;
            this.hideFiles = hideFiles;
            this.hidePackages = hidePackages;
            this.coordinates = coordinates;
        }
    }
}
