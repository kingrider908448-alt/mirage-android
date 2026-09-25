package de.robv.android.xposed;

public final class XposedHelpers {
    public static void setStaticObjectField(Class<?> type, String name, Object value) {
        try { type.getField(name).set(null, value); }
        catch (ReflectiveOperationException e) { throw new IllegalArgumentException(e); }
    }
    public static Class<?> findClassIfExists(String name, ClassLoader loader) {
        try { return Class.forName(name, false, loader); }
        catch (ClassNotFoundException e) { return null; }
    }
}
