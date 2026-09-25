package dev.ghostviki.app.hooks;

import android.app.ActivityManager;
import android.app.usage.StorageStatsManager;
import android.bluetooth.BluetoothAdapter;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Point;
import android.os.Build;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.storage.StorageManager;
import android.provider.Settings;
import android.view.Display;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import dev.ghostviki.app.ConfigStore;
import dev.ghostviki.core.DeviceCatalog;
import java.util.Map;
import java.util.Set;

/** Production privacy callbacks with counted originals; these checks do not emulate Vector injection. */
public final class PrivacyChecks {
    private static final String ALPHA = "example.privacy.alpha", BETA = "example.privacy.beta";
    private static final String[] READS = {"getPrimaryClip", "getPrimaryClipDescription", "getText", "hasPrimaryClip", "hasText"};
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static void publish(ConfigStore store) {
        XSharedPreferences.published = store.preferences.getAll(); SystemClock.advance();
    }
    private static void clipboard(HookConfig config, boolean blocked) throws Throwable {
        XposedBridge.clearHooks(); ClipboardHooks.install(config);
        for (String method : READS) {
            int[] reads = {0};
            Object original = method.startsWith("has") ? Boolean.TRUE : new Object();
            var result = XposedBridge.invoke(ClipboardManager.class, method, () -> { reads[0]++; return original; });
            Object expected = blocked ? (method.startsWith("has") ? Boolean.FALSE : null) : original;
            check(result.getResult() == expected && !result.hasThrowable(), method + " returns the correct contract value");
            check(reads[0] == (blocked ? 0 : 1), method + " must block before the underlying read");
        }
    }
    private static void resetBuild() throws Exception {
        for (var field : Build.class.getFields()) field.set(null, "original_" + field.getName());
    }
    private static Object after(Class<?> type, String method, Object result, Object... args) throws Throwable {
        return XposedBridge.after(type, method, null, args, result, null).getResult();
    }
    public static void main(String[] args) throws Throwable {
        Context context = new Context(true);
        ConfigStore store = new ConfigStore(context);
        store.setTargets(Set.of(ALPHA, BETA)); store.rotateIdentity(); publish(store);
        HookConfig alpha = new HookConfig(ALPHA), beta = new HookConfig(BETA);
        check(alpha.get().overrideDevice && !alpha.get().blockClipboard, "Existing behavior preserved unless a per-app option is selected");
        clipboard(alpha, false);
        check(store.setPrivacyOption(ALPHA, ConfigStore.BLOCK_CLIPBOARD, true), "Privacy option commits");
        publish(store); clipboard(alpha, true); clipboard(beta, false);
        check(alpha.diagnostics().contains("clipboard_block=true"), "Diagnostic includes clipboard policy without content");
        store.setFlag("identity_enabled", false); publish(store);
        check(!alpha.get().identity && alpha.get().blockClipboard, "Clipboard protection is independent of global identity state");
        clipboard(alpha, true);
        store.setFlag("identity_enabled", true);
        store.preferences.edit().putString("android_id:" + ALPHA, "invalid").commit(); publish(store);
        check(!alpha.get().identity && alpha.get().blockClipboard, "Invalid identity does not disable a valid clipboard policy");
        clipboard(alpha, true);
        store.rotateIdentity();
        check(store.setPrivacyOption(ALPHA, ConfigStore.KEEP_REAL_DEVICE, true), "Keep-real-device option commits");
        publish(store);
        check(alpha.get().identity && !alpha.get().overrideDevice && beta.get().overrideDevice, "Keep-real mode preserves identity and isolates the selected app");
        resetBuild(); BuildProfileHooks.apply(alpha.get());
        check(Build.SERIAL.equals(alpha.get().serial), "Serial still changes in identifier-only mode");
        for (String field : new String[]{"BRAND", "MODEL", "MANUFACTURER", "DEVICE", "SOC_MODEL", "SOC_MANUFACTURER", "ID", "HARDWARE", "PRODUCT", "FINGERPRINT"})
            check(Build.class.getField(field).get(null).equals("original_" + field), "Keep-real mode preserves Build." + field);
        XposedBridge.clearHooks(); DeviceNameHooks.install(alpha, PrivacyChecks.class.getClassLoader()); HardwareProfileHooks.install(alpha);
        check("Original name".equals(after(Settings.Global.class, "getString", "Original name", null, "device_name")), "Keep-real mode preserves the friendly name");
        check("Original BT".equals(after(BluetoothAdapter.class, "getName", "Original BT")), "Keep-real mode preserves local Bluetooth name");
        check("Original model".equals(after(SystemProperties.class, "get", "Original model", "ro.product.model")), "Keep-real mode preserves product properties");
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo(); memory.totalMem = 321;
        after(ActivityManager.class, "getMemoryInfo", null, memory);
        check(memory.totalMem == 321, "Keep-real mode preserves RAM");
        check((Long) after(StorageStatsManager.class, "getTotalBytes", 456L, StorageManager.UUID_DEFAULT) == 456L, "Keep-real mode preserves storage");
        Point point = new Point(); point.x = 800; point.y = 600;
        XposedBridge.after(Display.class, "getRealSize", new Display(0), new Object[]{point}, null, null);
        check(point.x == 800 && point.y == 600, "Keep-real mode preserves screen size");
        store.setPrivacyOption(ALPHA, ConfigStore.KEEP_REAL_DEVICE, false); publish(store);
        resetBuild(); BuildProfileHooks.apply(alpha.get());
        check(Build.MODEL.equals(alpha.get().model) && Build.BRAND.equals(alpha.get().brand), "Catalog mode still applies actual production Build overrides");
        check(Build.FINGERPRINT.equals("original_FINGERPRINT") && Build.PRODUCT.equals("original_PRODUCT"), "Catalog mode never fabricates stock firmware");
        store.setPrivacyOption(ALPHA, ConfigStore.IDENTITY_PAUSED, true); publish(store);
        check(!alpha.get().identity && !alpha.get().overrideDevice && beta.get().identity, "Per-app pause does not disable another app");
        check(alpha.diagnostics().startsWith("IDENTITY_PAUSED"), "Per-app pause has its own diagnostic");
        resetBuild(); BuildProfileHooks.apply(alpha.get());
        check(Build.MODEL.equals("original_MODEL") && Build.SERIAL.equals("original_SERIAL"), "New paused process has no Build overrides");
        clipboard(alpha, true);
        String savedId = store.preferences.getString("android_id:" + ALPHA, "");
        ConfigStore restarted = new ConfigStore(new Context(true, context.disk));
        check(restarted.privacyOption(ALPHA, ConfigStore.BLOCK_CLIPBOARD) && restarted.privacyOption(ALPHA, ConfigStore.IDENTITY_PAUSED), "Per-app settings survive writer restart");
        check(savedId.equals(restarted.preferences.getString("android_id:" + ALPHA, "")), "Changing privacy settings never silently rotates IDs");
        store.selectDeviceProfile(ALPHA, DeviceCatalog.all().get(0).key); store.rotateIdentity(); publish(store);
        check(alpha.get().blockClipboard && !alpha.get().identity, "Profile selection and rotation preserve pause and clipboard options");
        store.setPrivacyOption(ALPHA, ConfigStore.BLOCK_CLIPBOARD, false); publish(store); clipboard(alpha, false);
        SecurityException denied = new SecurityException("platform denial");
        check(XposedBridge.invoke(ClipboardManager.class, "getPrimaryClip", () -> { throw denied; }).getThrowable() == denied, "Unblocked platform errors pass through unchanged");
        store.setPrivacyOption(ALPHA, ConfigStore.BLOCK_CLIPBOARD, true);
        store.setTargets(Set.of(BETA)); publish(store); clipboard(alpha, false);
        check(!alpha.get().blockClipboard, "Removing a target removes its privacy policy");
        boolean rejected = false;
        try { store.setPrivacyOption(ALPHA, ConfigStore.BLOCK_CLIPBOARD, true); } catch (IllegalArgumentException e) { rejected = true; }
        check(rejected, "Reject privacy edits for unselected packages");
        rejected = false;
        try { store.setPrivacyOption(BETA, "schema", true); } catch (IllegalArgumentException e) { rejected = true; }
        check(rejected, "Reject unknown option keys");
        XSharedPreferences.published = Map.of(); SystemClock.advance(); clipboard(beta, false);
        check(!beta.get().blockClipboard && beta.diagnostics().startsWith("CONFIG_UNAVAILABLE"), "Missing config is explicitly unavailable, not protected");
        store.setTargets(Set.of(ALPHA, BETA));
        store.preferences.edit().putInt("schema", 5).commit();
        String beforeUpgrade = store.preferences.getString("android_id:" + BETA, "");
        ConfigStore upgrade = new ConfigStore(new Context(true, context.disk)); publish(upgrade);
        check(upgrade.preferences.getInt("schema", 0) == 6 && beforeUpgrade.equals(upgrade.preferences.getString("android_id:" + BETA, "")), "Schema-5 upgrade preserves existing IDs");
        check(!upgrade.privacyOption(BETA, ConfigStore.BLOCK_CLIPBOARD) && !upgrade.privacyOption(BETA, ConfigStore.KEEP_REAL_DEVICE), "New options default off for existing apps");
        context.cache.get("runtime").failWrites = true;
        check(!store.setPrivacyOption(BETA, ConfigStore.BLOCK_CLIPBOARD, true), "Failed privacy write is reported");
        System.out.println("Privacy checks: " + checks + " passed (JVM callbacks, not phone validation)");
    }
}
