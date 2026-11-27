/*
 * Compile-time shadow of android.provider.Settings.
 *
 * This class shadows the SDK's android.jar copy (via -Xbootclasspath/p) so the
 * app can direct-link hidden Settings.*ForUser members without reflection.
 * It is compileOnly: at runtime the real framework class is used. Constant
 * values below MUST match the platform / crDroid values on the target device
 * (verified against a crDroid 10.6 / Android 14 device).
 */
package android.provider;

import android.content.ContentResolver;
import android.net.Uri;

public final class Settings {

    public static final class System {
        public static final String SCREEN_BRIGHTNESS_MODE = "screen_brightness_mode";
        public static final int SCREEN_BRIGHTNESS_MODE_MANUAL = 0;
        public static final int SCREEN_BRIGHTNESS_MODE_AUTOMATIC = 1;

        /** crDroid framework addition. */
        public static final String GAMESPACE_GAME_LIST = "gamespace_game_list";

        private System() { }

        public static String getString(ContentResolver resolver, String name) {
            throw new UnsupportedOperationException("stub");
        }

        public static String getStringForUser(ContentResolver resolver, String name, int userId) {
            throw new UnsupportedOperationException("stub");
        }

        public static boolean putString(ContentResolver resolver, String name, String value) {
            throw new UnsupportedOperationException("stub");
        }

        public static boolean putStringForUser(ContentResolver resolver, String name,
                String value, int userId) {
            throw new UnsupportedOperationException("stub");
        }

        public static int getInt(ContentResolver resolver, String name, int def) {
            throw new UnsupportedOperationException("stub");
        }

        public static boolean putInt(ContentResolver resolver, String name, int value) {
            throw new UnsupportedOperationException("stub");
        }

        public static int getIntForUser(ContentResolver resolver, String name, int def,
                int userId) {
            throw new UnsupportedOperationException("stub");
        }

        public static boolean putIntForUser(ContentResolver resolver, String name, int value,
                int userId) {
            throw new UnsupportedOperationException("stub");
        }

        public static float getFloatForUser(ContentResolver resolver, String name, float def,
                int userId) {
            throw new UnsupportedOperationException("stub");
        }

        public static boolean putFloatForUser(ContentResolver resolver, String name, float value,
                int userId) {
            throw new UnsupportedOperationException("stub");
        }

        public static long getLongForUser(ContentResolver resolver, String name, long def,
                int userId) {
            throw new UnsupportedOperationException("stub");
        }

        public static boolean putLongForUser(ContentResolver resolver, String name, long value,
                int userId) {
            throw new UnsupportedOperationException("stub");
        }

        public static Uri getUriFor(String name) {
            throw new UnsupportedOperationException("stub");
        }

        public static Uri getUriFor(ContentResolver resolver, String name) {
            throw new UnsupportedOperationException("stub");
        }
    }

    public static final class Global {
        public static final String HEADS_UP_NOTIFICATIONS_ENABLED =
                "heads_up_notifications_enabled";
        public static final String ADB_ENABLED = "adb_enabled";

        private Global() { }

        public static int getInt(ContentResolver resolver, String name, int def) {
            throw new UnsupportedOperationException("stub");
        }

        public static boolean putInt(ContentResolver resolver, String name, int value) {
            throw new UnsupportedOperationException("stub");
        }
    }

    public static final class Secure {
        /** Per-app GameMode override key (Settings.Secure row). */
        public static final String GAME_OVERLAY = "game_overlay";

        private Secure() { }

        public static String getString(ContentResolver resolver, String name) {
            throw new UnsupportedOperationException("stub");
        }

        public static boolean putString(ContentResolver resolver, String name, String value) {
            throw new UnsupportedOperationException("stub");
        }
    }

    private Settings() { }
}
