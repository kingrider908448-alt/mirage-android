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
    public final String profileId;
    public final String deviceId;
    public final String bootId;
    public final String wifiMac;
    public final String bssid;
    public final String bluetoothMac;
    public final String imei1;
    public final String imei2;
    public final String imsi;
    public final String iccid;
    public final String buildId;
    public final String hardware;
    public final String brand;
    public final String model;
    public final String manufacturer;
    public final String device;
    public final String product;
    public final String fingerprint;

    private Identity(String androidId, String serial, String advertisingId, String appSetId,
                     String firebaseInstallationId, String fcmToken, String gsfId,
                     String crashlyticsInstallationId, String profileId, String deviceId,
                     String bootId, String wifiMac, String bssid, String bluetoothMac, String imei1,
                     String imei2, String imsi, String iccid, String buildId, String hardware,
                     String brand, String model, String manufacturer, String device,
                     String product, String fingerprint) {
        this.androidId = androidId;
        this.serial = serial;
        this.advertisingId = advertisingId;
        this.appSetId = appSetId;
        this.firebaseInstallationId = firebaseInstallationId;
        this.fcmToken = fcmToken;
        this.gsfId = gsfId;
        this.crashlyticsInstallationId = crashlyticsInstallationId;
        this.profileId = profileId;
        this.deviceId = deviceId;
        this.bootId = bootId;
        this.wifiMac = wifiMac;
        this.bssid = bssid;
        this.bluetoothMac = bluetoothMac;
        this.imei1 = imei1;
        this.imei2 = imei2;
        this.imsi = imsi;
        this.iccid = iccid;
        this.buildId = buildId;
        this.hardware = hardware;
        this.brand = brand;
        this.model = model;
        this.manufacturer = manufacturer;
        this.device = device;
        this.product = product;
        this.fingerprint = fingerprint;
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
                randomHex(16),
                randomUuid(),
                randomHex(8),
                randomUuid(),
                randomMac(),
                randomMac(),
                randomMac(),
                randomDigits(15),
                randomDigits(15),
                randomDigits(15),
                randomDigits(20),
                "GV" + randomHex(7).toUpperCase(Locale.ROOT),
                "gv_" + randomHex(2),
                "GhostViki",
                "GV-" + randomHex(2).toUpperCase(Locale.ROOT),
                "GhostViki Labs",
                "gv_" + randomHex(2),
                "gv_" + randomHex(2),
                "ghostviki/gv/gv:16/GV" + randomHex(7).toUpperCase(Locale.ROOT)
                        + "/" + randomDigits(8) + ":user/release-keys");
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

    private static String randomMac() {
        byte[] bytes = new byte[6];
        RANDOM.nextBytes(bytes);
        bytes[0] = (byte) ((bytes[0] | 0x02) & 0xfe);
        return String.format(Locale.ROOT, "%02X:%02X:%02X:%02X:%02X:%02X",
                bytes[0] & 255, bytes[1] & 255, bytes[2] & 255,
                bytes[3] & 255, bytes[4] & 255, bytes[5] & 255);
    }

    private static String randomDigits(int count) {
        StringBuilder out = new StringBuilder(count);
        for (int i = 0; i < count; i++) out.append((char) ('0' + RANDOM.nextInt(10)));
        return out.toString();
    }

    private static String randomUrlSafe(int count) {
        char[] text = new char[count];
        for (int i = 0; i < count; i++) text[i] = URL_SAFE[RANDOM.nextInt(URL_SAFE.length)];
        return new String(text);
    }
}
