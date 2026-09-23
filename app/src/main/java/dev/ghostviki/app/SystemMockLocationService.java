package dev.ghostviki.app;

import android.app.*;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationManager;
import android.location.provider.ProviderProperties;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;

public final class SystemMockLocationService extends Service {
    public static final String ACTION_START = "dev.ghostviki.action.START_SYSTEM_LOCATION";
    public static final String ACTION_STOP = "dev.ghostviki.action.STOP_SYSTEM_LOCATION";
    public static final String EXTRA_LATITUDE = "latitude";
    public static final String EXTRA_LONGITUDE = "longitude";
    public static final String EXTRA_ALTITUDE = "altitude";
    public static final String EXTRA_ACCURACY = "accuracy";
    public static final String EXTRA_LABEL = "label";

    public static final String PREFS = "system_mock_location";
    public static final String KEY_ACTIVE = "active";

    private static final String CHANNEL = "ghostviki_system_location";
    private static final int NOTIFICATION_ID = 4042;
    private static final long UPDATE_MS = 1000L;
    private static final String[] PROVIDERS = new String[]{
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.FUSED_PROVIDER
    };

    private LocationManager manager;
    private Handler handler;
    private double latitude;
    private double longitude;
    private double altitude;
    private float accuracy;
    private String label;
    private boolean running;

    private final Runnable pulse = new Runnable() {
        @Override public void run() {
            if (!running) return;
            if (!pushLocation()) {
                stopMock(true);
                return;
            }
            handler.postDelayed(this, UPDATE_MS);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        manager = getSystemService(LocationManager.class);
        handler = new Handler(getMainLooper());
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopMock(false);
            return START_NOT_STICKY;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (intent != null && ACTION_START.equals(intent.getAction())) {
            latitude = intent.getDoubleExtra(EXTRA_LATITUDE, Double.NaN);
            longitude = intent.getDoubleExtra(EXTRA_LONGITUDE, Double.NaN);
            altitude = intent.getDoubleExtra(EXTRA_ALTITUDE, 0.0);
            accuracy = intent.getFloatExtra(EXTRA_ACCURACY, 3.0f);
            label = intent.getStringExtra(EXTRA_LABEL);
            if (label == null || label.isBlank()) label = "Selected location";

            if (!valid(latitude, longitude)) {
                stopSelf();
                return START_NOT_STICKY;
            }

            prefs.edit()
                    .putLong("latitude_bits", Double.doubleToRawLongBits(latitude))
                    .putLong("longitude_bits", Double.doubleToRawLongBits(longitude))
                    .putLong("altitude_bits", Double.doubleToRawLongBits(altitude))
                    .putFloat("accuracy", accuracy)
                    .putString("label", label)
                    .putBoolean(KEY_ACTIVE, true)
                    .apply();
        } else {
            latitude = Double.longBitsToDouble(prefs.getLong("latitude_bits",
                    Double.doubleToRawLongBits(Double.NaN)));
            longitude = Double.longBitsToDouble(prefs.getLong("longitude_bits",
                    Double.doubleToRawLongBits(Double.NaN)));
            altitude = Double.longBitsToDouble(prefs.getLong("altitude_bits",
                    Double.doubleToRawLongBits(0.0)));
            accuracy = prefs.getFloat("accuracy", 3.0f);
            label = prefs.getString("label", "Selected location");
            if (!prefs.getBoolean(KEY_ACTIVE, false) || !valid(latitude, longitude)) {
                stopSelf();
                return START_NOT_STICKY;
            }
        }

        // Keep raw double values in a representation SharedPreferences supports.
        prefs.edit()
                .putLong("latitude_bits", Double.doubleToRawLongBits(latitude))
                .putLong("longitude_bits", Double.doubleToRawLongBits(longitude))
                .putLong("altitude_bits", Double.doubleToRawLongBits(altitude))
                .putFloat("accuracy", accuracy)
                .putString("label", label)
                .putBoolean(KEY_ACTIVE, true)
                .apply();

        startAsForeground();
        startMock();
        return START_STICKY;
    }

    private void startAsForeground() {
        Notification notification = notification(
                "System location active",
                label + "  •  " + latitude + ", " + longitude);

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void startMock() {
        handler.removeCallbacks(pulse);
        if (!installProviders()) {
            stopMock(true);
            return;
        }
        running = true;
        pulse.run();
    }

    private boolean installProviders() {
        if (manager == null) return false;
        try {
            for (String provider : PROVIDERS) {
                try {
                    manager.addTestProvider(provider, properties(provider));
                } catch (IllegalArgumentException ignored) {
                    // A provider can be absent or vendor-modified; continue with the others.
                }
                try {
                    manager.setTestProviderEnabled(provider, true);
                } catch (IllegalArgumentException ignored) {
                }
            }
            return true;
        } catch (SecurityException denied) {
            return false;
        }
    }

    private ProviderProperties properties(String provider) {
        ProviderProperties.Builder builder = new ProviderProperties.Builder()
                .setAccuracy(ProviderProperties.ACCURACY_FINE)
                .setPowerUsage(ProviderProperties.POWER_USAGE_LOW)
                .setHasAltitudeSupport(true)
                .setHasBearingSupport(true)
                .setHasSpeedSupport(true);
        if (LocationManager.GPS_PROVIDER.equals(provider))
            builder.setHasSatelliteRequirement(true);
        if (LocationManager.NETWORK_PROVIDER.equals(provider))
            builder.setHasNetworkRequirement(true);
        return builder.build();
    }

    private boolean pushLocation() {
        if (manager == null) return false;
        boolean any = false;
        try {
            for (String provider : PROVIDERS) {
                try {
                    Location location = new Location(provider);
                    location.setLatitude(latitude);
                    location.setLongitude(longitude);
                    location.setAltitude(altitude);
                    location.setAccuracy(Math.max(0.5f, accuracy));
                    location.setSpeed(0f);
                    location.setBearing(0f);
                    location.setTime(System.currentTimeMillis());
                    location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
                    manager.setTestProviderLocation(provider, location);
                    any = true;
                } catch (IllegalArgumentException ignored) {
                }
            }
            return any;
        } catch (SecurityException denied) {
            return false;
        }
    }

    private void stopMock(boolean permissionProblem) {
        running = false;
        handler.removeCallbacks(pulse);
        removeProviders();
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ACTIVE, false).apply();

        // The activity already reports missing mock-location access. Avoid posting a
        // second notification here so Android 13+ does not require notification permission.
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void removeProviders() {
        if (manager == null) return;
        for (String provider : PROVIDERS) {
            try { manager.setTestProviderEnabled(provider, false); }
            catch (RuntimeException ignored) {}
            try { manager.removeTestProvider(provider); }
            catch (RuntimeException ignored) {}
        }
    }

    private Notification notification(String title, String text) {
        Intent open = new Intent(this, SystemLocationActivity.class);
        PendingIntent content = PendingIntent.getActivity(
                this, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stop = new Intent(this, SystemMockLocationService.class).setAction(ACTION_STOP);
        PendingIntent stopAction = PendingIntent.getService(
                this, 2, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_ghostviki)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(content)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(new Notification.Action.Builder(
                        null, "STOP", stopAction).build())
                .build();
    }

    private void createChannel() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL, "GhostViki system location", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("System-wide mock location testing");
        nm.createNotificationChannel(channel);
    }

    private boolean valid(double lat, double lon) {
        return Double.isFinite(lat) && Double.isFinite(lon)
                && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
    }

    @Override public void onDestroy() {
        running = false;
        handler.removeCallbacks(pulse);
        removeProviders();
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ACTIVE, false).apply();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
