package de.robv.android.xposed;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public final class XposedBridge {
    public static final List<String> logs = new ArrayList<>();
    public static void log(String message) { logs.add(message); }
    private static final Map<String, List<XC_MethodHook>> hooks = new HashMap<>();
    public static void clearHooks() { hooks.clear(); }
    public interface OriginalCall { Object call() throws Throwable; }
    public static XC_MethodHook.MethodHookParam invoke(Class<?> type, String method, OriginalCall original) throws Throwable {
        XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam();
        param.args = new Object[0];
        List<XC_MethodHook> registered = hooks.get(type.getName() + "." + method);
        if (registered == null) throw new AssertionError("No hook registered for " + type + "." + method);
        for (XC_MethodHook hook : registered) {
            hook.beforeHookedMethod(param);
            if (param.returnEarly) break;
        }
        if (!param.returnEarly) {
            try { param.setResult(original.call()); }
            catch (Throwable error) { param.setThrowable(error); }
        }
        for (int i = registered.size() - 1; i >= 0; i--) registered.get(i).afterHookedMethod(param);
        return param;
    }
    public static void hookAllMethods(Class<?> type, String method, XC_MethodHook hook) {
        hooks.computeIfAbsent(type.getName() + "." + method, key -> new ArrayList<>()).add(hook);
    }
    public static XC_MethodHook.MethodHookParam after(Class<?> type, String method, Object receiver,
            Object[] args, Object result, Throwable error) throws Throwable {
        XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam();
        param.thisObject = receiver; param.args = args;
        if (error == null) param.setResult(result); else param.setThrowable(error);
        List<XC_MethodHook> registered = hooks.get(type.getName() + "." + method);
        if (registered == null) throw new AssertionError("No hook registered for " + type + "." + method);
        for (XC_MethodHook hook : registered) hook.afterHookedMethod(param);
        return param;
    }
}
