package dev.ghostviki.core;

public final class HardwareReadouts {
    private HardwareReadouts() {}
    public static long ramBytes(DeviceProfile p) { return p == null ? 0 : p.ramGiB * (1L << 30); }
    public static long storageBytes(DeviceProfile p) { return p == null ? 0 : p.storageGB * 1_000_000_000L; }
    public static long cap(long original, long total) { return Math.max(0, Math.min(original, total)); }
    public static int width(DeviceProfile p, int oldWidth, int oldHeight) { return oldWidth > oldHeight ? p.height : p.width; }
    public static int height(DeviceProfile p, int oldWidth, int oldHeight) { return oldWidth > oldHeight ? p.width : p.height; }
}
