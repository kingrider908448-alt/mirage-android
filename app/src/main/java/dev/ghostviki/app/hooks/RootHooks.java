package dev.ghostviki.app.hooks;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import dev.ghostviki.core.RootSignals;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

final class RootHooks {
    private RootHooks() {}
    static void install(HookConfig config) {
        XC_MethodHook fileCheck = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (!param.hasThrowable() && Boolean.TRUE.equals(param.getResult()) && config.get().hideFiles
                        && RootSignals.isSuPath(((File) param.thisObject).getAbsolutePath())) param.setResult(false);
            }
        };
        for (String name : new String[]{"exists", "isFile", "canExecute"})
            XposedBridge.hookAllMethods(File.class, name, fileCheck);

        Class<?> manager = XposedHelpers.findClass("android.app.ApplicationPackageManager", null);
        XC_MethodHook packageCheck = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (config.get().hidePackages && !param.hasThrowable() && param.args.length > 0
                        && param.args[0] instanceof String && RootSignals.isRootPackage((String) param.args[0]))
                    param.setThrowable(new PackageManager.NameNotFoundException((String) param.args[0]));
            }
        };
        // Hook real overloads on the implementation class, rather than an assumed signature.
        for (String name : new String[]{"getPackageInfo", "getApplicationInfo"})
            XposedBridge.hookAllMethods(manager, name, packageCheck);
        XC_MethodHook packageList = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (!config.get().hidePackages || param.hasThrowable() || !(param.getResult() instanceof List<?>)) return;
                List<Object> filtered = new ArrayList<>();
                for (Object item : (List<?>) param.getResult()) {
                    String name = item instanceof PackageInfo ? ((PackageInfo) item).packageName
                            : item instanceof ApplicationInfo ? ((ApplicationInfo) item).packageName : null;
                    if (!RootSignals.isRootPackage(name)) filtered.add(item);
                }
                param.setResult(filtered);
            }
        };
        for (String name : new String[]{"getInstalledPackages", "getInstalledApplications"})
            XposedBridge.hookAllMethods(manager, name, packageList);
    }
}
