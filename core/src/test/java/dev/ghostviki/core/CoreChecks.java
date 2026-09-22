package dev.ghostviki.core;

import java.util.HashSet;
import java.util.Set;

public final class CoreChecks {
    private static int assertions;
    public static void main(String[] args) {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            Identity id = Identity.generate();
            check(id.androidId.matches("[0-9a-f]{16}"), "Android ID must be a 64-bit hex string");
            check(id.serial.matches("[0-9A-F]{16}"), "serial format");
            check(id.advertisingId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"), "advertising ID format");
            check(id.appSetId.matches("[0-9a-f]{32}"), "app set ID format");
            check(id.firebaseInstallationId.matches("[A-Za-z0-9_-]{22}"), "FID format");
            check(id.fcmToken.matches("[A-Za-z0-9_-]{22}:[A-Za-z0-9_-]{120}"), "FCM token format");
            check(id.gsfId.matches("[0-9a-f]{16}"), "GSF ID format");
            check(id.crashlyticsInstallationId.matches("[0-9a-f]{32}"), "Crashlytics ID format");
            check(seen.add(id.androidId), "unexpected identifier collision");
        }
        check(Coordinates.parse(" -90 ", "180").latitude == -90, "valid coordinate bounds");
        check(Coordinates.parse("0", "0").longitude == 0, "zero is a valid location");
        reject("NaN", "0"); reject("0", "Infinity"); reject("90.001", "0");
        reject("0", "-180.01"); reject("", "0"); reject("1,2", "0");
        check(RootSignals.isSuPath("/system/xbin/su"), "SU signal");
        check(RootSignals.isSuPath("/system/bin/../xbin/su"), "normalized path");
        check(!RootSignals.isSuPath("/data/user/0/example/files/su"), "do not hide unrelated app data");
        check(!RootSignals.isSuPath("/system/bin/surfaceflinger"), "do not hide unrelated system files");
        check(!RootSignals.isSuPath(null), "null is not a root artifact");
        check(RootSignals.isRootPackage("com.topjohnwu.magisk"), "known package");
        check(!RootSignals.isRootPackage("com.topjohnwu.magisk.example"), "no substring matches");
        System.out.println("PASS: " + assertions + " core assertions");
    }
    private static void reject(String lat, String lon) {
        boolean failed = false;
        try { Coordinates.parse(lat, lon); } catch (IllegalArgumentException e) { failed = true; }
        check(failed, "invalid coordinates accepted: " + lat + ", " + lon);
    }
    private static void check(boolean ok, String message) {
        assertions++;
        if (!ok) throw new AssertionError(message);
    }
}
