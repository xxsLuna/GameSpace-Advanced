/*
 * Minimal stand-in for SettingsLib's MainSwitchPreference (the big header
 * switch). Behaviour-wise it is a SwitchPreferenceCompat; the animated
 * switch artwork is not reproduced.
 */
package com.android.settingslib.widget;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.SwitchPreferenceCompat;

public class MainSwitchPreference extends SwitchPreferenceCompat {

    public MainSwitchPreference(Context context) {
        super(context);
    }

    public MainSwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MainSwitchPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setChecked(boolean checked) {
        super.setChecked(checked);
    }
}
