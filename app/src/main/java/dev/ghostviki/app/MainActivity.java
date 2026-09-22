package dev.ghostviki.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.*;
import dev.ghostviki.core.Coordinates;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;

public final class MainActivity extends Activity {
    private static final int GREEN = Color.rgb(72, 255, 174);
    private static final int GREEN_DARK = Color.rgb(18, 94, 58);
    private static final int INK = Color.rgb(3, 10, 7);
    private static final int PANEL = Color.rgb(7, 26, 17);
    private static final int PANEL_2 = Color.rgb(9, 34, 22);
    private static final int TEXT = Color.rgb(239, 250, 244);
    private static final int MUTED = Color.rgb(139, 183, 157);

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
        if (android.os.Build.VERSION.SDK_INT >= 33) backCallback = this::goBack;
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

    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override @SuppressWarnings("deprecation") public void onBackPressed() {
        if (!"home".equals(page)) goBack(); else super.onBackPressed();
    }

    private void goBack() {
        if (page.startsWith("identity:")) show("identity");
        else if (!"home".equals(page)) show("home");
    }

    private void updateBackCallback() {
        if (android.os.Build.VERSION.SDK_INT < 33) return;
        boolean needed = !"home".equals(page);
        if (needed == backRegistered) return;
        if (needed) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
        } else {
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
                new int[]{Color.rgb(1, 18, 10), INK, Color.rgb(2, 24, 14)}));
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets safe = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });

        body = column();
        body.setPadding(dp(18), dp(18), dp(18), dp(24));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -1));
        setContentView(scroll);

        if ("home".equals(page)) home();
        else if ("identity".equals(page)) identityCategories();
        else if (page.startsWith("identity:")) identityDetail(page.substring("identity:".length()));
        else if ("root".equals(page)) { subHeader("ROOT HIDE"); root(); }
        else if ("location".equals(page)) { subHeader("LOCATION"); location(); }
        else show("home");
    }

    private void home() {
        TextView brand = text("GHOSTVIKI", 40, GREEN, true);
        brand.setGravity(Gravity.CENTER);
        brand.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        brand.setLetterSpacing(0.07f);
        space(6);
        TextView sub = text("CHANGE IDENTITY", 12, GREEN, true);
        sub.setGravity(Gravity.CENTER);
        sub.setLetterSpacing(0.28f);
        space(24);

        targetPanel();
        space(20);

        homeCard("ROOT HIDE", "BYPASS  •  MASK  •  PROTECT", null, "◆", () -> show("root"));
        homeCard("CHANGE IDENTITY", "SPOOF  •  MODIFY  •  RESET", R.drawable.ic_fingerprint, null, () -> show("identity"));
        homeCard("LOCATION", "FAKE  •  CUSTOM  •  CONTROL", null, "●", () -> show("location"));

        space(24);
        TextView footer = text("STAY PRIVATE  STAY AHEAD", 10, GREEN, true);
        footer.setGravity(Gravity.CENTER);
        footer.setLetterSpacing(0.22f);
    }

    private void targetPanel() {
        LinearLayout panel = column();
        panel.setPadding(dp(18), dp(16), dp(18), dp(16));
        panel.setBackground(shape(PANEL, GREEN_DARK, 18));

        TextView title = labelView("TARGET APPLICATION", 19, TEXT, true);
        title.setLetterSpacing(0.08f);
        panel.addView(title);
        TextView hint = labelView("SELECT TARGET APP", 11, GREEN, true);
        hint.setLetterSpacing(0.12f);
        hint.setPadding(0, dp(4), 0, dp(14));
        panel.addView(hint);

        LinearLayout selector = new LinearLayout(this);
        selector.setGravity(Gravity.CENTER_VERTICAL);
        selector.setPadding(dp(14), dp(10), dp(14), dp(10));
        selector.setBackground(shape(Color.rgb(5, 20, 13), Color.rgb(25, 118, 73), 12));

        TextView selected = labelView(config.targets().isEmpty() ? "NONE SELECTED" :
                "SELECTED APPS  (" + config.targets().size() + ")", 14, TEXT, true);
        selected.setAllCaps(true);
        LinearLayout.LayoutParams selectedParams = new LinearLayout.LayoutParams(0, -2, 1f);
        selector.addView(selected, selectedParams);

        TextView grid = labelView("⊞", 30, GREEN, false);
        grid.setGravity(Gravity.CENTER);
        selector.addView(grid, new LinearLayout.LayoutParams(dp(44), dp(44)));
        selector.setOnClickListener(v -> selectTargets());
        panel.addView(selector, new LinearLayout.LayoutParams(-1, -2));

        addTargetIcons(panel);

        TextView count = labelView("TOTAL TARGET APPS: " + config.targets().size(), 12, GREEN, true);
        count.setLetterSpacing(0.10f);
        count.setPadding(0, dp(14), 0, 0);
        panel.addView(count);

        panel.setOnClickListener(v -> selectTargets());
        body.addView(panel, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addTargetIcons(LinearLayout parent) {
        List<String> targets = new ArrayList<>(config.targets());
        Collections.sort(targets);
        if (targets.isEmpty()) return;

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, 0);

        for (String pkg : targets) {
            LinearLayout item = column();
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(4), 0, dp(10), 0);

            ImageView icon = new ImageView(this);
            try {
                Drawable drawable = getPackageManager().getApplicationIcon(pkg);
                icon.setImageDrawable(drawable);
            } catch (PackageManager.NameNotFoundException ignored) {
                icon.setImageResource(android.R.drawable.sym_def_app_icon);
            }
            item.addView(icon, new LinearLayout.LayoutParams(dp(34), dp(34)));

            TextView name = labelView(label(pkg).toUpperCase(Locale.ROOT), 9, MUTED, true);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(dp(72), -2);
            np.topMargin = dp(4);
            item.addView(name, np);
            row.addView(item, new LinearLayout.LayoutParams(-2, -2));
        }
        scroll.addView(row);
        parent.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
    }

    private void homeCard(String title, String subtitle, Integer iconRes, String glyph, Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(18), dp(16), dp(16), dp(16));
        card.setBackground(shape(PANEL_2, Color.rgb(29, 135, 82), 18));

        if (iconRes != null) {
            ImageView icon = new ImageView(this);
            icon.setImageResource(iconRes);
            icon.setColorFilter(GREEN);
            card.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));
        } else {
            TextView icon = labelView(glyph == null ? "•" : glyph, 34, GREEN, true);
            icon.setGravity(Gravity.CENTER);
            card.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));
        }

        LinearLayout words = column();
        TextView h = labelView(title, 20, TEXT, true);
        h.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        h.setLetterSpacing(0.07f);
        words.addView(h);
        TextView s = labelView(subtitle, 11, GREEN, true);
        s.setLetterSpacing(0.10f);
        s.setPadding(0, dp(5), 0, 0);
        words.addView(s);
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(0, -2, 1f);
        wp.leftMargin = dp(14);
        card.addView(words, wp);

        TextView arrow = labelView("›", 38, GREEN, false);
        arrow.setGravity(Gravity.CENTER);
        card.addView(arrow, new LinearLayout.LayoutParams(dp(30), dp(50)));
        card.setOnClickListener(v -> action.run());

        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.bottomMargin = dp(14);
        body.addView(card, cp);
    }

    private void subHeader(String subtitle) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = labelView("‹", 38, GREEN, false);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> goBack());
        row.addView(back, new LinearLayout.LayoutParams(dp(44), dp(48)));

        LinearLayout center = column();
        TextView brand = labelView("GHOSTVIKI", 25, GREEN, true);
        brand.setGravity(Gravity.CENTER);
        brand.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        brand.setLetterSpacing(0.08f);
        center.addView(brand);
        TextView sub = labelView(subtitle, 10, GREEN, true);
        sub.setGravity(Gravity.CENTER);
        sub.setLetterSpacing(0.22f);
        center.addView(sub);
        row.addView(center, new LinearLayout.LayoutParams(0, -2, 1f));

        row.addView(new View(this), new LinearLayout.LayoutParams(dp(44), 1));
        body.addView(row, new LinearLayout.LayoutParams(-1, -2));
        space(18);
    }

    private void identityCategories() {
        subHeader("CHANGE IDENTITY");

        long generation = config.preferences.getLong("generation", 0);
        long changedAt = config.preferences.getLong("changed_at", 0);
        heading("IDENTITY PROFILE  #" + generation,
                config.targets().isEmpty() ? "SELECT A TARGET APP TO GENERATE VALUES"
                        : (changedAt == 0 ? "READY TO GENERATE A SAVED PROFILE"
                        : "ACTIVE SAVED PROFILE  •  " + config.targets().size() + " TARGET APP(S)"));
        button("CHANGE ALL VALUES NOW", () -> {
            if (config.targets().isEmpty()) { selectTargets(); return; }
            save(config::rotateIdentity, "All profile values changed.");
        }, true);
        space(18);

        categoryCard("G", "GOOGLE IDENTIFIERS",
                "GSF ID  |  AAID  |  APP SET ID  |  FID  |  FCM\nCRASHLYTICS  |  PLAY SERVICES",
                "google");
        categoryCard("A", "ANDROID / DEVICE IDENTIFIERS",
                "ANDROID ID  |  SERIAL  |  ANDROID DEVICE ID\nBOOT ID  |  BUILD ID  |  HARDWARE",
                "android");
        categoryCard("N", "NETWORK IDENTIFIERS",
                "WIFI MAC  |  BLUETOOTH MAC  |  SSID  |  BSSID\nIPV4  |  IPV6  |  DHCP  |  GATEWAY",
                "network");
        categoryCard("SIM", "SIM / TELEPHONY / COUNTRY",
                "IMEI  |  IMSI  |  ICCID  |  MSISDN  |  MCC  |  MNC\nOPERATOR  |  COUNTRY  |  ROAMING  |  EID",
                "telephony");
        categoryCard("L", "LOCATION IDENTIFIERS",
                "LATITUDE  |  LONGITUDE  |  ALTITUDE  |  ACCURACY\nTIMEZONE  |  LOCALE  |  COUNTRY  |  CITY",
                "location");
        categoryCard("U", "ACCOUNTS & USER DATA",
                "GOOGLE ACCOUNT  |  EMAIL  |  USER ID\nDEVICE OWNER  |  WORK PROFILE  |  OTHER",
                "accounts");
        categoryCard("SYS", "APP & SYSTEM IDENTIFIERS",
                "PACKAGE NAME  |  SIGNATURE  |  INSTALLER\nAPP SETS  |  ANDROID USER  |  PROCESS UID",
                "system");
        categoryCard("HW", "DEVICE PROFILE (HARDWARE)",
                "BRAND  |  MODEL  |  MANUFACTURER  |  PRODUCT\nSOC  |  ROM  |  ANDROID  |  KERNEL  |  ABI  |  FINGERPRINT",
                "hardware");

        space(8);
        TextView random = text("⟳  GENERATE RANDOM PROFILE", 11, GREEN, true);
        random.setGravity(Gravity.CENTER);
        random.setLetterSpacing(0.12f);
        random.setPadding(0, dp(10), 0, dp(10));
        random.setOnClickListener(v -> {
            if (config.targets().isEmpty()) { selectTargets(); return; }
            save(config::rotateIdentity, "New profile generated.");
        });
    }

    private void categoryCard(String mark, String title, String summary, String key) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(14), dp(12), dp(14));
        card.setBackground(shape(PANEL, Color.rgb(24, 111, 68), 15));

        TextView icon = labelView(mark, mark.length() > 1 ? 11 : 30, GREEN, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(shape(Color.rgb(8, 44, 27), Color.rgb(31, 142, 85), 50));
        card.addView(icon, new LinearLayout.LayoutParams(dp(50), dp(50)));

        LinearLayout words = column();
        TextView h = labelView(title, 17, TEXT, true);
        h.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        h.setLetterSpacing(0.04f);
        words.addView(h);
        TextView s = labelView(summary, 10, MUTED, false);
        s.setLineSpacing(dp(2), 1f);
        s.setPadding(0, dp(5), 0, 0);
        words.addView(s);
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(0, -2, 1f);
        wp.leftMargin = dp(12);
        card.addView(words, wp);

        TextView arrow = labelView("›", 34, GREEN, false);
        arrow.setGravity(Gravity.CENTER);
        card.addView(arrow, new LinearLayout.LayoutParams(dp(26), dp(46)));
        card.setOnClickListener(v -> show("identity:" + key));

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(10);
        body.addView(card, p);
    }

    private void identityDetail(String category) {
        subHeader("CHANGE IDENTITY");
        List<String> targets = new ArrayList<>(config.targets());
        Collections.sort(targets);
        String pkg = targets.isEmpty() ? getPackageName() : targets.get(0);

        String title = categoryTitle(category);
        TextView t = text(title, 24, TEXT, true);
        t.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        t.setLetterSpacing(0.05f);
        TextView desc = text(categoryDescription(category), 11, GREEN, true);
        desc.setLetterSpacing(0.08f);
        space(16);

        for (String[] row : categoryRows(category, pkg)) valueCard(row[0], row[1]);

        space(8);
        button("GENERATE NEW VALUES", () -> {
            if (config.targets().isEmpty()) { selectTargets(); return; }
            save(config::rotateIdentity, "New values generated.");
        }, true);
    }

    private String categoryTitle(String c) {
        switch (c) {
            case "google": return "GOOGLE IDENTIFIERS";
            case "android": return "ANDROID / DEVICE IDENTIFIERS";
            case "network": return "NETWORK IDENTIFIERS";
            case "telephony": return "SIM / TELEPHONY / COUNTRY";
            case "location": return "LOCATION IDENTIFIERS";
            case "accounts": return "ACCOUNTS & USER DATA";
            case "system": return "APP & SYSTEM IDENTIFIERS";
            case "hardware": return "DEVICE PROFILE (HARDWARE)";
            default: return "IDENTIFIERS";
        }
    }

    private String categoryDescription(String c) {
        switch (c) {
            case "google": return "GOOGLE-RELATED PROFILE VALUES";
            case "android": return "ANDROID AND DEVICE PROFILE VALUES";
            case "network": return "NETWORK PROFILE VALUES";
            case "telephony": return "SIM, CARRIER AND COUNTRY PROFILE VALUES";
            case "location": return "LOCATION AND REGIONAL PROFILE VALUES";
            case "accounts": return "ACCOUNT-SCOPED PROFILE VALUES";
            case "system": return "APP AND SYSTEM PROFILE VALUES";
            case "hardware": return "DEVICE BUILD AND HARDWARE PROFILE VALUES";
            default: return "PROFILE VALUES";
        }
    }

    private List<String[]> categoryRows(String c, String pkg) {
        List<String[]> rows = new ArrayList<>();
        if ("google".equals(c)) {
            rows.add(row("GSF ID", pref(pkg, "gsf_id", synth(pkg, "gsf", "hex16"))));
            rows.add(row("ADVERTISING ID (AAID)", pref(pkg, "advertising_id", synth(pkg, "aaid", "uuid"))));
            rows.add(row("APP SET ID", pref(pkg, "app_set_id", synth(pkg, "appset", "hex32"))));
            rows.add(row("FIREBASE INSTALLATION ID (FID)", pref(pkg, "firebase_installation_id", synth(pkg, "fid", "token22"))));
            rows.add(row("FCM REGISTRATION TOKEN", pref(pkg, "fcm_token", synth(pkg, "fcm", "fcm"))));
            rows.add(row("CRASHLYTICS INSTALLATION ID", pref(pkg, "crashlytics_installation_id", synth(pkg, "crash", "hex32"))));
            rows.add(row("PLAY SERVICES ID", synth(pkg, "play_services", "uuid")));
            rows.add(row("LEGACY INSTANCE ID", synth(pkg, "instance_id", "hex32")));
        } else if ("android".equals(c)) {
            rows.add(row("ANDROID ID (SSAID)", pref(pkg, "android_id", synth(pkg, "android_id", "hex16"))));
            rows.add(row("DEVICE SERIAL", pref(pkg, "serial", synth(pkg, "serial", "serial"))));
            rows.add(row("ANDROID DEVICE ID", pref(pkg, "device_id", synth(pkg, "device_id", "hex16"))));
            rows.add(row("BOOT ID", pref(pkg, "boot_id", synth(pkg, "boot_id", "uuid"))));
            rows.add(row("BOOT COUNT", synth(pkg, "boot_count", "digits4")));
            rows.add(row("DEVICE NAME", "GHOST-" + synth(pkg, "device_name", "hex4").toUpperCase(Locale.ROOT)));
            rows.add(row("BUILD ID", pref(pkg, "build_id", "GV" + synth(pkg, "build_id", "hex14").toUpperCase(Locale.ROOT))));
            rows.add(row("HARDWARE", pref(pkg, "hardware", "gv_" + synth(pkg, "hardware", "hex4"))));
        } else if ("network".equals(c)) {
            rows.add(row("WIFI MAC", pref(pkg, "wifi_mac", synth(pkg, "wifi_mac", "mac"))));
            rows.add(row("BLUETOOTH MAC", pref(pkg, "bluetooth_mac", synth(pkg, "bt_mac", "mac"))));
            rows.add(row("SSID", "GHOSTVIKI_" + synth(pkg, "ssid", "hex4").toUpperCase(Locale.ROOT)));
            rows.add(row("BSSID", synth(pkg, "bssid", "mac")));
            rows.add(row("IPV4", synth(pkg, "ipv4", "ipv4")));
            rows.add(row("IPV6", synth(pkg, "ipv6", "ipv6")));
            rows.add(row("DHCP SERVER", "10.42.0.1"));
            rows.add(row("GATEWAY", "10.42." + (Integer.parseInt(synth(pkg, "gateway", "hex4").substring(0,2),16)%250+1) + ".1"));
            rows.add(row("DNS 1", "1.1.1.1"));
            rows.add(row("NETWORK INTERFACE", "wlan" + (Integer.parseInt(synth(pkg, "iface", "hex4").substring(0,1),16)%4)));
        } else if ("telephony".equals(c)) {
            rows.add(row("IMEI (SIM 1)", pref(pkg, "imei1", synth(pkg, "imei1", "digits15"))));
            rows.add(row("IMEI (SIM 2)", pref(pkg, "imei2", synth(pkg, "imei2", "digits15"))));
            rows.add(row("MEID", synth(pkg, "meid", "hex14").toUpperCase(Locale.ROOT)));
            rows.add(row("IMSI", pref(pkg, "imsi", synth(pkg, "imsi", "digits15"))));
            rows.add(row("ICCID", pref(pkg, "iccid", synth(pkg, "iccid", "digits20"))));
            rows.add(row("MSISDN", "+1 202 555 " + synth(pkg, "msisdn", "digits4")));
            rows.add(row("MCC / MNC", "310 / 260"));
            rows.add(row("OPERATOR", "GHOST MOBILE " + synth(pkg, "operator", "digits4")));
            rows.add(row("COUNTRY ISO", "US"));
            rows.add(row("ROAMING", "FALSE"));
            rows.add(row("CARRIER ID", synth(pkg, "carrier_id", "digits5")));
            rows.add(row("EID", synth(pkg, "eid", "digits32")));
        } else if ("location".equals(c)) {
            rows.add(row("LATITUDE", config.preferences.getString("latitude:" + pkg, "37." + synth(pkg, "latitude", "digits8"))));
            rows.add(row("LONGITUDE", config.preferences.getString("longitude:" + pkg, "-122." + synth(pkg, "longitude", "digits8"))));
            rows.add(row("ALTITUDE", synth(pkg, "altitude", "digits4") + " m"));
            rows.add(row("ACCURACY", (Integer.parseInt(synth(pkg, "accuracy", "hex4").substring(0,2),16)%20+3) + ".0 m"));
            rows.add(row("TIMEZONE", "Etc/UTC"));
            rows.add(row("LOCALE", "en-US"));
            rows.add(row("COUNTRY", "US"));
            rows.add(row("CITY", "TEST CITY"));
        } else if ("accounts".equals(c)) {
            rows.add(row("GOOGLE ACCOUNT", "test.user@example.com"));
            rows.add(row("EMAIL", "test.user@example.com"));
            rows.add(row("USER ID", synth(pkg, "user_id", "digits8")));
            rows.add(row("DEVICE OWNER", "FALSE"));
            rows.add(row("WORK PROFILE", "NONE"));
            rows.add(row("PROFILE NAME", "PRIMARY"));
        } else if ("system".equals(c)) {
            rows.add(row("PACKAGE NAME", pkg));
            rows.add(row("SIGNATURE SHA-256", synth(pkg, "signature", "hex64").toUpperCase(Locale.ROOT)));
            rows.add(row("INSTALLER", "com.android.vending"));
            rows.add(row("APP SET ID", pref(pkg, "app_set_id", synth(pkg, "appset2", "hex32"))));
            rows.add(row("ANDROID USER", "0"));
            rows.add(row("PROCESS UID", synth(pkg, "uid", "digits5")));
            rows.add(row("DRM / MEDIA ID", synth(pkg, "drm_id", "hex32").toUpperCase(Locale.ROOT)));
            rows.add(row("WEBVIEW PROFILE ID", synth(pkg, "webview_id", "uuid")));
            rows.add(row("INSTALL SESSION ID", synth(pkg, "install_session", "uuid")));
        } else if ("hardware".equals(c)) {
            rows.add(row("BRAND", pref(pkg, "brand", "GhostViki")));
            rows.add(row("MODEL", pref(pkg, "model", "GV-" + synth(pkg, "model", "hex4").toUpperCase(Locale.ROOT))));
            rows.add(row("MANUFACTURER", pref(pkg, "manufacturer", "GhostViki Labs")));
            rows.add(row("DEVICE", pref(pkg, "device", "gv_" + synth(pkg, "device", "hex4"))));
            rows.add(row("PRODUCT", pref(pkg, "product", "gv_" + synth(pkg, "product", "hex4"))));
            rows.add(row("BOARD", "board_" + synth(pkg, "board", "hex4")));
            rows.add(row("BOOTLOADER", "GV" + synth(pkg, "bootloader", "hex14").toUpperCase(Locale.ROOT)));
            rows.add(row("PROCESSOR / SOC", "Google Tensor G4"));
            rows.add(row("ROM", "256 GB"));
            rows.add(row("ANDROID VERSION", "16"));
            rows.add(row("SECURITY PATCH", "2026-09-05"));
            rows.add(row("KERNEL", "6.1.99-android15"));
            rows.add(row("ABI", "arm64-v8a"));
            rows.add(row("DISPLAY PROFILE", (1080 + Integer.parseInt(synth(pkg, "display", "hex4").substring(0,2),16)%400) + " × 2400"));
            rows.add(row("MEMORY PROFILE", (6 + Integer.parseInt(synth(pkg, "memory", "hex4").substring(0,1),16)%7) + " GB"));
            rows.add(row("BUILD FINGERPRINT", pref(pkg, "fingerprint", "ghostviki/gv/gv:16/GV" + synth(pkg, "fingerprint", "hex14").toUpperCase(Locale.ROOT) + "/" + synth(pkg, "build_number", "digits8") + ":user/release-keys")));
        }
        return rows;
    }

    private String[] row(String name, String value) { return new String[]{name, value}; }

    private String pref(String pkg, String key, String fallback) {
        return config.preferences.getString(key + ":" + pkg, fallback);
    }

    private void valueCard(String title, String value) {
        LinearLayout card = column();
        card.setPadding(dp(14), dp(11), dp(14), dp(12));
        card.setBackground(shape(Color.rgb(5, 22, 14), Color.rgb(22, 93, 58), 13));

        TextView name = labelView(title, 12, TEXT, true);
        name.setLetterSpacing(0.04f);
        card.addView(name);

        TextView val = labelView(value, 13, GREEN, false);
        val.setTypeface(Typeface.MONOSPACE);
        val.setTextIsSelectable(true);
        val.setSingleLine(false);
        val.setPadding(0, dp(6), 0, 0);
        card.addView(val);

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(8);
        body.addView(card, p);
    }

    private String synth(String pkg, String key, String format) {
        long generation = config.preferences.getLong("generation", 0);
        String hex = digest(pkg + "|" + key + "|" + generation);
        switch (format) {
            case "hex4": return hex.substring(0, 4);
            case "hex14": return hex.substring(0, 14);
            case "hex16": return hex.substring(0, 16);
            case "hex32": return hex.substring(0, 32);
            case "hex64": return hex.substring(0, 64);
            case "uuid":
                return hex.substring(0,8)+"-"+hex.substring(8,12)+"-"+hex.substring(12,16)+"-"+hex.substring(16,20)+"-"+hex.substring(20,32);
            case "serial": return hex.substring(0,16).toUpperCase(Locale.ROOT);
            case "token22": return tokenFromHex(hex, 22);
            case "fcm": return tokenFromHex(hex, 22) + ":" + tokenFromHex(digest(hex), 96);
            case "mac": return mac(hex);
            case "ipv4": return "10.42." + (Integer.parseInt(hex.substring(0,2),16)%250+1) + "." + (Integer.parseInt(hex.substring(2,4),16)%250+1);
            case "ipv6": return "fd42:" + hex.substring(0,4) + ":" + hex.substring(4,8) + "::" + hex.substring(8,12);
            case "digits4": return digits(hex, 4);
            case "digits5": return digits(hex, 5);
            case "digits8": return digits(hex, 8);
            case "digits15": return digits(hex, 15);
            case "digits20": return digits(hex, 20);
            case "digits32": return digits(hex, 32);
            default: return hex.substring(0, 16);
        }
    }

    private String digest(String seed) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(seed.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(64);
            for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            return out.toString();
        } catch (Exception e) {
            return "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        }
    }

    private String tokenFromHex(String hex, int length) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
        StringBuilder out = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int p = (i * 2) % (hex.length() - 1);
            int n = Integer.parseInt(hex.substring(p, p + 2), 16);
            out.append(alphabet.charAt(n % alphabet.length()));
        }
        return out.toString();
    }

    private String digits(String hex, int length) {
        StringBuilder out = new StringBuilder(length);
        int i = 0;
        while (out.length() < length) {
            int p = (i * 2) % (hex.length() - 1);
            out.append(Integer.parseInt(hex.substring(p, p + 2), 16) % 10);
            i++;
        }
        return out.toString();
    }

    private String mac(String hex) {
        return (hex.substring(0,2)+":"+hex.substring(2,4)+":"+hex.substring(4,6)+":"+
                hex.substring(6,8)+":"+hex.substring(8,10)+":"+hex.substring(10,12)).toUpperCase(Locale.ROOT);
    }

    private void root() {
        heading("ROOT HIDE METHODS", "SUPPORTED APP-VISIBLE SIGNALS");
        toggle("SU FILE CHECKS", "Common SU paths checked through Java File APIs.", "hide_files");
        toggle("ROOT PACKAGE VISIBILITY", "Known package names in supported package-manager calls.", "hide_packages");
        button("CHOOSE TARGET APPS", this::selectTargets, false);
        button("OPEN DUCK DETECTOR", () -> launch("com.eltavine.duckdetector"), true);
    }

    private void location() {
        heading("LOCATION", "SELECTED-APP TEST LOCATION");
        List<String> targets = new ArrayList<>(config.targets());
        Collections.sort(targets);
        if (targets.isEmpty()) {
            button("CHOOSE TARGET APPS", this::selectTargets, true);
            return;
        }

        Spinner picker = new Spinner(this);
        List<String> labels = new ArrayList<>();
        for (String pkg : targets) labels.add(label(pkg).toUpperCase(Locale.ROOT));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels);
        picker.setAdapter(adapter);
        body.addView(picker, new LinearLayout.LayoutParams(-1, dp(52)));

        EditText latitude = input("LATITUDE  ·  -90 TO 90");
        EditText longitude = input("LONGITUDE  ·  -180 TO 180");
        TextView state = text("", 12, GREEN, true);

        picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int index, long id) {
                String pkg = targets.get(index);
                latitude.setText(config.preferences.getString("latitude:" + pkg, ""));
                longitude.setText(config.preferences.getString("longitude:" + pkg, ""));
                state.setText(config.preferences.getBoolean("location:" + pkg, false) ? "LOCATION PROFILE: ON" : "LOCATION PROFILE: OFF");
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        space(12);
        button("SAVE & ENABLE", () -> {
            try {
                Coordinates point = Coordinates.parse(latitude.getText().toString(), longitude.getText().toString());
                String pkg = targets.get(picker.getSelectedItemPosition());
                save(() -> config.setLocation(pkg, point, true), "Location saved.");
            } catch (IllegalArgumentException e) { toast(e.getMessage()); }
        }, true);

        button("STOP LOCATION CHANGE", () -> {
            String pkg = targets.get(picker.getSelectedItemPosition());
            save(() -> config.stopLocation(pkg), "Location change disabled.");
        }, false);
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
                rows[i] = names.get(pkg).toUpperCase(Locale.ROOT) + "\n" + pkg;
                checked[i] = selected.contains(pkg);
            }

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                new AlertDialog.Builder(this)
                        .setTitle("TARGET APPS")
                        .setMultiChoiceItems(rows, checked, (dialog, index, on) -> {
                            if (on) selected.add(packages.get(index)); else selected.remove(packages.get(index));
                        })
                        .setNegativeButton("CANCEL", null)
                        .setPositiveButton("SAVE", (dialog, which) ->
                                save(() -> config.setTargets(selected), "Targets saved."))
                        .show();
            });
        });
    }

    private void save(BooleanSupplier action, String message) {
        if (saving) return;
        saving = true;
        worker.execute(() -> {
            String result;
            try {
                result = action.getAsBoolean() ? message : "Could not save settings.";
            } catch (RuntimeException e) {
                result = "Could not save: " + e.getMessage();
            }
            String completed = result;
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
        control.setTextSize(16);
        control.setTextColor(TEXT);
        control.setMinHeight(dp(54));
        control.setChecked(config.preferences.getBoolean(key, false));
        control.setOnCheckedChangeListener((button, checked) ->
                save(() -> config.setFlag(key, checked), "Method saved."));
        body.addView(control, new LinearLayout.LayoutParams(-1, -2));
        text(detail, 12, MUTED, false);
        space(16);
    }

    private void launch(String pkg) {
        Intent intent = getPackageManager().getLaunchIntentForPackage(pkg);
        if (intent == null) toast("Install " + label(pkg) + " first.");
        else startActivity(intent);
    }

    private String label(String pkg) {
        try {
            return getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException ignored) {
            return pkg;
        }
    }

    private void heading(String title, String description) {
        TextView h = text(title, 27, TEXT, true);
        h.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        h.setLetterSpacing(0.05f);
        space(5);
        TextView d = text(description, 11, GREEN, true);
        d.setLetterSpacing(0.10f);
        space(22);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView label = labelView(value, size, color, bold);
        body.addView(label, new LinearLayout.LayoutParams(-1, -2));
        return label;
    }

    private TextView labelView(String value, int size, int color, boolean bold) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextSize(size);
        label.setTextColor(color);
        label.setLineSpacing(dp(2), 1f);
        if (bold) label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return label;
    }

    private void space(int height) {
        body.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height)));
    }

    private void button(String value, Runnable action, boolean primary) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(true);
        button.setTextSize(13);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(primary ? INK : GREEN);
        button.setMinHeight(dp(50));
        button.setBackground(shape(primary ? GREEN : PANEL, primary ? GREEN : GREEN_DARK, 14));
        button.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(9);
        body.addView(button, params);
    }

    private EditText input(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER |
                InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setMinHeight(dp(56));
        body.addView(input, new LinearLayout.LayoutParams(-1, -2));
        return input;
    }

    private GradientDrawable shape(int fill, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private int dp(int size) {
        return Math.round(size * getResources().getDisplayMetrics().density);
    }
}
