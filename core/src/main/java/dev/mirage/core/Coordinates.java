package dev.ghostviki.core;

public final class Coordinates {
    public final double latitude;
    public final double longitude;

    public Coordinates(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Latitude must be -90 to 90; longitude -180 to 180.");
        }
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public static Coordinates parse(String latitude, String longitude) {
        try {
            return new Coordinates(Double.parseDouble(latitude.trim()), Double.parseDouble(longitude.trim()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Enter both coordinates as numbers, for example 28.6139.");
        }
    }
}
