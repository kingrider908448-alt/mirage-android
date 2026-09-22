package dev.ghostviki.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.*;
import dev.ghostviki.core.Coordinates;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;

public final class MainActivity extends Activity {
    private static final int GREEN = Color.rgb(106, 255, 183);
    private static final int INK = Color.rgb(7, 13, 10);
    private static final int TEXT = Color.rgb(235, 247, 240);
    private static final int MUTED = Color.rgb(151, 178, 161);
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private ConfigStore config;
    private LinearLayout body;
    private String page = "home";
    private boolean saving;
    private android.window.OnBackInvokedCallback backCallback;
    private boolean backRegistered;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        config = new ConfigStore(this);
        if (state != null) page = state.getString("page", "home");
        getWindow().setDecorFitsSystemWindows(false);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            backCallback = () -> show("home");
        }
        render();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("page", page);
    }
    @Override protected void onDestroy() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && backRegistered)
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        worker.shutdown();
        super.onDestroy();
    }
    // Android 12/12L fallback only. Android 13+ uses the platform callback below.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override @SuppressWarnings("deprecation") public void onBackPressed() {
        if (!"home".equals(page)) show("home"); else super.onBackPressed();
    }

    private void updateBackCallback() {
        if (android.os.Build.VERSION.SDK_INT < 33) return;
        boolean needed = !"home".equals(page);
        if (needed == backRegistered) return;
        if (needed) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
        } else {
            // Let Android handle leaving the home screen, including its back animation.
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        }
        backRegistered = needed;
    }

    private void show(String screen) { page = screen; render(); }
    private void render() {
        updateBackCallback();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(13, 43, 28), INK, Color.rgb(8, 21, 14)}));
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });
        body = column();
        body.setPadding(dp(24), dp(24), dp(24), dp(28));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -1));
        setContentView(scroll);
        if ("home".equals(page)) home();
        else {
            button("‹  Home", () -> show("home"), false);
            space(22);
            switch (page) {
                case "root": root(); break;
                case "identity": identity(); break;
                case "location": location(); break;
                default: show("home");
            }
        }
    }

    private void home() {
        text("ON-DEVICE WORKSPACE", 11, GREEN, true);
        space(5);
        text("GhostViki", 46, TEXT, true);
        space(8);
        text(config.bridgeAvailable ? "Settings bridge ready · target verification pending"
                : "Setup needed · enable GhostViki in Vector, then reopen", 12, MUTED, false);
        space(18);
        button(config.targets().size() + " target apps  ·  Select apps", this::selectTargets, false);
        LinearLayout cards = column();
        cards.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams cardsParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        cardsParams.topMargin = dp(24);
        cardsParams.bottomMargin = dp(24);
        body.addView(cards, cardsParams);
        card(cards, "01", "Root Hide Methods", "Manage supported detection signals", () -> show("root"));
        card(cards, "02", "Change Identity", "One tap. A saved profile for your apps.", () -> show("identity"));
        card(cards, "03", "Location", "Set a location for a selected app", () -> show("location"));
        text("ALPHA 0.1  /  ANDROID 16 TEST TARGET", 10, MUTED, true);
    }

    private void root() {
        heading("Root Hide Methods", "Choose which supported app-visible signals to filter.");
        toggle("SU file checks", "Common SU paths checked through Java File APIs.", "hide_files");
        toggle("Root package visibility", "Known package names in supported package-manager calls.", "hide_packages");
        space(22);
        note("Native, kernel and attestation checks need separate validation. Enabling a method does not mean Duck Detector has passed.");
        button("Choose target apps", this::selectTargets, false);
        button("Open Duck Detector", () -> launch("com.eltavine.duckdetector"), true);
    }

    private void identity() {
        heading("Change Identity", "One profile, shown as a simple ID/value column.");
        List<String> targets = new ArrayList<>(config.targets());
        Collections.sort(targets);
        if (targets.isEmpty()) {
            text("Select at least one target app to create a profile.", 15, MUTED, false);
            space(18);
            button("Choose target apps", this::selectTargets, true);
            return;
        }

        String pkg = targets.get(0);
        long generation = config.preferences.getLong("generation", 0);
        text(generation == 0 ? "PROFILE PREVIEW" : "PROFILE " + generation, 11, GREEN, true);
        space(5);
        text(label(pkg), 25, TEXT, true);
        text(pkg, 12, MUTED, false);
        space(22);

        identityValue("Android ID / SSAID", config.preferences.getString("android_id:" + pkg, "Not generated"));
        identityValue("Device Serial", config.preferences.getString("serial:" + pkg, "Not generated"));
        identityValue("Advertising ID", config.preferences.getString("advertising_id:" + pkg, "Not generated"));
        identityValue("App Set ID", config.preferences.getString("app_set_id:" + pkg, "Not generated"));
        identityValue("Firebase Installation ID", config.preferences.getString("firebase_installation_id:" + pkg, "Not generated"));
        identityValue("FCM Registration Token", config.preferences.getString("fcm_token:" + pkg, "Not generated"));
        identityValue("GSF ID", config.preferences.getString("gsf_id:" + pkg, "Not generated"));
        identityValue("Crashlytics Installation ID", config.preferences.getString("crashlytics_installation_id:" + pkg, "Not generated"));

        space(12);
        button("Change Values", () ->
                save(config::rotateIdentity, "New synthetic test profile generated for the selected apps."), true);
        button("Use original values", () -> save(() -> config.setFlag("identity_enabled", false),
                "Original values selected. Restart the target apps."), false);
        button("Open GhostViki Probe", () -> launch("dev.ghostviki.probe"), false);
        space(20);
        note("The values shown here are synthetic test-profile values. Current runtime adapters remain limited to the identifiers already supported by GhostViki.");
    }

    private void location() {
        heading("Location", "Save a test location for one selected app.");
        List<String> targets = new ArrayList<>(config.targets());
        Collections.sort(targets);
        if (targets.isEmpty()) {
            button("Choose target apps", this::selectTargets, true);
            return;
        }
        Spinner picker = new Spinner(this);
        List<String> labels = new ArrayList<>();
        for (String pkg : targets) labels.add(label(pkg));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels);
        picker.setAdapter(adapter);
        body.addView(picker, new LinearLayout.LayoutParams(-1, dp(52)));
        EditText latitude = input("Latitude  ·  -90 to 90");
        EditText longitude = input("Longitude  ·  -180 to 180");
        TextView state = text("", 13, GREEN, false);
        picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int index, long id) {
                String pkg = targets.get(index);
                latitude.setText(config.preferences.getString("latitude:" + pkg, ""));
                longitude.setText(config.preferences.getString("longitude:" + pkg, ""));
                state.setText(config.preferences.getBoolean("location:" + pkg, false) ? "Location setting saved: ON" : "Location setting: OFF");
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        space(16);
        button("Save & enable", () -> {
            try {
                Coordinates point = Coordinates.parse(latitude.getText().toString(), longitude.getText().toString());
                String pkg = targets.get(picker.getSelectedItemPosition());
                save(() -> config.setLocation(pkg, point, true), "Location saved. Restart the target and verify the result.");
            } catch (IllegalArgumentException e) { toast(e.getMessage()); }
        }, true);
        button("Stop location change", () -> {
            String pkg = targets.get(picker.getSelectedItemPosition());
            save(() -> config.stopLocation(pkg), "Location change disabled. Restart the target if it cached a location.");
        }, false);
        note("Support depends on the app's location API. Existing location permission is still required; no fix is invented when the app receives no location.");
    }

    private void selectTargets() {
        if (saving) return;
        worker.execute(() -> {
            Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> found = getPackageManager().queryIntentActivities(launcher, 0);
            Map<String, String> names = new TreeMap<>();
            for (ResolveInfo item : found) {
                String pkg = item.activityInfo.packageName;
                ApplicationInfo info = item.activityInfo.applicationInfo;
                if (!getPackageName().equals(pkg) && (info.flags & ApplicationInfo.FLAG_SYSTEM) == 0)
                    names.put(pkg, item.loadLabel(getPackageManager()).toString());
            }
            Set<String> selected = config.targets();
            for (String pkg : selected) names.putIfAbsent(pkg, pkg);
            List<String> packages = new ArrayList<>(names.keySet());
            packages.sort(Comparator.comparing(names::get, String.CASE_INSENSITIVE_ORDER));
            String[] rows = new String[packages.size()];
            boolean[] checked = new boolean[packages.size()];
            for (int i = 0; i < packages.size(); i++) {
                String pkg = packages.get(i);
                rows[i] = names.get(pkg) + "\n" + pkg;
                checked[i] = selected.contains(pkg);
            }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                new AlertDialog.Builder(this).setTitle("Target apps")
                        .setMultiChoiceItems(rows, checked, (dialog, index, on) -> {
                            if (on) selected.add(packages.get(index)); else selected.remove(packages.get(index));
                        }).setNegativeButton("Cancel", null)
                        .setPositiveButton("Save", (dialog, which) -> save(() -> config.setTargets(selected),
                                "Targets saved. Select the same apps in Vector's GhostViki scope and restart them."))
                        .show();
            });
        });
    }

    private void save(BooleanSupplier action, String message) {
        if (saving) return;
        saving = true;
        worker.execute(() -> {
            String result;
            boolean success = false;
            try {
                success = action.getAsBoolean();
                result = success ? message : "Settings could not be saved. Try again.";
            }
            catch (RuntimeException e) { result = "Could not save: " + e.getMessage(); }
            String completed = success && !config.bridgeAvailable
                    ? "Draft saved. Enable GhostViki in Vector and reopen it before testing." : result;
            runOnUiThread(() -> {
                saving = false;
                if (!isFinishing() && !isDestroyed()) {
                    render();
                    toast(completed);
                }
            });
        });
    }

    private void toggle(String title, String detail, String key) {
        Switch control = new Switch(this);
        control.setText(title);
        control.setTextSize(18);
        control.setTextColor(TEXT);
        control.setMinHeight(dp(58));
        control.setChecked(config.preferences.getBoolean(key, false));
        control.setOnCheckedChangeListener((button, checked) -> save(() -> config.setFlag(key, checked), "Method saved. Verify the target app after restarting it."));
        body.addView(control, new LinearLayout.LayoutParams(-1, -2));
        text(detail, 13, MUTED, false);
        space(20);
    }
    private void launch(String pkg) {
        Intent intent = getPackageManager().getLaunchIntentForPackage(pkg);
        if (intent == null) toast("Install " + label(pkg) + " first.");
        else startActivity(intent);
    }
    private String label(String pkg) {
        try { return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)).toString(); }
        catch (android.content.pm.PackageManager.NameNotFoundException ignored) { return pkg; }
    }
    private void identityValue(String title, String value) {
        TextView name = labelView(title.toUpperCase(Locale.ROOT), 11, MUTED, true);
        body.addView(name, new LinearLayout.LayoutParams(-1, -2));
        TextView valueView = labelView(value, 15, GREEN, false);
        valueView.setTypeface(Typeface.MONOSPACE);
        valueView.setTextIsSelectable(true);
        valueView.setPadding(0, dp(5), 0, dp(16));
        body.addView(valueView, new LinearLayout.LayoutParams(-1, -2));
        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(35, 67, 48));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(1));
        params.bottomMargin = dp(18);
        body.addView(divider, params);
    }

    private void heading(String title, String description) {
        text(title, 32, TEXT, true); space(10); text(description, 15, MUTED, false); space(30);
    }
    private void note(String text) { space(18); text(text, 13, MUTED, false); space(18); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    private LinearLayout column() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView label = labelView(value, size, color, bold);
        body.addView(label, new LinearLayout.LayoutParams(-1, -2));
        return label;
    }
    private TextView labelView(String value, int size, int color, boolean bold) {
        TextView label = new TextView(this);
        label.setText(value); label.setTextSize(size); label.setTextColor(color);
        label.setLineSpacing(dp(3), 1f);
        if (bold) label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return label;
    }
    private void space(int height) { body.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height))); }
    private void button(String value, Runnable action, boolean primary) {
        Button button = new Button(this);
        button.setText(value); button.setAllCaps(false); button.setTextSize(15);
        button.setTextColor(primary ? INK : GREEN);
        button.setMinHeight(dp(52));
        button.setBackground(shape(primary ? GREEN : Color.rgb(14, 31, 22), Color.rgb(40, 80, 56)));
        button.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(10);
        body.addView(button, params);
    }
    private EditText input(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint); input.setTextColor(TEXT); input.setHintTextColor(MUTED);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setMinHeight(dp(58));
        body.addView(input, new LinearLayout.LayoutParams(-1, -2));
        return input;
    }
    private void card(LinearLayout parent, String number, String title, String subtitle, Runnable action) {
        LinearLayout card = column();
        card.setPadding(dp(20), dp(18), dp(20), dp(22));
        card.setBackground(shape(Color.rgb(14, 35, 23), Color.rgb(43, 88, 59)));
        card.addView(labelView(number + "   /", 12, GREEN, true));
        TextView heading = labelView(title, 23, TEXT, true);
        heading.setPadding(0, dp(9), 0, dp(4));
        card.addView(heading);
        card.addView(labelView(subtitle, 13, MUTED, false));
        card.setOnClickListener(view -> action.run());
        card.setFocusable(true);
        card.setContentDescription(title + ". " + subtitle);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(14);
        parent.addView(card, params);
    }
    private GradientDrawable shape(int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill); drawable.setCornerRadius(dp(20)); drawable.setStroke(dp(1), stroke);
        return drawable;
    }
    private int dp(int size) { return Math.round(size * getResources().getDisplayMetrics().density); }
}
