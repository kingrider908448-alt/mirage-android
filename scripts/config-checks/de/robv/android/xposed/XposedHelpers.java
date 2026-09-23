package de.robv.android.xposed;

public final class XposedHelpers {
    public static Class<?> findClassIfExists(String name, ClassLoader loader) {
        try { return Class.forName(name, false, loader); }
        catch (ClassNotFoundException e) { return null; }
    }
}
