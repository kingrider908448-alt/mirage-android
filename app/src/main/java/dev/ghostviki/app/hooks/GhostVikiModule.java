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
        // Never install into system_server, zygote, or a shared system UID.
        if (param.appInfo == null || param.appInfo.uid % 100000 < 10000
                || "android".equals(param.packageName)) return;
        HookConfig config = new HookConfig(param.packageName);
        install("identity", () -> installIdentity(config, param.classLoader));
        install("root signals", () -> RootHooks.install(config));
        install("location", () -> LocationHooks.install(config, param.classLoader));
        XposedBridge.log("GhostViki: adapters registered for " + param.packageName
                + "; verify results in the target app (registration is not a passing detector result)");
    }

    private void installIdentity(HookConfig config, ClassLoader classLoader) {
        XposedBridge.hookAllMethods(Settings.Secure.class, "getString", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                HookConfig.Snapshot state = config.get();
                if (state.identity && !param.hasThrowable() && param.args.length > 1
                        && Settings.Secure.ANDROID_ID.equals(param.args[1]) && param.getResult() != null) {
                    param.setResult(state.androidId);
                }
            }
        });
        // Keep platform permission errors intact: this does not grant serial-number access.
        XposedBridge.hookAllMethods(Build.class, "getSerial", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                HookConfig.Snapshot state = config.get();
                if (state.identity && !param.hasThrowable() && param.getResult() != null
                        && !Build.UNKNOWN.equals(param.getResult())) param.setResult(state.serial);
            }
        });

        hookString(TelephonyManager.class, "getDeviceId", config, s -> s.deviceId);
        hookString(TelephonyManager.class, "getImei", config, s -> {
            if (s.imei2.isEmpty() || paramSlot.get() == 0) return s.imei1;
            return s.imei2;
        });
        hookString(TelephonyManager.class, "getSubscriberId", config, s -> s.imsi);
        hookString(TelephonyManager.class, "getSimSerialNumber", config, s -> s.iccid);

        Class<?> wifiInfo = XposedHelpers.findClassIfExists("android.net.wifi.WifiInfo", classLoader);
        if (wifiInfo != null) {
            hookString(wifiInfo, "getMacAddress", config, s -> s.wifiMac);
            hookString(wifiInfo, "getBSSID", config, s -> s.wifiMac);
        }
        Class<?> bluetooth = XposedHelpers.findClassIfExists("android.bluetooth.BluetoothAdapter", classLoader);
        if (bluetooth != null) hookString(bluetooth, "getAddress", config, s -> s.bluetoothMac);

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
        }
    }

    private static final ThreadLocal<Integer> paramSlot = ThreadLocal.withInitial(() -> 0);

    private interface Value {
        String get(HookConfig.Snapshot state);
    }

    private static void hookString(Class<?> type, String method, HookConfig config, Value value) {
        XposedBridge.hookAllMethods(type, method, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                HookConfig.Snapshot state = config.get();
                if (!state.identity || param.hasThrowable() || param.getResult() == null) return;
                int slot = 0;
                if (param.args.length > 0 && param.args[0] instanceof Integer)
                    slot = (Integer) param.args[0];
                paramSlot.set(slot);
                String replacement = value.get(state);
                paramSlot.remove();
                if (replacement != null && !replacement.isEmpty()) param.setResult(replacement);
            }
        });
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
