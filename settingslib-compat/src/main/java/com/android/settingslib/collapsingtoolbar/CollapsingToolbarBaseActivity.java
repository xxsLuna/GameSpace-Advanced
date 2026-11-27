/*
 * Minimal stand-in for the AOSP SettingsLib
 * com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity.
 *
 * Provides the same contract GameSpace relies on: a layout exposing
 * com.android.settingslib.R.id.content_frame under a (collapsing) toolbar,
 * with the activity title taken from the standard title extras.
 */
package com.android.settingslib.collapsingtoolbar;

import android.os.Bundle;
import android.text.TextUtils;

import androidx.appcompat.app.AppCompatActivity;

import com.android.settingslib.R;
import com.google.android.material.appbar.CollapsingToolbarLayout;

public abstract class CollapsingToolbarBaseActivity extends AppCompatActivity {

    private static final String EXTRA_TITLE = ":android:extra:title";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.collapsing_toolbar_base_layout);

        CollapsingToolbarLayout collapsing =
                findViewById(R.id.collapsing_toolbar_layout);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        if (TextUtils.isEmpty(title)) {
            title = getIntent().getStringExtra("android.intent.extra.TITLE");
        }
        if (TextUtils.isEmpty(title)) {
            CharSequence self = getTitle();
            title = TextUtils.isEmpty(self) ? getString(R.string.app_name) : self.toString();
        }
        if (collapsing != null) {
            collapsing.setTitleEnabled(false);
        }
        setTitle(title);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title);
        }
    }
}
