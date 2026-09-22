package dev.mirage.app.hooks;

import android.os.Build;
import android.provider.Settings;
import dev.mirage.app.ConfigStore;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class MirageModule implements IXposedHookLoadPackage {
    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam param) {
        if (ConfigStore.PACKAGE.equals(param.packageName)) {
            XposedHelpers.findAndHookMethod("dev.mirage.app.ModuleStatus", param.classLoader,
                    "isLoaded", XC_MethodReplacement.returnConstant(true));
            return;
        }
        // Never install into system_server, zygote, or a shared system UID.
        if (param.appInfo == null || param.appInfo.uid % 100000 < 10000
                || "android".equals(param.packageName)) return;
        HookConfig config = new HookConfig(param.packageName);
        install("identity", () -> installIdentity(config));
        install("root signals", () -> RootHooks.install(config));
        install("location", () -> LocationHooks.install(config, param.classLoader));
        XposedBridge.log("Mirage: adapters registered for " + param.packageName
                + "; verify results in the target app (registration is not a passing detector result)");
    }

    private void installIdentity(HookConfig config) {
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
    }

    private static void install(String name, Runnable action) {
        try { action.run(); }
        catch (Throwable e) { XposedBridge.log("Mirage: " + name + " registration failed: " + e); }
    }
}
