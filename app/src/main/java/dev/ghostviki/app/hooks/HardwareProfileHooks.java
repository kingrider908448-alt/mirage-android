package dev.ghostviki.app.hooks;

import android.app.ActivityManager;
import android.app.usage.StorageStatsManager;
import android.graphics.Point;
import android.os.storage.StorageManager;
import android.util.DisplayMetrics;
import android.view.Display;
import dev.ghostviki.core.DeviceProfile;
import dev.ghostviki.core.HardwareReadouts;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Scoped Java readouts only. Does not change allocations, filesystems, or native display state. */
final class HardwareProfileHooks {
    private HardwareProfileHooks() {}

    static void install(HookConfig config) {
        register(ActivityManager.class, "getMemoryInfo", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                DeviceProfile p = profile(config, param);
                long total = HardwareReadouts.ramBytes(p);
                if (total == 0 || param.args.length == 0 || !(param.args[0] instanceof ActivityManager.MemoryInfo)) return;
                ActivityManager.MemoryInfo info = (ActivityManager.MemoryInfo) param.args[0];
                info.totalMem = total;
                info.availMem = HardwareReadouts.cap(info.availMem, total);
                info.threshold = HardwareReadouts.cap(info.threshold, total);
                info.lowMemory = info.availMem < info.threshold;
            }
        });
        register(StorageStatsManager.class, "getTotalBytes", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                DeviceProfile p = profile(config, param);
                long total = HardwareReadouts.storageBytes(p);
                if (total > 0 && param.getResult() instanceof Long && param.args.length > 0
                        && StorageManager.UUID_DEFAULT.equals(param.args[0])) param.setResult(total);
            }
        });
        register(StorageStatsManager.class, "getFreeBytes", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                DeviceProfile p = profile(config, param);
                long total = HardwareReadouts.storageBytes(p);
                if (total > 0 && param.getResult() instanceof Long && param.args.length > 0
                        && StorageManager.UUID_DEFAULT.equals(param.args[0]))
                    param.setResult(HardwareReadouts.cap((Long) param.getResult(), total));
            }
        });
        for (String method : new String[]{"getMetrics", "getRealMetrics", "getSize", "getRealSize"}) {
            register(Display.class, method, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    DeviceProfile p = profile(config, param);
                    if (p == null || !p.hasDisplay() || !(param.thisObject instanceof Display)
                            || ((Display) param.thisObject).getDisplayId() != Display.DEFAULT_DISPLAY || param.args.length == 0) return;
                    if (param.args[0] instanceof Point) {
                        Point size = (Point) param.args[0];
                        int width = size.x, height = size.y;
                        size.x = HardwareReadouts.width(p, width, height);
                        size.y = HardwareReadouts.height(p, width, height);
                    } else if (param.args[0] instanceof DisplayMetrics) {
                        DisplayMetrics metrics = (DisplayMetrics) param.args[0];
                        int width = metrics.widthPixels, height = metrics.heightPixels;
                        metrics.widthPixels = HardwareReadouts.width(p, width, height);
                        metrics.heightPixels = HardwareReadouts.height(p, width, height);
                    }
                }
            });
        }
    }

    private static DeviceProfile profile(HookConfig config, XC_MethodHook.MethodHookParam param) {
        if (param.hasThrowable()) return null;
        HookConfig.Snapshot state = config.get();
        return state.overrideDevice ? state.deviceProfile : null;
    }

    private static void register(Class<?> type, String name, XC_MethodHook hook) {
        try { XposedBridge.hookAllMethods(type, name, hook); }
        catch (Throwable e) { XposedBridge.log("GhostViki: hardware adapter " + type.getSimpleName() + "." + name + " unavailable: " + e.getClass().getSimpleName()); }
    }
}
