package dev.ghostviki.app.hooks;

import android.os.SystemClock;
import dev.ghostviki.app.ConfigStore;
import dev.ghostviki.core.Coordinates;
import dev.ghostviki.core.DeviceCatalog;
import dev.ghostviki.core.DeviceProfile;
import dev.ghostviki.core.ProfileConsistency;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

final class HookConfig {
    private final String packageName;
    private final XSharedPreferences preferences;
    private volatile Snapshot snapshot = Snapshot.OFF;
    private volatile long nextRefresh;
    private volatile String status = "CONFIG_NOT_READ";
    private String loggedStatus = "";
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
                // Read one immutable view, through XSharedPreferences' file service.
                // getFile().canRead() is NOT authoritative for a service-backed reader.
                Map<String, ?> data = preferences.getAll();
                Object configuredTargets = data.get("targets");
                Set<?> targets = configuredTargets instanceof Set<?> ? (Set<?>) configuredTargets : Collections.emptySet();
                boolean selected = targets.contains(packageName);
                int schema = data.get("schema") instanceof Integer ? (Integer) data.get("schema") : 0;
                long generation = data.get("generation") instanceof Long ? (Long) data.get("generation") : -1;
                boolean available = schema >= 3 && schema <= ConfigStore.SCHEMA;
                String reason;
                if (!available || !selected) {
                    snapshot = Snapshot.OFF;
                    reason = !available ? "CONFIG_UNAVAILABLE" : "NOT_SELECTED";
                } else {
                    String id = value(data, "android_id");
                    String serial = value(data, "serial");
                    boolean paused = flag(data, ConfigStore.IDENTITY_PAUSED + ":" + packageName);
                    boolean enabled = flag(data, "identity_enabled") && !paused;
                    boolean validId = id.matches("[0-9a-f]{16}");
                    boolean validSerial = serial.matches("[0-9A-F]{16}");
                    DeviceProfile profile = DeviceCatalog.find(value(data, "device_profile_key"));
                    boolean validProfile = schema < 5 || ProfileConsistency.validStoredDeviceProfile(
                            profile,
                            value(data, "device_profile_key"),
                            value(data, "device_name"),
                            value(data, "brand"),
                            value(data, "model"),
                            value(data, "manufacturer"),
                            value(data, "device"));
                    boolean validAdapters = mac(value(data, "wifi_mac"))
                            && mac(value(data, "bssid"))
                            && mac(value(data, "bluetooth_mac"))
                            && !value(data, "wifi_mac").equals(value(data, "bssid"))
                            && value(data, "imei1").matches("[0-9]{15}")
                            && value(data, "imei2").matches("[0-9]{15}")
                            && value(data, "imsi").matches("[0-9]{15}")
                            && value(data, "iccid").matches("[0-9]{20}");
                    boolean cleanFirmware = value(data, "build_id").isEmpty()
                            && value(data, "hardware").isEmpty()
                            && value(data, "product").isEmpty()
                            && value(data, "fingerprint").isEmpty();
                    boolean identity = enabled && validId && validSerial && validProfile
                            && validAdapters && cleanFirmware;
                    reason = paused ? "IDENTITY_PAUSED" : !enabled ? "IDENTITY_DISABLED" : !identity ? "INVALID_PROFILE" : "PROFILE_READY";
                    Coordinates coordinates = null;
                    if (flag(data, "location:" + packageName)) {
                        try {
                            coordinates = Coordinates.parse(value(data, "latitude"), value(data, "longitude"));
                        } catch (IllegalArgumentException ignored) { /* Invalid settings disable replacement. */ }
                    }
                    snapshot = new Snapshot(identity, id, serial,
                            value(data, "device_id"), value(data, "wifi_mac"), value(data, "bssid"), value(data, "bluetooth_mac"),
                            value(data, "imei1"), value(data, "imei2"), value(data, "imsi"), value(data, "iccid"),
                            value(data, "build_id"), value(data, "hardware"), value(data, "brand"), value(data, "model"),
                            value(data, "manufacturer"), value(data, "device"), value(data, "product"), value(data, "fingerprint"),
                            flag(data, "hide_files"), flag(data, "hide_packages"), coordinates,
                            profile, value(data, "device_name"),
                            identity && !flag(data, ConfigStore.KEEP_REAL_DEVICE + ":" + packageName),
                            flag(data, ConfigStore.BLOCK_CLIPBOARD + ":" + packageName));
                }
                report(reason + " schema=" + schema + " generation=" + generation + " targets=" + targets.size()
                        + " device_override=" + snapshot.overrideDevice + " clipboard_block=" + snapshot.blockClipboard);
            } catch (RuntimeException e) {
                snapshot = Snapshot.OFF;
                report("CONFIG_READ_ERROR " + e.getClass().getSimpleName());
            } finally {
                nextRefresh = now + 1000;
                loading.set(false);
            }
        }
        return snapshot;
    }

    String diagnostics() {
        get();
        return status;
    }

    private void report(String message) {
        status = message;
        if (!message.equals(loggedStatus)) {
            loggedStatus = message;
            XposedBridge.log("GhostViki: config for " + packageName + " " + message);
        }
    }

    private static boolean mac(String value) {
        if (value == null || !value.matches("([0-9A-F]{2}:){5}[0-9A-F]{2}")) return false;
        int first = Integer.parseInt(value.substring(0, 2), 16);
        return (first & 3) == 2;
    }

    private static boolean flag(Map<String, ?> data, String key) {
        return Boolean.TRUE.equals(data.get(key));
    }

    private String value(Map<String, ?> data, String key) {
        Object value = data.get(key + ":" + packageName);
        return value instanceof String ? (String) value : "";
    }

    static final class Snapshot {
        static final Snapshot OFF = new Snapshot(false, "", "", "", "", "", "", "", "", "", "",
                "", "", "", "", "", "", "", "", false, false, null, null, "", false, false);
        final boolean identity, hideFiles, hidePackages, overrideDevice, blockClipboard;
        final String androidId, serial, deviceId, wifiMac, bssid, bluetoothMac;
        final String imei1, imei2, imsi, iccid;
        final String buildId, hardware, brand, model, manufacturer, device, product, fingerprint;
        final Coordinates coordinates;
        final DeviceProfile deviceProfile;
        final String deviceName;
        Snapshot(boolean identity, String androidId, String serial, String deviceId,
                 String wifiMac, String bssid, String bluetoothMac, String imei1, String imei2,
                 String imsi, String iccid, String buildId, String hardware, String brand,
                 String model, String manufacturer, String device, String product,
                 String fingerprint, boolean hideFiles, boolean hidePackages,
                 Coordinates coordinates, DeviceProfile deviceProfile, String deviceName,
                 boolean overrideDevice, boolean blockClipboard) {
            this.identity = identity;
            this.androidId = androidId;
            this.serial = serial;
            this.deviceId = deviceId;
            this.wifiMac = wifiMac;
            this.bssid = bssid;
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
            this.deviceProfile = deviceProfile;
            this.deviceName = deviceName;
            this.overrideDevice = overrideDevice;
            this.blockClipboard = blockClipboard;
        }
    }
}
