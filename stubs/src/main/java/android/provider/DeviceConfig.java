/*
 * Compile-time stub of android.provider.DeviceConfig.
 *
 * DeviceConfig is @SystemApi, hence absent from the public android.jar while
 * being present on every device's bootclasspath. Only the overload GameSpace
 * uses is declared. compileOnly: the real class is used at runtime.
 */
package android.provider;

public final class DeviceConfig {

    private DeviceConfig() { }

    public static String getString(String namespace, String name, String defaultValue) {
        throw new UnsupportedOperationException("stub");
    }

    public static String getString(String namespace, String name) {
        throw new UnsupportedOperationException("stub");
    }
}
