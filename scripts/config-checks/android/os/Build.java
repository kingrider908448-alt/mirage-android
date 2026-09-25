package android.os;

/** Writable fields for executing the production BuildProfileHooks on the JVM. */
public final class Build {
    public static String BRAND, MODEL, MANUFACTURER, DEVICE, SOC_MODEL, SOC_MANUFACTURER, SERIAL;
    public static String ID, HARDWARE, PRODUCT, FINGERPRINT;
}
