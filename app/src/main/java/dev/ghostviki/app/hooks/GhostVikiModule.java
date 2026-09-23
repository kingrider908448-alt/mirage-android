package dev.ghostviki.app.hooks;

import android.os.Build;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import dev.ghostviki.app.ConfigStore;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class GhostVikiModule implements IXposedHookLoadPackage {
    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam param) {
        if (ConfigStore.PACKAGE.equals(param.packageName)) {
            XposedHelpers.findAndHookMethod("dev.ghostviki.app.ModuleStatus", param.classLoader,
                    "isLoaded", XC_MethodReplacement.returnConstant(true));
            return;
        }
        if ("android".equals(param.packageName)) {
            install("system location", () -> SystemLocationHooks.install(param.classLoader));
            return;
        }
        // Keep identity/root adapters out of system_server, zygote and shared system UIDs.
        if (param.appInfo == null || param.appInfo.uid % 100000 < 10000) return;
        HookConfig config = new HookConfig(param.packageName);
        if ("dev.ghostviki.probe".equals(param.packageName)) {
            install("probe marker", () -> XposedHelpers.findAndHookMethod(
                    "dev.ghostviki.probe.ProbeActivity", param.classLoader,
                    "ghostVikiHookActive", XC_MethodReplacement.returnConstant(true)));
            // Diagnostics only: all observed identity values still come from Android APIs.
            install("probe config diagnostic", () -> XposedHelpers.findAndHookMethod(
                    "dev.ghostviki.probe.ProbeActivity", param.classLoader,
                    "ghostVikiConfigStatus", new XC_MethodReplacement() {
                        @Override protected Object replaceHookedMethod(MethodHookParam ignored) {
                            return config.diagnostics();
                        }
                    }));
        }
        install("identity", () -> installIdentity(config, param.classLoader));
        install("device names and properties", () -> DeviceNameHooks.install(config, param.classLoader));
        install("hardware readouts", () -> HardwareProfileHooks.install(config));
        install("root signals", () -> RootHooks.install(config));
        XposedBridge.log("GhostViki: adapter registration attempted for " + param.packageName
                + "; check per-adapter errors and actual target values (not a passing test)");
    }

    private void installIdentity(HookConfig config, ClassLoader classLoader) {
        install("Settings.Secure.getString", () -> XposedBridge.hookAllMethods(Settings.Secure.class, "getString", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                HookConfig.Snapshot state = config.get();
                if (state.identity && !param.hasThrowable() && param.args.length > 1
                        && Settings.Secure.ANDROID_ID.equals(param.args[1]) && param.getResult() != null) {
                    param.setResult(state.androidId);
                }
            }
        }));
        // Keep platform permission errors intact: this does not grant serial-number access.
        install("Build.getSerial", () -> XposedBridge.hookAllMethods(Build.class, "getSerial", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                HookConfig.Snapshot state = config.get();
                if (state.identity && !param.hasThrowable() && param.getResult() != null
                        && !Build.UNKNOWN.equals(param.getResult())) param.setResult(state.serial);
            }
        }));

        // Legacy getDeviceId returns an IMEI or MEID, not the UI's synthetic hex device ID.
        // Replace only readable IMEI-shaped results; leave CDMA/unknown results untouched.
        hookString(TelephonyManager.class, "getDeviceId", config, GhostVikiModule::imeiForSlot);
        hookString(TelephonyManager.class, "getImei", config, GhostVikiModule::imeiForSlot);
        hookString(TelephonyManager.class, "getSubscriberId", config, s -> s.imsi);
        hookString(TelephonyManager.class, "getSimSerialNumber", config, s -> s.iccid);

        install("Wi-Fi class", () -> {
            Class<?> wifiInfo = XposedHelpers.findClassIfExists("android.net.wifi.WifiInfo", classLoader);
            if (wifiInfo != null) {
                hookString(wifiInfo, "getMacAddress", config, s -> s.wifiMac);
                hookString(wifiInfo, "getBSSID", config, s -> s.bssid);
            }
        });
        install("Bluetooth class", () -> {
            Class<?> bluetooth = XposedHelpers.findClassIfExists("android.bluetooth.BluetoothAdapter", classLoader);
            if (bluetooth != null) hookString(bluetooth, "getAddress", config, s -> s.bluetoothMac);
        });

        HookConfig.Snapshot state = config.get();
        if (state.identity) {
            setBuild("ID", state.buildId);
            setBuild("HARDWARE", state.hardware);
            setBuild("BRAND", state.brand);
            setBuild("MODEL", state.model);
            setBuild("MANUFACTURER", state.manufacturer);
            setBuild("DEVICE", state.device);
            setBuild("PRODUCT", state.product);
            setBuild("FINGERPRINT", state.fingerprint);
            setBuild("SERIAL", state.serial);
            if (state.deviceProfile != null) {
                setBuild("SOC_MODEL", state.deviceProfile.socModel);
                setBuild("SOC_MANUFACTURER", state.deviceProfile.socManufacturer);
            }
        }
    }

    private static final ThreadLocal<Integer> paramSlot = ThreadLocal.withInitial(() -> 0);

    private static String imeiForSlot(HookConfig.Snapshot state) {
        int slot = paramSlot.get();
        return slot == 0 ? state.imei1 : slot == 1 ? state.imei2 : null;
    }

    private interface Value {
        String get(HookConfig.Snapshot state);
    }

    private static void hookString(Class<?> type, String method, HookConfig config, Value value) {
        install(type.getSimpleName() + "." + method, () -> XposedBridge.hookAllMethods(type, method, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                HookConfig.Snapshot state = config.get();
                if (!state.identity || param.hasThrowable() || !(param.getResult() instanceof String)) return;
                String original = (String) param.getResult();
                if (original.isEmpty() || Build.UNKNOWN.equals(original) || "02:00:00:00:00:00".equals(original)) return;
                if ("getDeviceId".equals(method) && !original.matches("[0-9]{15}")) return;
                int slot = 0;
                if (param.args.length > 0 && param.args[0] instanceof Integer)
                    slot = (Integer) param.args[0];
                paramSlot.set(slot);
                try {
                    String replacement = value.get(state);
                    if (replacement != null && !replacement.isEmpty()) param.setResult(replacement);
                } finally { paramSlot.remove(); }
            }
        }));
    }

    private static void setBuild(String field, String value) {
        if (value == null || value.isEmpty()) return;
        try { XposedHelpers.setStaticObjectField(Build.class, field, value); }
        catch (Throwable e) { XposedBridge.log("GhostViki: Build." + field + " not replaceable: " + e.getClass().getSimpleName()); }
    }

    private static void install(String name, Runnable action) {
        try { action.run(); }
        catch (Throwable e) { XposedBridge.log("GhostViki: " + name + " registration failed: " + e); }
    }
}
