package dev.ghostviki.app.hooks;

import android.app.ActivityManager;
import android.app.usage.StorageStatsManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.graphics.Point;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.storage.StorageManager;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Display;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import dev.ghostviki.app.ConfigStore;
import dev.ghostviki.core.DeviceCatalog;
import dev.ghostviki.core.DeviceProfile;
import dev.ghostviki.core.HardwareReadouts;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Executes the production configuration writer, reader and after-hook callbacks on host fixtures. */
public final class ProfileChecks {
    private static int checks;
    private static final String ALPHA = "example.catalog.reader", BETA = "example.another.reader";
    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
    private static void publish(ConfigStore store) {
        XSharedPreferences.published = store.preferences.getAll(); SystemClock.advance();
    }
    private static Object read(Class<?> type, String method, Object result, Object... args) throws Throwable {
        return XposedBridge.after(type, method, null, args, result, null).getResult();
    }
    public static void main(String[] args) throws Throwable {
        check(DeviceCatalog.all().size() == 100, "Exactly 100 catalog entries");
        Set<String> names = new HashSet<>(), models = new HashSet<>(), keys = new HashSet<>();
        for (DeviceProfile profile : DeviceCatalog.all()) {
            check(names.add(profile.name) && models.add(profile.model) && keys.add(profile.key), "Unique model/name/key: " + profile.name);
            check(DeviceCatalog.find(profile.key) == profile, "Stable lookup");
        }
        DeviceProfile prior = DeviceCatalog.all().get(0);
        Random random = new Random(20260923L);
        for (int i = 0; i < 1000; i++) {
            DeviceProfile next = DeviceCatalog.next(prior.key, random);
            check(!next.key.equals(prior.key) && !next.manufacturer.equalsIgnoreCase(prior.manufacturer), "Rotation avoids preceding model and manufacturer");
            prior = next;
        }
        Context context = new Context(true);
        ConfigStore store = new ConfigStore(context);
        store.setTargets(Set.of(ALPHA, BETA)); store.rotateIdentity();
        Map<String, ?> beforeBeta = store.preferences.getAll();
        HookConfig config = new HookConfig(ALPHA);
        XposedBridge.clearHooks();
        DeviceNameHooks.install(config, ProfileChecks.class.getClassLoader());
        HardwareProfileHooks.install(config);
        String previousId = store.preferences.getString("android_id:" + ALPHA, "");
        for (DeviceProfile profile : DeviceCatalog.all()) {
            check(store.selectDeviceProfile(ALPHA, profile.key), "Profile commit succeeds");
            publish(store);
            HookConfig.Snapshot saved = config.get();
            check(saved.identity && saved.deviceProfile == profile && saved.model.equals(profile.model)
                    && saved.brand.equals(profile.brand) && saved.manufacturer.equals(profile.manufacturer)
                    && saved.device.equals(profile.device) && saved.deviceName.equals(profile.name), "Writer and hook reader agree: " + profile.name);
            check(!previousId.equals(saved.androidId), "Choosing a device refreshes its identifiers");
            previousId = saved.androidId;
            for (Class<?> table : new Class<?>[]{Settings.Global.class, Settings.System.class, Settings.Secure.class}) {
                check(profile.name.equals(read(table, "getString", "Old phone", null, "device_name")), "Friendly device name reaches target");
                check(profile.name.equals(read(table, "getStringForUser", "Old phone", null, "device_name", 0)), "Per-user setting name reaches target");
            }
            check(profile.name.equals(read(Settings.Global.class, "getString", null, null, "device_name")), "Unset friendly name receives profile name");
            check(profile.name.equals(read(BluetoothAdapter.class, "getName", "Old Bluetooth")), "Local Bluetooth name agrees");
            check(profile.model.equals(read(SystemProperties.class, "get", "", "ro.product.vendor.model", "")), "Whitelisted property agrees even with empty original");
            check(profile.name.equals(read(SystemProperties.class, "get", "Old name", "ro.product.marketname")), "Marketing-name property agrees");
            ConfigStore restarted = new ConfigStore(new Context(true, context.disk));
            check(profile.key.equals(restarted.preferences.getString("device_profile_key:" + ALPHA, ""))
                    && profile.name.equals(restarted.preferences.getString("device_name:" + ALPHA, "")), "Selected profile survives process restart");
        }
        for (Map.Entry<String, ?> entry : beforeBeta.entrySet()) if (entry.getKey().endsWith(":" + BETA))
            check(entry.getValue().equals(store.preferences.getAll().get(entry.getKey())), "Choosing ALPHA never rotates BETA");
        boolean rejected = false;
        try { store.selectDeviceProfile(ALPHA, "not-a-model"); } catch (IllegalArgumentException e) { rejected = true; }
        check(rejected, "Reject unknown model key");
        rejected = false;
        try { store.selectDeviceProfile("example.unselected", DeviceCatalog.all().get(0).key); } catch (IllegalArgumentException e) { rejected = true; }
        check(rejected, "Reject unselected target");
        check("1234".equals(read(Settings.Secure.class, "getString", "1234", null, "android_id")), "Name adapter does not touch unrelated settings");
        check("verified".equals(read(SystemProperties.class, "get", "verified", "ro.boot.verifiedbootstate")), "Unrelated security property preserved");
        check("stock".equals(read(SystemProperties.class, "get", "stock", "ro.build.fingerprint")), "Unverified firmware property preserved");
        check(read(BluetoothAdapter.class, "getName", null) == null, "Unavailable Bluetooth adapter stays unavailable");
        SecurityException denied = new SecurityException("denied");
        check(XposedBridge.after(Settings.Global.class, "getString", null, new Object[]{null, "device_name"}, null, denied).getThrowable() == denied, "Name read permission errors preserved");

        DeviceProfile complete = DeviceCatalog.all().stream().filter(p -> p.ramGiB > 0 && p.storageGB > 0 && p.hasDisplay()).findFirst().orElseThrow();
        store.selectDeviceProfile(ALPHA, complete.key); publish(store);
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        memory.totalMem = 1; memory.availMem = Long.MAX_VALUE; memory.threshold = 100;
        read(ActivityManager.class, "getMemoryInfo", null, memory);
        check(memory.totalMem == HardwareReadouts.ramBytes(complete) && memory.availMem == memory.totalMem && !memory.lowMemory, "RAM is replaced with bounded available memory");
        long storage = HardwareReadouts.storageBytes(complete);
        check((Long) read(StorageStatsManager.class, "getTotalBytes", 1L, StorageManager.UUID_DEFAULT) == storage, "Internal volume capacity follows profile");
        check((Long) read(StorageStatsManager.class, "getFreeBytes", Long.MAX_VALUE, StorageManager.UUID_DEFAULT) == storage, "Free storage cannot exceed capacity");
        check((Long) read(StorageStatsManager.class, "getTotalBytes", 123L, UUID.randomUUID()) == 123L, "External volume unchanged");
        Point point = new Point(); point.x = 2000; point.y = 1000;
        XposedBridge.after(Display.class, "getRealSize", new Display(0), new Object[]{point}, null, null);
        check(point.x == complete.height && point.y == complete.width, "Display preserves landscape orientation");
        DisplayMetrics metrics = new DisplayMetrics(); metrics.widthPixels = 500; metrics.heightPixels = 1000; metrics.density = 2;
        XposedBridge.after(Display.class, "getRealMetrics", new Display(0), new Object[]{metrics}, null, null);
        check(metrics.widthPixels == complete.width && metrics.heightPixels == complete.height && metrics.density == 2, "Display pixel readout changes without inventing density");
        point.x = 800; point.y = 600;
        XposedBridge.after(Display.class, "getSize", new Display(1), new Object[]{point}, null, null);
        check(point.x == 800 && point.y == 600, "External display unchanged");
        check(XposedBridge.after(StorageStatsManager.class, "getTotalBytes", null,
                new Object[]{StorageManager.UUID_DEFAULT}, null, denied).getThrowable() == denied, "Hardware access errors preserved");
        store.setFlag("identity_enabled", false); publish(store);
        check("Original".equals(read(Settings.Global.class, "getString", "Original", null, "device_name")), "Disabled profile leaves name alone");
        check((Long) read(StorageStatsManager.class, "getTotalBytes", 999L, StorageManager.UUID_DEFAULT) == 999L, "Disabled profile leaves capacity alone");
        store.setFlag("identity_enabled", true);
        store.preferences.edit().putString("device_name:" + ALPHA, "mismatched name").commit(); publish(store);
        check(!config.get().identity, "Inconsistent catalog profile fails closed");
        store.selectDeviceProfile(ALPHA, complete.key); store.setTargets(Set.of(BETA)); publish(store);
        check("Original".equals(read(Settings.Global.class, "getString", "Original", null, "device_name")), "Deselected app leaves name alone");
        System.out.println("PASS: " + checks + " catalog / persistence / callback checks (host fixtures, not an Android runtime)");
    }
}
