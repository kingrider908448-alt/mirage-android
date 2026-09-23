package dev.ghostviki.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class DeviceCatalog {
    private static final Map<String, DeviceProfile> BY_KEY = load();
    private static final List<DeviceProfile> ALL = Collections.unmodifiableList(new ArrayList<>(BY_KEY.values()));
    private static final SecureRandom RANDOM = new SecureRandom();
    private DeviceCatalog() {}

    public static List<DeviceProfile> all() { return ALL; }
    public static DeviceProfile find(String key) { return BY_KEY.get(key); }
    public static DeviceProfile next(String previousKey) { return next(previousKey, RANDOM); }

    /** A rotation never repeats the preceding model; prefer a different manufacturer too. */
    public static DeviceProfile next(String previousKey, Random random) {
        DeviceProfile previous = find(previousKey);
        List<DeviceProfile> candidates = new ArrayList<>();
        for (DeviceProfile p : ALL)
            if (previous == null || !p.manufacturer.equalsIgnoreCase(previous.manufacturer)) candidates.add(p);
        if (candidates.isEmpty()) for (DeviceProfile p : ALL) if (!p.key.equals(previousKey)) candidates.add(p);
        if (candidates.isEmpty()) throw new IllegalStateException("No alternative device profile");
        return candidates.get(random.nextInt(candidates.size()));
    }

    private static Map<String, DeviceProfile> load() {
        Map<String, DeviceProfile> profiles = new LinkedHashMap<>();
        try (var stream = DeviceCatalog.class.getResourceAsStream("device-profiles.tsv")) {
            if (stream == null) throw new IllegalStateException("Device catalog is missing");
            try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank() || line.startsWith("#")) continue;
                    DeviceProfile profile = new DeviceProfile(line.split("\\|", -1));
                    if (profiles.put(profile.key, profile) != null) throw new IllegalStateException("Duplicate catalog ID");
                }
            }
        } catch (java.io.IOException e) { throw new IllegalStateException("Cannot read device catalog", e); }
        if (profiles.size() != 100) throw new IllegalStateException("Expected 100 device profiles");
        return Collections.unmodifiableMap(profiles);
    }
}
