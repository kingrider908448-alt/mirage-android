package dev.ghostviki.app.hooks;

import android.location.Location;
import android.os.SystemClock;

import java.lang.reflect.Array;
import java.util.List;
import java.util.Map;

import dev.ghostviki.app.ConfigStore;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

final class SystemLocationHooks {
    private SystemLocationHooks() {}

    static void install(ClassLoader loader) {
        GlobalConfig config = new GlobalConfig();

        hookReturnLocation(loader, config);
        hookBinderLocationCallback(loader, config,
                "android.location.ILocationListener$Stub$Proxy", "onLocationChanged");
        hookBinderLocationCallback(loader, config,
                "android.location.ILocationCallback$Stub$Proxy", "onLocation");
        hookPendingIntentDelivery(loader, config);

        XposedBridge.log("GhostViki: system location hook registration attempted");
    }

    private static void hookReturnLocation(ClassLoader loader, GlobalConfig config) {
        Class<?> service = XposedHelpers.findClassIfExists(
                "com.android.server.location.LocationManagerService", loader);
        if (service == null) {
            XposedBridge.log("GhostViki: LocationManagerService not found");
            return;
        }
        XposedBridge.hookAllMethods(service, "getLastLocation", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                State state = config.get();
                if (!state.enabled || param.hasThrowable()) return;
                Object result = param.getResult();
                if (result instanceof Location) {
                    param.setResult(rewrite((Location) result, state));
                }
            }
        });
    }

    private static void hookBinderLocationCallback(
            ClassLoader loader, GlobalConfig config, String className, String method) {
        Class<?> type = XposedHelpers.findClassIfExists(className, loader);
        if (type == null) {
            XposedBridge.log("GhostViki: " + className + " not found");
            return;
        }
        XposedBridge.hookAllMethods(type, method, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                State state = config.get();
                if (!state.enabled) return;
                for (int i = 0; i < param.args.length; i++) {
                    param.args[i] = rewriteAny(param.args[i], state);
                }
            }
        });
    }

    private static void hookPendingIntentDelivery(ClassLoader loader, GlobalConfig config) {
        Class<?> type = XposedHelpers.findClassIfExists(
                "com.android.server.location.provider.LocationProviderManager$LocationPendingIntentTransport",
                loader);
        if (type == null) {
            XposedBridge.log("GhostViki: LocationPendingIntentTransport not found");
            return;
        }
        XposedBridge.hookAllMethods(type, "deliverOnLocationChanged", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                State state = config.get();
                if (!state.enabled) return;
                for (int i = 0; i < param.args.length; i++) {
                    param.args[i] = rewriteAny(param.args[i], state);
                }
            }
        });
    }

    private static Object rewriteAny(Object value, State state) {
        if (value == null) return null;
        if (value instanceof Location) return rewrite((Location) value, state);

        if (value instanceof List<?>) {
            for (Object item : (List<?>) value) {
                if (item instanceof Location) rewriteInPlace((Location) item, state);
            }
            return value;
        }

        Class<?> type = value.getClass();
        if (type.isArray() && Location.class.isAssignableFrom(type.getComponentType())) {
            int count = Array.getLength(value);
            for (int i = 0; i < count; i++) {
                Object item = Array.get(value, i);
                if (item instanceof Location) Array.set(value, i, rewrite((Location) item, state));
            }
            return value;
        }

        if ("android.location.LocationResult".equals(type.getName())) {
            try {
                Object list = XposedHelpers.callMethod(value, "asList");
                if (list instanceof List<?>) {
                    for (Object item : (List<?>) list) {
                        if (item instanceof Location) rewriteInPlace((Location) item, state);
                    }
                }
            } catch (Throwable e) {
                XposedBridge.log("GhostViki: LocationResult rewrite failed: " +
                        e.getClass().getSimpleName());
            }
        }
        return value;
    }

    private static Location rewrite(Location source, State state) {
        if (source == null) return null;
        Location changed = new Location(source);
        rewriteInPlace(changed, state);
        return changed;
    }

    private static void rewriteInPlace(Location location, State state) {
        location.setLatitude(state.latitude);
        location.setLongitude(state.longitude);
        location.removeAltitude();
        location.removeSpeed();
        location.removeBearing();
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            location.removeVerticalAccuracy();
            location.removeSpeedAccuracy();
            location.removeBearingAccuracy();
        }
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            location.removeMslAltitude();
            location.removeMslAltitudeAccuracy();
        }
        // Keep the framework-wide override explicitly visible as a simulated test location.
        location.setMock(true);
        if (!location.hasAccuracy()) location.setAccuracy(5f);
        if (location.getTime() == 0) location.setTime(System.currentTimeMillis());
        if (location.getElapsedRealtimeNanos() == 0)
            location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
    }

    private static final class GlobalConfig {
        private final XSharedPreferences preferences =
                new XSharedPreferences(ConfigStore.PACKAGE, ConfigStore.PREFS);
        private volatile State state = State.OFF;
        private volatile long nextRefresh;

        State get() {
            long now = SystemClock.elapsedRealtime();
            if (now < nextRefresh) return state;
            synchronized (this) {
                if (now < nextRefresh) return state;
                try {
                    preferences.reload();
                    Map<String, ?> data = preferences.getAll();
                    boolean enabled = Boolean.TRUE.equals(data.get("system_location_enabled"));
                    double lat = parse(data.get("system_latitude"), Double.NaN);
                    double lon = parse(data.get("system_longitude"), Double.NaN);
                    if (enabled && Double.isFinite(lat) && Double.isFinite(lon)
                            && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180) {
                        state = new State(true, lat, lon);
                    } else {
                        state = State.OFF;
                    }
                } catch (RuntimeException e) {
                    state = State.OFF;
                    XposedBridge.log("GhostViki: system location config read failed: " +
                            e.getClass().getSimpleName());
                } finally {
                    nextRefresh = now + 750;
                }
            }
            return state;
        }

        private static double parse(Object value, double fallback) {
            if (!(value instanceof String)) return fallback;
            try { return Double.parseDouble((String) value); }
            catch (NumberFormatException e) { return fallback; }
        }
    }

    private static final class State {
        static final State OFF = new State(false, 0, 0);
        final boolean enabled;
        final double latitude;
        final double longitude;

        State(boolean enabled, double latitude, double longitude) {
            this.enabled = enabled;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }
}
