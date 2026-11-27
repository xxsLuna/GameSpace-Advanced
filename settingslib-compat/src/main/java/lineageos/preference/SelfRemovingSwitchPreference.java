/*
 * Minimal replacement for LineagePreferenceLib's
 * lineageos.preference.SelfRemovingSwitchPreference, the base class of
 * crDroid's SystemSettingSwitchPreference (which is instantiated from
 * GameSpace's root_preferences.xml).
 *
 * Contract preserved from LineageOS: subclasses provide the actual
 * Settings.System-backed storage through isPersisted()/getBoolean()/
 * putBoolean(); the preference hides itself when the underlying settings key
 * does not exist on the device ("self removing").
 */
package lineageos.preference;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.SwitchPreference;

public abstract class SelfRemovingSwitchPreference extends SwitchPreference {

    public SelfRemovingSwitchPreference(Context context) {
        super(context);
    }

    public SelfRemovingSwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SelfRemovingSwitchPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /** Whether the underlying settings key exists on this device. */
    protected abstract boolean isPersisted();

    /** Persist the switch state to the settings provider. */
    protected abstract void putBoolean(String key, boolean value);

    /** Read the switch state from the settings provider. */
    protected abstract boolean getBoolean(String key, boolean defaultValue);

    @Override
    public void onAttached() {
        super.onAttached();
        setVisible(isPersisted());
    }

    @Override
    protected boolean shouldPersist() {
        return true;
    }

    @Override
    protected boolean persistBoolean(boolean value) {
        putBoolean(getKey(), value);
        return true;
    }

    @Override
    protected boolean getPersistedBoolean(boolean defValue) {
        return getBoolean(getKey(), defValue);
    }
}
