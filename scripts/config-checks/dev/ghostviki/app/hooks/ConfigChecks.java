package dev.ghostviki.app.hooks;

import android.content.Context;
import android.os.SystemClock;
import de.robv.android.xposed.XSharedPreferences;
import dev.ghostviki.app.ConfigStore;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class ConfigChecks {
    private static int passed, failed;
    private static void check(boolean ok, String label) {
        if (ok) { passed++; System.out.println("PASS " + label); }
        else { failed++; System.out.println("FAIL " + label); }
    }
    private static void publish(ConfigStore store) {
        XSharedPreferences.published = store.preferences.getAll();
        SystemClock.advance();
    }
    public static void main(String[] args) {
        Context active = new Context(true);
        ConfigStore store = new ConfigStore(active);
        check(active.cache.get("runtime").openingMode == Context.MODE_WORLD_READABLE,
                "Runtime first-open mode is WORLD_READABLE (Android caches that mode)");
        check(store.bridgeAvailable, "Active writer reports local bridge request accepted");
        ConfigStore inactive = new ConfigStore(new Context(false));
        check(!inactive.bridgeAvailable, "Private fallback never reports bridge available");
        check(inactive.setTargets(Set.of("example.offline")) && inactive.rotateIdentity(), "Offline local editing still works");

        String alpha = "example.reader.alpha", beta = "example.reader.beta";
        check(store.setTargets(Set.of(alpha, beta)) && store.rotateIdentity(), "Save and rotate two arbitrary targets (not Probe)");
        String first = store.preferences.getString("android_id:" + alpha, "");
        String second = store.preferences.getString("android_id:" + beta, "");
        check(!first.equals(second), "Selected apps have independent profiles");
        ConfigStore restarted = new ConfigStore(new Context(true, active.disk));
        check(first.equals(restarted.preferences.getString("android_id:" + alpha, "")), "Profile survives writer process restart");
        publish(store);
        HookConfig alphaReader = new HookConfig(alpha);
        HookConfig betaReader = new HookConfig(beta);
        check(alphaReader.get().identity && first.equals(alphaReader.get().androidId), "Read loaded map even if direct backing path is unreadable");
        check(betaReader.get().identity && second.equals(betaReader.get().androidId), "General hooks read a second non-Probe target");
        check(alphaReader.diagnostics().startsWith("PROFILE_READY schema=" + ConfigStore.SCHEMA + " generation=1"), "Diagnostic reports loaded schema and generation, not a spoof verdict");
        check(store.preferences.getString("bssid:" + alpha, "").equals(alphaReader.get().bssid), "BSSID reader matches saved UI profile");
        check(!new HookConfig("example.not.selected").get().identity, "Unselected app is untouched");
        check(new HookConfig("example.not.selected").diagnostics().startsWith("NOT_SELECTED"), "Unselected reason is explicit");
        check(store.rotateIdentity(), "Next rotation commits");
        publish(store);
        check(alphaReader.get().identity && !first.equals(alphaReader.get().androidId), "Existing reader observes new generation after refresh");
        store.setFlag("identity_enabled", false);
        publish(store);
        check(!alphaReader.get().identity, "Disable identity without rotating generation");
        check(alphaReader.diagnostics().startsWith("IDENTITY_DISABLED"), "Diagnostic updates even when generation has not changed");
        store.setFlag("identity_enabled", true);
        store.preferences.edit().putString("android_id:" + alpha, "invalid").commit();
        publish(store);
        check(!alphaReader.get().identity && betaReader.get().identity, "Bad profile fails closed for only its target");
        check(alphaReader.diagnostics().startsWith("INVALID_PROFILE"), "Invalid profile has its own diagnostic");
        store.setTargets(Set.of(alpha)); publish(store);
        check(!betaReader.get().identity, "Removing a selected target disables its existing reader");
        check(betaReader.diagnostics().startsWith("NOT_SELECTED"), "Removal is logged without requiring an identity rotation");
        XSharedPreferences.published = Map.of(); SystemClock.advance();
        check(!alphaReader.get().identity, "Missing configuration fails closed");
        check(alphaReader.diagnostics().startsWith("CONFIG_UNAVAILABLE"), "Missing config is distinct from an unselected target");
        XSharedPreferences.failRead = true; SystemClock.advance();
        check(!alphaReader.get().identity, "Reader exceptions fail closed");
        check(alphaReader.diagnostics().startsWith("CONFIG_READ_ERROR"), "Read failure exposes type without dumping identity values");
        XSharedPreferences.failRead = false;
        store.rotateIdentity(); publish(store);
        check(alphaReader.get().identity, "Reader recovers after a transient failure");
        check(de.robv.android.xposed.XposedBridge.logs.stream().noneMatch(line -> line.contains(first) || line.contains(second)),
                "Diagnostics do not dump saved Android IDs");
        active.cache.get("runtime").failWrites = true;
        check(!store.rotateIdentity(), "Failed rotation commit is reported to UI");

        Map<String, Map<String, Object>> old = new HashMap<>();
        old.put("runtime", new HashMap<>(Map.of("schema", 2, "generation", 8L, "targets", Set.of(alpha),
                "android_id:" + alpha, "1234567890abcdef", "serial:" + alpha, "0123456789ABCDEF")));
        old.put("draft", new HashMap<>(Map.of("generation", 1L, "targets", Set.of(beta), "android_id:" + alpha, "0000000000000000")));
        ConfigStore upgraded = new ConfigStore(new Context(true, old));
        check(upgraded.targets().equals(Set.of(alpha)) && upgraded.preferences.getLong("generation", 0) == 8,
                "Migration does not overwrite current selection or generation with stale draft");
        check("1234567890abcdef".equals(upgraded.preferences.getString("android_id:" + alpha, "")),
                "Migration preserves existing identity");
        check(upgraded.preferences.contains("wifi_mac:" + alpha), "Migration backfills missing identity fields");
        check(upgraded.preferences.contains("bssid:" + alpha), "Upgrade backfills a dedicated BSSID");
        check(upgraded.preferences.getInt("schema", 0) == ConfigStore.SCHEMA, "Upgrade publishes current schema");
        long revision = upgraded.preferences.getLong("writer_revision", 0);
        ConfigStore reopened = new ConfigStore(new Context(true, old));
        check(reopened.preferences.getLong("writer_revision", 0) > revision, "Writer startup forces a new disk commit to repair old file mode");
        System.out.println("Config checks: " + passed + " passed; " + failed + " failed (JVM fixtures, not a device test)");
        if (failed > 0) throw new AssertionError(failed + " config regression checks failed");
    }
}
