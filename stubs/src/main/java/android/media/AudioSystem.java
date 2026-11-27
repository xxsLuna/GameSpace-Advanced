/*
 * Compile-time stub of android.media.AudioSystem (hidden framework class).
 * Constant values mirror audio_policy.h (AUDIO_POLICY_FORCE_* /
 * AUDIO_POLICY_FORCE_FOR_*). compileOnly: the real class is used at runtime.
 */
package android.media;

public final class AudioSystem {

    public static final int FOR_MEDIA = 0;
    public static final int FOR_COMMUNICATION = 1;

    public static final int FORCE_NONE = 0;
    public static final int FORCE_SPEAKER = 1;
    public static final int FORCE_HEADPHONES = 2;
    public static final int FORCE_BT_SCO = 3;

    private AudioSystem() { }

    public static void setForceUse(int usage, int forcedConfig) {
        throw new UnsupportedOperationException("stub");
    }
}
