package dev.ghostviki.core;

/** Exact Java SystemProperties coverage; never invent stock build/security properties. */
public final class ProfileProperties {
    private ProfileProperties() {}
    public static String replacement(DeviceProfile profile, String key) {
        if (profile == null || key == null) return null;
        switch (key) {
            case "ro.product.marketname": case "ro.product.market_name":
            case "ro.vendor.oplus.market.name": return profile.name;
            case "ro.soc.model": return profile.socModel.isEmpty() ? null : profile.socModel;
            case "ro.soc.manufacturer": return profile.socManufacturer.isEmpty() ? null : profile.socManufacturer;
            default: break;
        }
        for (String prefix : new String[]{"ro.product.", "ro.product.system.", "ro.product.vendor.",
                "ro.product.product.", "ro.product.odm.", "ro.product.system_ext."}) {
            if (key.equals(prefix + "brand")) return profile.brand;
            if (key.equals(prefix + "manufacturer")) return profile.manufacturer;
            if (key.equals(prefix + "model")) return profile.model;
            if (key.equals(prefix + "device")) return profile.device;
        }
        return null;
    }
}
