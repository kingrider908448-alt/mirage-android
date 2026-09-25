package dev.ghostviki.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import dev.ghostviki.core.Coordinates;
import dev.ghostviki.core.Identity;
import dev.ghostviki.core.DeviceCatalog;
import dev.ghostviki.core.DeviceProfile;
import dev.ghostviki.core.ProfileConsistency;

public final class ConfigStore {
    public static final String PACKAGE = "dev.ghostviki.app";
    public static final String PREFS = "runtime";
    public static final int SCHEMA = 6;
    public static final String BLOCK_CLIPBOARD = "block_clipboard";
    public static final String KEEP_REAL_DEVICE = "keep_real_device";
    public static final String IDENTITY_PAUSED = "identity_paused";
    public final SharedPreferences preferences;
    public final boolean bridgeAvailable;

    @SuppressWarnings({"deprecation", "unchecked"})
    public ConfigStore(Context context) {
        SharedPreferences draft = context.getSharedPreferences("draft", Context.MODE_PRIVATE);
        SharedPreferences selected;
        boolean ready;
        try {
            // This MUST be the first open of runtime in this process. ContextImpl caches
            // by filename, and SharedPreferencesImpl keeps the FIRST opening mode.
            // A private open followed by a world-readable open can falsely report success
            // while all subsequent writes still use MODE_PRIVATE.
            selected = context.getSharedPreferences(PREFS, Context.MODE_WORLD_READABLE);
            ready = true;
        } catch (SecurityException notActive) {
            selected = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            ready = false;
        }

        SharedPreferences.Editor editor = selected.edit();
        if (selected.getInt("schema", 0) < SCHEMA) copyMissing(draft, selected, editor);
        // Force a real disk write after an upgrade or a private-mode fallback. Re-putting
        // an unchanged schema alone may be optimized away without repairing file mode.
        boolean committed = editor.putInt("schema", SCHEMA)
                .putLong("writer_revision", selected.getLong("writer_revision", 0) + 1).commit();
        preferences = selected;
        boolean backfilled = backfillIdentityFields();
        bridgeAvailable = ready && committed && backfilled;
    }

    @SuppressWarnings("unchecked")
    private static void copyMissing(SharedPreferences source, SharedPreferences destination, SharedPreferences.Editor editor) {
        for (Map.Entry<String, ?> e : source.getAll().entrySet()) {
            // A stale draft must never replace an existing runtime profile or selection.
            if (destination.contains(e.getKey())) continue;
            Object value = e.getValue();
            if (value instanceof String) editor.putString(e.getKey(), (String) value);
            else if (value instanceof Boolean) editor.putBoolean(e.getKey(), (Boolean) value);
            else if (value instanceof Long) editor.putLong(e.getKey(), (Long) value);
            else if (value instanceof Integer) editor.putInt(e.getKey(), (Integer) value);
            else if (value instanceof Set<?>) editor.putStringSet(e.getKey(), new HashSet<>((Set<String>) value));
        }
    }

    public Set<String> targets() {
        return new HashSet<>(preferences.getStringSet("targets", java.util.Collections.emptySet()));
    }

    public boolean setTargets(Set<String> packages) {
        SharedPreferences.Editor editor = preferences.edit().putStringSet("targets", new HashSet<>(packages));
        // A newly selected app gets its own independently generated values.
        for (String pkg : packages) {
            if (!preferences.contains("android_id:" + pkg)) putIdentity(editor, pkg, checked(Identity.generate()));
        }
        return editor.commit();
    }

    public boolean rotateIdentity() {
        SharedPreferences.Editor editor = preferences.edit();
        for (String pkg : targets()) putIdentity(editor, pkg, checked(Identity.generate(
                DeviceCatalog.next(preferences.getString("device_profile_key:" + pkg, "")))));
        return editor.putBoolean("identity_enabled", true)
                .putLong("generation", preferences.getLong("generation", 0) + 1)
                .putLong("changed_at", System.currentTimeMillis()).commit();
    }

    public boolean selectDeviceProfile(String pkg, String key) {
        if (!targets().contains(pkg)) throw new IllegalArgumentException("Select this target app first.");
        DeviceProfile profile = DeviceCatalog.find(key);
        if (profile == null) throw new IllegalArgumentException("Unknown device profile");
        SharedPreferences.Editor editor = preferences.edit();
        putIdentity(editor, pkg, checked(Identity.generate(profile)));
        return editor.putBoolean("identity_enabled", true)
                .putLong("generation", preferences.getLong("generation", 0) + 1)
                .putLong("changed_at", System.currentTimeMillis()).commit();
    }

    private boolean backfillIdentityFields() {
        SharedPreferences.Editor editor = preferences.edit();
        boolean changed = false;
        for (String pkg : targets()) {
            Identity identity = checked(Identity.generate());
            if (!preferences.contains("device_profile_key:" + pkg)) {
                // Upgrade only the device profile atomically; keep existing per-app IDs.
                putDeviceProfile(editor, pkg, identity);
                changed = true;
            }
            if (!preferences.contains("android_id:" + pkg)) { editor.putString("android_id:" + pkg, identity.androidId); changed = true; }
            if (!preferences.contains("serial:" + pkg)) { editor.putString("serial:" + pkg, identity.serial); changed = true; }
            if (!preferences.contains("advertising_id:" + pkg)) { editor.putString("advertising_id:" + pkg, identity.advertisingId); changed = true; }
            if (!preferences.contains("app_set_id:" + pkg)) { editor.putString("app_set_id:" + pkg, identity.appSetId); changed = true; }
            if (!preferences.contains("firebase_installation_id:" + pkg)) { editor.putString("firebase_installation_id:" + pkg, identity.firebaseInstallationId); changed = true; }
            if (!preferences.contains("fcm_token:" + pkg)) { editor.putString("fcm_token:" + pkg, identity.fcmToken); changed = true; }
            if (!preferences.contains("gsf_id:" + pkg)) { editor.putString("gsf_id:" + pkg, identity.gsfId); changed = true; }
            if (!preferences.contains("crashlytics_installation_id:" + pkg)) { editor.putString("crashlytics_installation_id:" + pkg, identity.crashlyticsInstallationId); changed = true; }
            if (!preferences.contains("profile_id:" + pkg)) { editor.putString("profile_id:" + pkg, identity.profileId); changed = true; }
            if (!preferences.contains("device_id:" + pkg)) { editor.putString("device_id:" + pkg, identity.deviceId); changed = true; }
            if (!preferences.contains("boot_id:" + pkg)) { editor.putString("boot_id:" + pkg, identity.bootId); changed = true; }
            if (!preferences.contains("wifi_mac:" + pkg)) { editor.putString("wifi_mac:" + pkg, identity.wifiMac); changed = true; }
            if (!preferences.contains("bssid:" + pkg)) { editor.putString("bssid:" + pkg, identity.bssid); changed = true; }
            if (!preferences.contains("bluetooth_mac:" + pkg)) { editor.putString("bluetooth_mac:" + pkg, identity.bluetoothMac); changed = true; }
            if (!preferences.contains("imei1:" + pkg)) { editor.putString("imei1:" + pkg, identity.imei1); changed = true; }
            if (!preferences.contains("imei2:" + pkg)) { editor.putString("imei2:" + pkg, identity.imei2); changed = true; }
            if (!preferences.contains("imsi:" + pkg)) { editor.putString("imsi:" + pkg, identity.imsi); changed = true; }
            if (!preferences.contains("iccid:" + pkg)) { editor.putString("iccid:" + pkg, identity.iccid); changed = true; }
            if (!preferences.contains("build_id:" + pkg)) { editor.putString("build_id:" + pkg, identity.buildId); changed = true; }
            if (!preferences.contains("hardware:" + pkg)) { editor.putString("hardware:" + pkg, identity.hardware); changed = true; }
            if (!preferences.contains("brand:" + pkg)) { editor.putString("brand:" + pkg, identity.brand); changed = true; }
            if (!preferences.contains("model:" + pkg)) { editor.putString("model:" + pkg, identity.model); changed = true; }
            if (!preferences.contains("manufacturer:" + pkg)) { editor.putString("manufacturer:" + pkg, identity.manufacturer); changed = true; }
            if (!preferences.contains("device:" + pkg)) { editor.putString("device:" + pkg, identity.device); changed = true; }
            if (!preferences.contains("product:" + pkg)) { editor.putString("product:" + pkg, identity.product); changed = true; }
            if (!preferences.contains("fingerprint:" + pkg)) { editor.putString("fingerprint:" + pkg, identity.fingerprint); changed = true; }
        }
        return !changed || editor.commit();
    }

    private static Identity checked(Identity identity) {
        ProfileConsistency.Result result = ProfileConsistency.check(identity);
        if (!result.ok) throw new IllegalStateException("Generated profile is inconsistent: " + result.reason);
        return identity;
    }

    public String profileConsistencyStatus(String pkg) {
        DeviceProfile profile = DeviceCatalog.find(preferences.getString("device_profile_key:" + pkg, ""));
        boolean device = ProfileConsistency.validStoredDeviceProfile(
                profile,
                preferences.getString("device_profile_key:" + pkg, ""),
                preferences.getString("device_name:" + pkg, ""),
                preferences.getString("brand:" + pkg, ""),
                preferences.getString("model:" + pkg, ""),
                preferences.getString("manufacturer:" + pkg, ""),
                preferences.getString("device:" + pkg, ""));
        boolean ids = preferences.getString("android_id:" + pkg, "").matches("[0-9a-f]{16}")
                && preferences.getString("serial:" + pkg, "").matches("[0-9A-F]{16}")
                && preferences.getString("wifi_mac:" + pkg, "").matches("([0-9A-F]{2}:){5}[0-9A-F]{2}")
                && preferences.getString("bssid:" + pkg, "").matches("([0-9A-F]{2}:){5}[0-9A-F]{2}")
                && preferences.getString("bluetooth_mac:" + pkg, "").matches("([0-9A-F]{2}:){5}[0-9A-F]{2}")
                && preferences.getString("imei1:" + pkg, "").matches("[0-9]{15}")
                && preferences.getString("imei2:" + pkg, "").matches("[0-9]{15}")
                && preferences.getString("imsi:" + pkg, "").matches("[0-9]{15}")
                && preferences.getString("iccid:" + pkg, "").matches("[0-9]{20}");
        boolean firmwareClean = preferences.getString("build_id:" + pkg, "").isEmpty()
                && preferences.getString("hardware:" + pkg, "").isEmpty()
                && preferences.getString("product:" + pkg, "").isEmpty()
                && preferences.getString("fingerprint:" + pkg, "").isEmpty();
        return device && ids && firmwareClean ? "READY" : "CHECK PROFILE";
    }

    private static void putIdentity(SharedPreferences.Editor editor, String pkg, Identity identity) {
        putDeviceProfile(editor, pkg, identity);
        editor.putString("android_id:" + pkg, identity.androidId)
                .putString("serial:" + pkg, identity.serial)
                .putString("advertising_id:" + pkg, identity.advertisingId)
                .putString("app_set_id:" + pkg, identity.appSetId)
                .putString("firebase_installation_id:" + pkg, identity.firebaseInstallationId)
                .putString("fcm_token:" + pkg, identity.fcmToken)
                .putString("gsf_id:" + pkg, identity.gsfId)
                .putString("crashlytics_installation_id:" + pkg, identity.crashlyticsInstallationId)
                .putString("profile_id:" + pkg, identity.profileId)
                .putString("device_id:" + pkg, identity.deviceId)
                .putString("boot_id:" + pkg, identity.bootId)
                .putString("wifi_mac:" + pkg, identity.wifiMac)
                .putString("bssid:" + pkg, identity.bssid)
                .putString("bluetooth_mac:" + pkg, identity.bluetoothMac)
                .putString("imei1:" + pkg, identity.imei1)
                .putString("imei2:" + pkg, identity.imei2)
                .putString("imsi:" + pkg, identity.imsi)
                .putString("iccid:" + pkg, identity.iccid)
                .putString("build_id:" + pkg, identity.buildId)
                .putString("hardware:" + pkg, identity.hardware)
                .putString("brand:" + pkg, identity.brand)
                .putString("model:" + pkg, identity.model)
                .putString("manufacturer:" + pkg, identity.manufacturer)
                .putString("device:" + pkg, identity.device)
                .putString("product:" + pkg, identity.product)
                .putString("fingerprint:" + pkg, identity.fingerprint);
    }

    private static void putDeviceProfile(SharedPreferences.Editor editor, String pkg, Identity identity) {
        DeviceProfile profile = identity.deviceProfile;
        editor.putString("device_profile_key:" + pkg, profile.key)
                .putString("device_name:" + pkg, profile.name)
                .putString("brand:" + pkg, profile.brand)
                .putString("manufacturer:" + pkg, profile.manufacturer)
                .putString("model:" + pkg, profile.model)
                .putString("device:" + pkg, profile.device)
                // Clear the previous fake firmware inputs. Empty means retain the host value.
                .putString("product:" + pkg, "")
                .putString("hardware:" + pkg, "")
                .putString("build_id:" + pkg, "")
                .putString("fingerprint:" + pkg, "");
    }

    public boolean setFlag(String name, boolean value) { return preferences.edit().putBoolean(name, value).commit(); }

    public boolean privacyOption(String pkg, String option) {
        checkPrivacyOption(option);
        return preferences.getBoolean(option + ":" + pkg, false);
    }

    public boolean setPrivacyOption(String pkg, String option, boolean value) {
        checkPrivacyOption(option);
        if (!targets().contains(pkg)) throw new IllegalArgumentException("Select this target app first.");
        return preferences.edit().putBoolean(option + ":" + pkg, value).commit();
    }

    private static void checkPrivacyOption(String option) {
        if (!BLOCK_CLIPBOARD.equals(option) && !KEEP_REAL_DEVICE.equals(option) && !IDENTITY_PAUSED.equals(option))
            throw new IllegalArgumentException("Unknown privacy option");
    }

    public boolean setLocation(String pkg, Coordinates location, boolean enabled) {
        if (!targets().contains(pkg)) throw new IllegalArgumentException("Select this target app first.");
        return preferences.edit().putString("latitude:" + pkg, Double.toString(location.latitude))
                .putString("longitude:" + pkg, Double.toString(location.longitude))
                .putBoolean("location:" + pkg, enabled).commit();
    }
    public boolean stopLocation(String pkg) { return preferences.edit().putBoolean("location:" + pkg, false).commit(); }
}
