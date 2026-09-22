package android.content;

import java.util.Map;
import java.util.Set;

/** JVM fixture only, never packaged into either Android app. */
public interface SharedPreferences {
    Map<String, ?> getAll();
    String getString(String key, String fallback);
    Set<String> getStringSet(String key, Set<String> fallback);
    int getInt(String key, int fallback);
    long getLong(String key, long fallback);
    boolean getBoolean(String key, boolean fallback);
    boolean contains(String key);
    Editor edit();
    interface Editor {
        Editor putString(String key, String value);
        Editor putStringSet(String key, Set<String> value);
        Editor putInt(String key, int value);
        Editor putLong(String key, long value);
        Editor putBoolean(String key, boolean value);
        boolean commit();
    }
}
