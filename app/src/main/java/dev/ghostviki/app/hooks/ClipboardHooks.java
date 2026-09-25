package dev.ghostviki.app.hooks;

import android.content.ClipboardManager;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Opt-in, per-target clipboard read blocking. Does not clear or change the shared clipboard. */
final class ClipboardHooks {
    private ClipboardHooks() {}

    static void install(HookConfig config) {
        for (String method : new String[]{"getPrimaryClip", "getPrimaryClipDescription", "getText", "hasPrimaryClip", "hasText"}) {
            boolean booleanResult = method.startsWith("has");
            try {
                XposedBridge.hookAllMethods(ClipboardManager.class, method, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        if (config.get().blockClipboard) {
                            // Stop BEFORE the service read. Filtering afterwards would already have
                            // fetched the private data (and could trigger Android's clipboard notice).
                            param.setResult(booleanResult ? Boolean.FALSE : null);
                        }
                    }
                });
            } catch (Throwable e) {
                XposedBridge.log("GhostViki: clipboard " + method + " unavailable: " + e.getClass().getSimpleName());
            }
        }
    }
}
