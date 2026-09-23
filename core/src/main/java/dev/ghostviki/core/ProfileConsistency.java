package dev.ghostviki.core;

import java.util.Locale;

/**
 * Validates one synthetic test profile before it is persisted or consumed by hooks.
 * This checks internal consistency only; it does not claim that every Android surface
 * is replaceable or that a target actually observed the saved value.
 */
public final class ProfileConsistency {
    private ProfileConsistency() {}

    public static Result check(Identity id) {
        if (id == null) return Result.fail("identity_missing");
        if (id.deviceProfile == null) return Result.fail("device_profile_missing");

        DeviceProfile p = id.deviceProfile;
        if (!p.key.equals(id.deviceProfile.key)
                || !p.name.equals(id.deviceName)
                || !p.brand.equals(id.brand)
                || !p.model.equals(id.model)
                || !p.manufacturer.equals(id.manufacturer)
                || !p.device.equals(id.device)) {
            return Result.fail("device_catalog_mismatch");
        }

        if (!id.androidId.matches("[0-9a-f]{16}")) return Result.fail("android_id");
        if (!id.serial.matches("[0-9A-F]{16}")) return Result.fail("serial");
        if (!id.advertisingId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            return Result.fail("advertising_id");
        if (!id.appSetId.matches("[0-9a-f]{32}")) return Result.fail("app_set_id");
        if (!id.firebaseInstallationId.matches("[A-Za-z0-9_-]{22}")) return Result.fail("fid");
        if (!id.fcmToken.matches("[A-Za-z0-9_-]{22}:[A-Za-z0-9_-]{120}")) return Result.fail("fcm");
        if (!id.gsfId.matches("[0-9a-f]{16}")) return Result.fail("gsf");
        if (!id.crashlyticsInstallationId.matches("[0-9a-f]{32}")) return Result.fail("crashlytics");
        if (!id.profileId.matches("[0-9a-f-]{36}")) return Result.fail("profile_id");
        if (!id.deviceId.matches("[0-9a-f]{16}")) return Result.fail("device_id");
        if (!mac(id.wifiMac) || !mac(id.bssid) || !mac(id.bluetoothMac)) return Result.fail("mac");
        if (id.wifiMac.equals(id.bssid)) return Result.fail("wifi_bssid_collision");
        if (!digits(id.imei1, 15) || !digits(id.imei2, 15)) return Result.fail("imei");
        if (!digits(id.imsi, 15)) return Result.fail("imsi");
        if (!digits(id.iccid, 20)) return Result.fail("iccid");

        // Firmware/OS metadata intentionally stays empty unless it is actually sourced.
        if (!id.buildId.isEmpty() || !id.hardware.isEmpty() || !id.product.isEmpty() || !id.fingerprint.isEmpty())
            return Result.fail("unsourced_firmware_metadata");

        return Result.ok();
    }

    public static boolean validStoredDeviceProfile(
            DeviceProfile p, String key, String name, String brand,
            String model, String manufacturer, String device) {
        return p != null
                && p.key.equals(key)
                && p.name.equals(name)
                && p.brand.equals(brand)
                && p.model.equals(model)
                && p.manufacturer.equals(manufacturer)
                && p.device.equals(device);
    }

    private static boolean digits(String value, int length) {
        return value != null && value.matches("[0-9]{" + length + "}");
    }

    private static boolean mac(String value) {
        if (value == null || !value.matches("([0-9A-F]{2}:){5}[0-9A-F]{2}")) return false;
        int first = Integer.parseInt(value.substring(0, 2), 16);
        return (first & 3) == 2; // locally administered, unicast
    }

    public static final class Result {
        public final boolean ok;
        public final String reason;

        private Result(boolean ok, String reason) {
            this.ok = ok;
            this.reason = reason;
        }

        static Result ok() { return new Result(true, "READY"); }
        static Result fail(String reason) {
            return new Result(false, reason.toUpperCase(Locale.ROOT));
        }
    }
}
