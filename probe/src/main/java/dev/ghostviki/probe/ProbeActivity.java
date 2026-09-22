package dev.ghostviki.probe;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.provider.Settings;
import android.view.WindowInsets;
import android.widget.*;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.json.JSONObject;

/** An independent reader: no dependency on the module, its config or its hook API. */
public final class ProbeActivity extends Activity {
    private TextView report;
    private TextView diagnostics;
    private TextView locationReport;
    private CancellationSignal pending;
    private LocationListener listener;
    private final Map<String, String> values = new LinkedHashMap<>();
    private android.os.Handler handler;
    private Runnable locationTimeout;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        handler = new android.os.Handler(getMainLooper());
        getWindow().setDecorFitsSystemWindows(false);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(7, 13, 10));
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        body.setPadding(padding, padding, padding, padding);
        scroll.addView(body);
        setContentView(scroll);
        TextView title = new TextView(this);
        title.setText("GhostViki Probe"); title.setTextSize(28); title.setTextColor(Color.rgb(106, 255, 183));
        body.addView(title);
        diagnostics = new TextView(this);
        diagnostics.setTextColor(Color.LTGRAY);
        diagnostics.setTextIsSelectable(true);
        body.addView(diagnostics);
        report = new TextView(this);
        report.setTextSize(14); report.setTextColor(Color.WHITE); report.setTextIsSelectable(true);
        Button refresh = button(body, "Read current values");
        refresh.setOnClickListener(v -> read());
        Button save = button(body, "Save this as baseline");
        save.setOnClickListener(v -> {
            read();
            getPreferences(MODE_PRIVATE).edit().putString("baseline", new JSONObject(values).toString()).apply();
            Toast.makeText(this, "Baseline saved on this device", Toast.LENGTH_SHORT).show();
        });
        Button compare = button(body, "Compare with baseline");
        compare.setOnClickListener(v -> compare());
        body.addView(report);
        locationReport = new TextView(this);
        locationReport.setTextColor(Color.WHITE);
        locationReport.setText("\nLocation has not been requested.\n");
        Button locate = button(body, "Check current + streamed location");
        locate.setOnClickListener(v -> locate());
        body.addView(locationReport);
        read();
    }

    private Button button(LinearLayout body, String title) {
        Button button = new Button(this); button.setText(title); button.setAllCaps(false);
        body.addView(button, new LinearLayout.LayoutParams(-1, -2));
        return button;
    }

    public static boolean ghostVikiHookActive() { return false; }
    public static String ghostVikiConfigStatus() { return "MODULE_NOT_LOADED"; }

    @SuppressWarnings("deprecation")
    private void read() {
        values.clear();
        // Keep module diagnostics OUT of the observed values and baseline comparison.
        // Changing the marker alone must never count as a changed identity.
        diagnostics.setText("v" + BuildConfig.VERSION_NAME + "\nModule loaded: " + ghostVikiHookActive()
                + "\nConfig: " + ghostVikiConfigStatus()
                + "\nDiagnostics are not proof of changed values. Compare Android API reads below.\n");
        values.put("Android ID", Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID));
        values.put("Manufacturer", Build.MANUFACTURER);
        values.put("Brand", Build.BRAND);
        values.put("Model", Build.MODEL);
        values.put("Device", Build.DEVICE);
        values.put("Product", Build.PRODUCT);
        values.put("Build fingerprint", Build.FINGERPRINT);
        values.put("Build ID", Build.ID);
        values.put("Android release", Build.VERSION.RELEASE);
        values.put("SDK", Integer.toString(Build.VERSION.SDK_INT));
        values.put("Build.SERIAL", Build.SERIAL);
        values.put("Build.getSerial", serial());
        for (String path : new String[]{"/system/bin/su", "/system/xbin/su", "/sbin/su"})
            values.put("File.exists " + path, Boolean.toString(new File(path).exists()));
        for (String pkg : new String[]{"com.topjohnwu.magisk", "me.weishu.kernelsu", "me.bmax.apatch"}) {
            try { getPackageManager().getPackageInfo(pkg, 0); values.put("Package " + pkg, "visible"); }
            catch (PackageManager.NameNotFoundException ignored) { values.put("Package " + pkg, "not visible"); }
        }
        StringBuilder text = new StringBuilder("\nObserved values (not a root verdict)\n\n");
        values.forEach((key, value) -> text.append(key).append(":\n").append(value).append("\n\n"));
        report.setText(text);
    }

    @SuppressLint({"HardwareIds", "MissingPermission"})
    private String serial() {
        try { return Build.getSerial(); }
        catch (SecurityException ignored) { return "Access denied by Android (expected for an ordinary app)"; }
    }

    private void compare() {
        read();
        String saved = getPreferences(MODE_PRIVATE).getString("baseline", null);
        if (saved == null) { Toast.makeText(this, "Save a baseline first", Toast.LENGTH_SHORT).show(); return; }
        try {
            JSONObject baseline = new JSONObject(saved);
            StringBuilder text = new StringBuilder("Changes since baseline\n\n");
            int changes = 0;
            for (Map.Entry<String, String> item : values.entrySet()) {
                String old = baseline.optString(item.getKey(), "");
                if (!Objects.equals(old, item.getValue())) {
                    text.append(item.getKey()).append("\nBefore: ").append(old)
                            .append("\nNow: ").append(item.getValue()).append("\n\n");
                    changes++;
                }
            }
            if (changes == 0) text.append("No changed values observed.\n");
            text.append("\nAn unchanged or unavailable value is not a passing test.");
            report.setText(text);
        } catch (org.json.JSONException e) { report.setText("Baseline could not be read. Save a new baseline."); }
    }

    private void locate() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return;
        }
        requestLocation();
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == 1 && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) requestLocation();
        else locationReport.setText("Location permission not granted.");
    }

    @SuppressLint("MissingPermission")
    private void requestLocation() {
        stopLocation();
        LocationManager manager = getSystemService(LocationManager.class);
        String provider = manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;
        if (!manager.isProviderEnabled(provider)) { locationReport.setText("Enable a location provider first."); return; }
        locationReport.setText("Waiting for a fix (up to 30 seconds)…\n");
        try {
            appendLocation("Cached", manager.getLastKnownLocation(provider));
            pending = new CancellationSignal();
            manager.getCurrentLocation(provider, pending, getMainExecutor(), value -> appendLocation("Current", value));
            listener = value -> appendLocation("Update", value);
            manager.requestLocationUpdates(provider, 3000, 0, getMainExecutor(), listener);
            locationTimeout = () -> { stopLocation(); locationReport.append("\nLocation check ended."); };
            handler.postDelayed(locationTimeout, 30000);
        } catch (SecurityException | IllegalArgumentException e) {
            stopLocation();
            locationReport.append("\nLocation unavailable: " + e.getClass().getSimpleName());
        }
    }

    private void appendLocation(String source, Location location) {
        if (location == null) { locationReport.append(source + ": no fix\n"); return; }
        locationReport.append(source + ": " + location.getLatitude() + ", " + location.getLongitude()
                + " · mock=" + location.isMock() + "\n");
    }
    private void stopLocation() {
        if (locationTimeout != null) { handler.removeCallbacks(locationTimeout); locationTimeout = null; }
        if (pending != null) { pending.cancel(); pending = null; }
        if (listener != null) {
            try { getSystemService(LocationManager.class).removeUpdates(listener); } catch (RuntimeException ignored) {}
            listener = null;
        }
    }
    @Override protected void onStop() { stopLocation(); super.onStop(); }
}
