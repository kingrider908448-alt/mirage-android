package dev.ghostviki.core;

/** Facts from a model catalog, not a complete dump of a manufacturer's firmware. */
public final class DeviceProfile {
    public final String key, name, brand, manufacturer, model, device;
    public final String socModel, socManufacturer, specSource;
    public final int ramGiB, storageGB, width, height;

    DeviceProfile(String[] row) {
        if (row.length != 13) throw new IllegalArgumentException("Invalid catalog column count");
        key = row[0]; name = row[1]; brand = row[2]; manufacturer = row[3]; model = row[4]; device = row[5];
        socModel = row[6]; socManufacturer = row[7];
        ramGiB = Integer.parseInt(row[8]); storageGB = Integer.parseInt(row[9]);
        width = Integer.parseInt(row[10]); height = Integer.parseInt(row[11]); specSource = row[12];
        if (key.isBlank() || name.isBlank() || brand.isBlank() || manufacturer.isBlank() || model.isBlank() || device.isBlank())
            throw new IllegalArgumentException("Missing catalog identity");
        if (ramGiB < 0 || ramGiB > 32 || storageGB < 0 || storageGB > 2048
                || width < 0 || height < 0 || width > height || (width == 0) != (height == 0))
            throw new IllegalArgumentException("Invalid catalog specification");
        if ((!socModel.isEmpty() || ramGiB != 0 || storageGB != 0 || width != 0) && !specSource.startsWith("https://"))
            throw new IllegalArgumentException("Specifications require a source");
    }

    public boolean hasDisplay() { return width > 0 && height > 0; }
}
