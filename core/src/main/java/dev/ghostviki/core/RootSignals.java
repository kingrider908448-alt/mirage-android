package dev.ghostviki.core;

import java.util.Set;

/** Deliberately narrow exact matches; these do not cover native or kernel probes. */
public final class RootSignals {
    private RootSignals() {}
    private static final Set<String> PATHS = Set.of(
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/vendor/bin/su",
            "/su/bin/su", "/data/local/bin/su", "/data/local/xbin/su", "/data/local/su");
    private static final Set<String> PACKAGES = Set.of(
            "com.topjohnwu.magisk", "me.weishu.kernelsu", "me.bmax.apatch",
            "eu.chainfire.supersu", "com.noshufou.android.su");

    public static boolean isSuPath(String path) {
        if (path == null || !path.startsWith("/")) return false;
        try { return PATHS.contains(java.nio.file.Paths.get(path).normalize().toString()); }
        catch (RuntimeException ignored) { return false; }
    }
    public static boolean isRootPackage(String name) { return name != null && PACKAGES.contains(name); }
}
