package dev.ghostviki.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.*;

import java.util.List;
import java.util.Locale;

public final class SystemLocationActivity extends Activity {
    private static final int GREEN = Color.rgb(72, 255, 174);
    private static final int INK = Color.rgb(3, 10, 7);
    private static final int PANEL = Color.rgb(7, 26, 17);
    private static final int TEXT = Color.rgb(239, 250, 244);
    private static final int MUTED = Color.rgb(139, 183, 157);
    private static final int ORANGE = Color.rgb(255, 184, 77);

    private LinearLayout body;
    private LinearLayout results;
    private EditText query;
    private EditText latitude;
    private EditText longitude;
    private TextView selected;
    private TextView status;
    private double selectedLat = Double.NaN;
    private double selectedLon = Double.NaN;
    private String selectedLabel = "MANUAL COORDINATES";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setDecorFitsSystemWindows(false);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(1, 18, 10), INK, Color.rgb(2, 24, 14)}));
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets safe = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });

        body = column();
        body.setPadding(dp(18), dp(18), dp(18), dp(28));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -1));
        setContentView(scroll);

        header();
        buildSearch();
        buildCoordinates();
        buildControls();
        loadSaved();
        refreshStatus();
    }

    @Override protected void onResume() {
        super.onResume();
        if (status != null) refreshStatus();
    }

    private void header() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = label("‹", 38, GREEN, false);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        row.addView(back, new LinearLayout.LayoutParams(dp(44), dp(48)));

        LinearLayout center = column();
        TextView brand = label("GHOSTVIKI", 25, GREEN, true);
        brand.setGravity(Gravity.CENTER);
        brand.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        brand.setLetterSpacing(0.08f);
        center.addView(brand);
        TextView sub = label("VECTOR SYSTEM LOCATION", 10, GREEN, true);
        sub.setGravity(Gravity.CENTER);
        sub.setLetterSpacing(0.18f);
        center.addView(sub);
        row.addView(center, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(new View(this), new LinearLayout.LayoutParams(dp(44), 1));
        body.addView(row);

        space(18);
        TextView title = text("SYSTEM-WIDE LOCATION", 24, TEXT, true);
        title.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        title.setLetterSpacing(0.05f);
        text("SEARCH A PLACE OR ENTER COORDINATES", 11, GREEN, true);
        space(8);

        status = text("", 12, MUTED, true);
        status.setPadding(0, dp(4), 0, dp(10));
    }

    private void buildSearch() {
        query = input("SEARCH PLACE  •  CITY  •  ADDRESS  •  AIRPORT", false);
        button("SEARCH PLACE", this::searchPlace, true);

        results = column();
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.topMargin = dp(8);
        body.addView(results, rp);
        space(12);
    }

    private void buildCoordinates() {
        selected = text("SELECTED: NONE", 12, GREEN, true);
        selected.setPadding(0, 0, 0, dp(8));

        latitude = input("LATITUDE  •  -90 TO 90", true);
        longitude = input("LONGITUDE  •  -180 TO 180", true);

        button("USE THESE COORDINATES", () -> {
            try {
                selectedLat = parseLat(latitude.getText().toString());
                selectedLon = parseLon(longitude.getText().toString());
                selectedLabel = "MANUAL COORDINATES";
                updateSelected();
            } catch (IllegalArgumentException e) {
                toast(e.getMessage());
            }
        }, false);

        space(12);
    }

    private void buildControls() {
        button("ENABLE VECTOR SYSTEM LOCATION", this::enableSystemLocation, true);
        button("DISABLE SYSTEM LOCATION", this::disableSystemLocation, false);

        space(16);
        TextView info = text(
                "VECTOR / LSPOSED MUST SCOPE GHOSTVIKI TO SYSTEM FRAMEWORK (android). " +
                "NO DEVELOPER-OPTIONS MOCK APP IS USED. THIS TEST OVERRIDE REMAINS MARKED AS SIMULATED.",
                10, MUTED, false);
        info.setLineSpacing(dp(2), 1f);
    }

    private void searchPlace() {
        String text = query.getText().toString().trim();
        if (text.isEmpty()) {
            toast("Enter a place name.");
            return;
        }
        results.removeAllViews();
        results.addView(label("SEARCHING…", 12, GREEN, true));

        if (!Geocoder.isPresent()) {
            results.removeAllViews();
            results.addView(label("NO GEOCODER SERVICE IS AVAILABLE ON THIS DEVICE.", 12, ORANGE, true));
            return;
        }

        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        if (Build.VERSION.SDK_INT >= 33) {
            geocoder.getFromLocationName(text, 5, new Geocoder.GeocodeListener() {
                @Override public void onGeocode(List<Address> addresses) {
                    runOnUiThread(() -> showResults(addresses));
                }

                @Override public void onError(String errorMessage) {
                    runOnUiThread(() -> showSearchError(errorMessage));
                }
            });
        } else {
            new Thread(() -> {
                try {
                    @SuppressWarnings("deprecation")
                    List<Address> addresses = geocoder.getFromLocationName(text, 5);
                    runOnUiThread(() -> showResults(addresses));
                } catch (Exception e) {
                    runOnUiThread(() -> showSearchError(e.getMessage()));
                }
            }, "GhostViki-Geocoder").start();
        }
    }

    private void showSearchError(String errorMessage) {
        results.removeAllViews();
        results.addView(label(
                errorMessage == null || errorMessage.isBlank()
                        ? "PLACE SEARCH FAILED."
                        : "PLACE SEARCH FAILED: " + errorMessage.toUpperCase(Locale.ROOT),
                12, ORANGE, true));
    }

    private void showResults(List<Address> addresses) {
        results.removeAllViews();
        if (addresses == null || addresses.isEmpty()) {
            results.addView(label("NO MATCHES FOUND.", 12, ORANGE, true));
            return;
        }

        for (Address address : addresses) {
            String display = addressLabel(address);
            Button item = new Button(this);
            item.setAllCaps(false);
            item.setText(display + "\n" +
                    String.format(Locale.ROOT, "%.6f, %.6f", address.getLatitude(), address.getLongitude()));
            item.setTextSize(12);
            item.setTextColor(TEXT);
            item.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            item.setPadding(dp(14), dp(10), dp(14), dp(10));
            item.setBackground(shape(PANEL, Color.rgb(24, 111, 68), 13));
            item.setOnClickListener(v -> {
                selectedLat = address.getLatitude();
                selectedLon = address.getLongitude();
                selectedLabel = display;
                latitude.setText(Double.toString(selectedLat));
                longitude.setText(Double.toString(selectedLon));
                updateSelected();
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
            p.bottomMargin = dp(7);
            results.addView(item, p);
        }
    }

    private String addressLabel(Address a) {
        if (a.getMaxAddressLineIndex() >= 0) {
            String line = a.getAddressLine(0);
            if (line != null && !line.isBlank()) return line;
        }
        StringBuilder out = new StringBuilder();
        if (a.getFeatureName() != null) out.append(a.getFeatureName());
        if (a.getLocality() != null) {
            if (out.length() > 0) out.append(", ");
            out.append(a.getLocality());
        }
        if (a.getCountryName() != null) {
            if (out.length() > 0) out.append(", ");
            out.append(a.getCountryName());
        }
        return out.length() == 0 ? "SEARCH RESULT" : out.toString();
    }

    private void enableSystemLocation() {
        try {
            if (Double.isNaN(selectedLat) || Double.isNaN(selectedLon)) {
                selectedLat = parseLat(latitude.getText().toString());
                selectedLon = parseLon(longitude.getText().toString());
                selectedLabel = "MANUAL COORDINATES";
                updateSelected();
            }
        } catch (IllegalArgumentException e) {
            toast(e.getMessage());
            return;
        }

        SharedPreferences prefs = runtimePreferences();
        boolean ok = prefs.edit()
                .putString("system_latitude", Double.toString(selectedLat))
                .putString("system_longitude", Double.toString(selectedLon))
                .putString("system_location_label", selectedLabel)
                .putBoolean("system_location_enabled", true)
                .putLong("system_location_changed_at", System.currentTimeMillis())
                .commit();
        refreshStatus();
        toast(ok
                ? "System location saved. Restart System Framework or reboot if Vector has not loaded the new scope yet."
                : "Could not save system location.");
    }

    private void disableSystemLocation() {
        boolean ok = runtimePreferences().edit()
                .putBoolean("system_location_enabled", false)
                .putLong("system_location_changed_at", System.currentTimeMillis())
                .commit();
        refreshStatus();
        toast(ok ? "System location disabled." : "Could not save setting.");
    }

    private void loadSaved() {
        SharedPreferences prefs = runtimePreferences();
        String lat = prefs.getString("system_latitude", "");
        String lon = prefs.getString("system_longitude", "");
        selectedLabel = prefs.getString("system_location_label", "SAVED LOCATION");
        latitude.setText(lat);
        longitude.setText(lon);
        try {
            if (!lat.isEmpty() && !lon.isEmpty()) {
                selectedLat = parseLat(lat);
                selectedLon = parseLon(lon);
                updateSelected();
            }
        } catch (IllegalArgumentException ignored) {
            selectedLat = Double.NaN;
            selectedLon = Double.NaN;
        }
    }

    @SuppressWarnings("deprecation")
    private SharedPreferences runtimePreferences() {
        try {
            return getSharedPreferences(ConfigStore.PREFS, Context.MODE_WORLD_READABLE);
        } catch (SecurityException e) {
            return getSharedPreferences(ConfigStore.PREFS, Context.MODE_PRIVATE);
        }
    }

    private void refreshStatus() {
        SharedPreferences prefs = runtimePreferences();
        boolean enabled = prefs.getBoolean("system_location_enabled", false);
        if (enabled) {
            status.setTextColor(GREEN);
            status.setText("SYSTEM LOCATION: ENABLED  •  VECTOR SYSTEM FRAMEWORK SCOPE REQUIRED");
        } else {
            status.setTextColor(MUTED);
            status.setText("SYSTEM LOCATION: OFF  •  NO MOCK-APP SETUP REQUIRED");
        }
    }

    private void updateSelected() {
        selected.setText("SELECTED: " + selectedLabel.toUpperCase(Locale.ROOT) + "\n" +
                String.format(Locale.ROOT, "%.6f, %.6f", selectedLat, selectedLon));
    }

    private double parseLat(String raw) {
        double value = parseRequired(raw, "Enter latitude.");
        if (!Double.isFinite(value) || value < -90 || value > 90)
            throw new IllegalArgumentException("Latitude must be between -90 and 90.");
        return value;
    }

    private double parseLon(String raw) {
        double value = parseRequired(raw, "Enter longitude.");
        if (!Double.isFinite(value) || value < -180 || value > 180)
            throw new IllegalArgumentException("Longitude must be between -180 and 180.");
        return value;
    }

    private double parseRequired(String raw, String message) {
        try {
            if (raw == null || raw.trim().isEmpty()) throw new NumberFormatException();
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(message);
        }
    }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = label(value, size, color, bold);
        body.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }

    private TextView label(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(dp(2), 1f);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private EditText input(String hint, boolean numeric) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setSingleLine(true);
        input.setInputType(numeric
                ? android.text.InputType.TYPE_CLASS_NUMBER
                    | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                    | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
                : android.text.InputType.TYPE_CLASS_TEXT);
        input.setMinHeight(dp(54));
        body.addView(input, new LinearLayout.LayoutParams(-1, -2));
        return input;
    }

    private void button(String title, Runnable action, boolean primary) {
        Button button = new Button(this);
        button.setText(title);
        button.setAllCaps(true);
        button.setTextSize(13);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(primary ? INK : GREEN);
        button.setMinHeight(dp(50));
        button.setBackground(shape(primary ? GREEN : PANEL,
                primary ? GREEN : Color.rgb(18, 94, 58), 14));
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(8);
        body.addView(button, p);
    }

    private void space(int height) {
        body.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height)));
    }

    private GradientDrawable shape(int fill, int stroke, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        d.setStroke(dp(1), stroke);
        return d;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
