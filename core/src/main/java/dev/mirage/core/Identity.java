package dev.mirage.core;

import java.security.SecureRandom;

/** Synthetic values, generated once per profile change and persisted by the app. */
public final class Identity {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    public final String androidId;
    public final String serial;

    private Identity(String androidId, String serial) {
        this.androidId = androidId;
        this.serial = serial;
    }

    public static Identity generate() {
        return new Identity(randomHex(8), randomHex(8).toUpperCase(java.util.Locale.ROOT));
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
}
