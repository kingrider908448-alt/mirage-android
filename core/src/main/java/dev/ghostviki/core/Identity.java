package dev.ghostviki.core;

import java.security.SecureRandom;
import java.util.Locale;

/** Synthetic values generated for GhostViki test profiles. */
public final class Identity {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final char[] URL_SAFE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".toCharArray();

    public final String androidId;
    public final String serial;
    public final String advertisingId;
    public final String appSetId;
    public final String firebaseInstallationId;
    public final String fcmToken;
    public final String gsfId;
    public final String crashlyticsInstallationId;

    private Identity(String androidId, String serial, String advertisingId, String appSetId,
                     String firebaseInstallationId, String fcmToken, String gsfId,
                     String crashlyticsInstallationId) {
        this.androidId = androidId;
        this.serial = serial;
        this.advertisingId = advertisingId;
        this.appSetId = appSetId;
        this.firebaseInstallationId = firebaseInstallationId;
        this.fcmToken = fcmToken;
        this.gsfId = gsfId;
        this.crashlyticsInstallationId = crashlyticsInstallationId;
    }

    public static Identity generate() {
        return new Identity(
                randomHex(8),
                randomHex(8).toUpperCase(Locale.ROOT),
                randomUuid(),
                randomHex(16),
                randomUrlSafe(22),
                randomUrlSafe(22) + ":" + randomUrlSafe(120),
                randomHex(8),
                randomHex(16));
    }

    private static String randomUuid() {
        String hex = randomHex(16);
        return hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" +
                hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-" +
                hex.substring(20);
    }

    private static String randomHex(int count) {
        byte[] bytes = new byte[count];
        RANDOM.nextBytes(bytes);
        char[] text = new char[count * 2];
        for (int i = 0; i < count; i++) {
            text[i * 2] = HEX[(bytes[i] & 255) >>> 4];
            text[i * 2 + 1] = HEX[bytes[i] & 15];
        }
        return new String(text);
    }

    private static String randomUrlSafe(int count) {
        char[] text = new char[count];
        for (int i = 0; i < count; i++) text[i] = URL_SAFE[RANDOM.nextInt(URL_SAFE.length)];
        return new String(text);
    }
}
