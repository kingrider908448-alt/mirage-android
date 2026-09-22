package dev.ghostviki.app.hooks;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import dev.ghostviki.core.Coordinates;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

final class LocationHooks {
    private LocationHooks() {}
    static void install(HookConfig config, ClassLoader loader) {
        XposedBridge.hookAllMethods(LocationManager.class, "getLastKnownLocation", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (!param.hasThrowable() && param.getResult() instanceof Location)
                    param.setResult(replace((Location) param.getResult(), config));
            }
        });
        XposedBridge.hookAllMethods(LocationManager.class, "getCurrentLocation", new XC_MethodHook() {
            @Override @SuppressWarnings("unchecked") protected void beforeHookedMethod(MethodHookParam param) {
                for (int i = 0; i < param.args.length; i++) {
                    if (param.args[i] instanceof Consumer<?>) {
                        Consumer<Location> original = (Consumer<Location>) param.args[i];
                        param.args[i] = (Consumer<Location>) value -> original.accept(replace(value, config));
                    }
                }
            }
        });

        Map<LocationListener, LocationListener> listeners = new IdentityHashMap<>();
        XC_MethodHook register = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                for (int i = 0; i < param.args.length; i++) {
                    if (param.args[i] instanceof LocationListener) {
                        LocationListener original = (LocationListener) param.args[i];
                        synchronized (listeners) {
                            // Public overloads delegate to each other. Do not wrap our wrapper
                            // again, or removeUpdates(original) would miss the registered listener.
                            if (listeners.containsValue(original)) continue;
                            if (!listeners.containsKey(original)) {
                                listeners.put(original, wrap(original, config));
                                param.setObjectExtra("ghostviki_new_listener", original);
                            }
                            param.args[i] = listeners.get(original);
                        }
                    }
                }
            }
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (param.hasThrowable()) {
                    Object original = param.getObjectExtra("ghostviki_new_listener");
                    if (original != null) synchronized (listeners) { listeners.remove(original); }
                }
            }
        };
        XposedBridge.hookAllMethods(LocationManager.class, "requestLocationUpdates", register);
        XposedBridge.hookAllMethods(LocationManager.class, "requestSingleUpdate", register);
        XposedBridge.hookAllMethods(LocationManager.class, "removeUpdates", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args.length == 0 || !(param.args[0] instanceof LocationListener)) return;
                synchronized (listeners) {
                    LocationListener original = (LocationListener) param.args[0];
                    LocationListener wrapper = listeners.get(original);
                    if (wrapper != null) {
                        param.setObjectExtra("ghostviki_remove_listener", original);
                        param.args[0] = wrapper;
                    }
                }
            }
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (!param.hasThrowable()) {
                    Object original = param.getObjectExtra("ghostviki_remove_listener");
                    if (original != null) synchronized (listeners) { listeners.remove(original); }
                }
            }
        });

        // Google fused callback deliveries; optional, because some apps do not include Play services.
        // Task<Location> one-shot results, geofences and PendingIntent deliveries are not covered.
        Class<?> result = XposedHelpers.findClassIfExists("com.google.android.gms.location.LocationResult", loader);
        if (result != null) {
            XposedBridge.hookAllMethods(result, "getLastLocation", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (!param.hasThrowable() && param.getResult() instanceof Location)
                        param.setResult(replace((Location) param.getResult(), config));
                }
            });
            XposedBridge.hookAllMethods(result, "getLocations", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.hasThrowable() || !(param.getResult() instanceof List<?>)) return;
                    List<Object> locations = new ArrayList<>();
                    for (Object item : (List<?>) param.getResult())
                        locations.add(item instanceof Location ? replace((Location) item, config) : item);
                    param.setResult(locations);
                }
            });
        }
    }

    private static LocationListener wrap(LocationListener original, HookConfig config) {
        return new LocationListener() {
            @Override public void onLocationChanged(Location location) { original.onLocationChanged(replace(location, config)); }
            @Override public void onLocationChanged(List<Location> locations) {
                List<Location> changed = new ArrayList<>();
                for (Location location : locations) changed.add(replace(location, config));
                original.onLocationChanged(changed);
            }
            @Override public void onFlushComplete(int requestCode) { original.onFlushComplete(requestCode); }
            @Override public void onProviderEnabled(String provider) { original.onProviderEnabled(provider); }
            @Override public void onProviderDisabled(String provider) { original.onProviderDisabled(provider); }
            @Override @SuppressWarnings("deprecation") public void onStatusChanged(String provider, int status, Bundle extras) {
                original.onStatusChanged(provider, status, extras);
            }
        };
    }

    private static Location replace(Location source, HookConfig config) {
        Coordinates point = config.get().coordinates;
        if (point == null || source == null) return source;
        Location changed = new Location(source);
        changed.setLatitude(point.latitude);
        changed.setLongitude(point.longitude);
        changed.removeAltitude();
        changed.removeSpeed();
        changed.removeBearing();
        if (Build.VERSION.SDK_INT >= 33) {
            changed.removeVerticalAccuracy();
            changed.removeSpeedAccuracy();
            changed.removeBearingAccuracy();
        }
        if (Build.VERSION.SDK_INT >= 34) {
            changed.removeMslAltitude();
            changed.removeMslAltitudeAccuracy();
        }
        // Preserve age, provider, accuracy and mock flag. No global Location getter replacement.
        return changed;
    }
}
