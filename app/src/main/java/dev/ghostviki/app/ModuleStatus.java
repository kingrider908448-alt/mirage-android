package dev.ghostviki.app;

public final class ModuleStatus {
    private ModuleStatus() {}
    // The module replaces this only in its own process. It is not a target-app verdict.
    public static boolean isLoaded() { return false; }
}
