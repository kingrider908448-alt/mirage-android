package dev.ghostviki.app.hooks;

import android.provider.Settings;
import dev.ghostviki.core.ProfileProperties;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Applies to every selected target. No Probe package or detector-result special case. */
final class DeviceNameHooks {
    private DeviceNameHooks() {}

    static void install(HookConfig config, ClassLoader loader) {
        settings(Settings.Global.class, "device_name", config);
        settings(Settings.System.class, "device_name", config);
        settings(Settings.Secure.class, "device_name", config);
        settings(Settings.Secure.class, "bluetooth_name", config);
        optional("android.bluetooth.BluetoothAdapter", loader, "getName", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                // Null can mean the adapter is off/unavailable. Do not invent a powered-on adapter.
                if (param.getResult() instanceof String) replaceName(param, config);
            }
        });
        optional("android.os.SystemProperties", loader, "get", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (param.hasThrowable() || !(param.getResult() instanceof String)
                        || param.args.length == 0 || !(param.args[0] instanceof String)) return;
                HookConfig.Snapshot state = config.get();
                if (!state.identity) return;
                String value = ProfileProperties.replacement(state.deviceProfile, (String) param.args[0]);
                if (value != null && !value.isEmpty()) param.setResult(value);
            }
        });
    }

    private static void settings(Class<?> table, String key, HookConfig config) {
        for (String method : new String[]{"getString", "getStringForUser"}) {
            register(table, method, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.args.length > 1 && key.equals(param.args[1])) replaceName(param, config);
                }
            });
        }
    }

    private static void replaceName(XC_MethodHook.MethodHookParam param, HookConfig config) {
        // A missing Settings device_name is an unset friendly name, not an access grant.
        // Preserve permission exceptions and unexpected result types.
        if (param.hasThrowable() || (param.getResult() != null && !(param.getResult() instanceof String))) return;
        HookConfig.Snapshot state = config.get();
        if (state.identity && state.deviceProfile != null && state.deviceName.equals(state.deviceProfile.name))
            param.setResult(state.deviceName);
    }

    private static void optional(String name, ClassLoader loader, String method, XC_MethodHook callback) {
        try {
            Class<?> type = XposedHelpers.findClassIfExists(name, loader);
            if (type != null) register(type, method, callback);
        } catch (Throwable e) { XposedBridge.log("GhostViki: name adapter unavailable " + name + ": " + e.getClass().getSimpleName()); }
    }

    private static void register(Class<?> type, String method, XC_MethodHook callback) {
        try { XposedBridge.hookAllMethods(type, method, callback); }
        catch (Throwable e) { XposedBridge.log("GhostViki: " + type.getName() + "." + method + " unavailable: " + e.getClass().getSimpleName()); }
    }
}
