package dev.ghostviki.app.hooks;

import android.os.Build;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Static Build values can only be applied when a target process starts. */
final class BuildProfileHooks {
    private BuildProfileHooks() {}

    static void apply(HookConfig.Snapshot state) {
        if (!state.identity) return;
        set("SERIAL", state.serial);
        if (!state.overrideDevice) return;
        set("BRAND", state.brand);
        set("MODEL", state.model);
        set("MANUFACTURER", state.manufacturer);
        set("DEVICE", state.device);
        if (state.deviceProfile != null) {
            set("SOC_MODEL", state.deviceProfile.socModel);
            set("SOC_MANUFACTURER", state.deviceProfile.socManufacturer);
        }
    }

    private static void set(String field, String value) {
        if (value == null || value.isEmpty()) return;
        try { XposedHelpers.setStaticObjectField(Build.class, field, value); }
        catch (Throwable e) { XposedBridge.log("GhostViki: Build." + field + " not replaceable: " + e.getClass().getSimpleName()); }
    }
}
