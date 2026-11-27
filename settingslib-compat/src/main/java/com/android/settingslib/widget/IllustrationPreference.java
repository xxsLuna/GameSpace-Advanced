/*
 * Minimal stand-in for SettingsLib's IllustrationPreference.
 * GameSpace only uses it as a decorative header; the lottie_rawRes attribute
 * is accepted but not animated here (kept as a plain non-selectable preference
 * so the settings screen layout is preserved).
 */
package com.android.settingslib.widget;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.Preference;

public class IllustrationPreference extends Preference {

    public IllustrationPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setSelectable(false);
    }

    public IllustrationPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setSelectable(false);
    }
}
