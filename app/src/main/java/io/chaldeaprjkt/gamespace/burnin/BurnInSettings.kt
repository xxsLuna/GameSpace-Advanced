/*
 * Copyright (C) 2026 GameSpace BurnIn Guard contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.chaldeaprjkt.gamespace.burnin

import android.content.Context
import androidx.preference.PreferenceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * User-facing Burn-In Guard configuration. Stored in the same default
 * SharedPreferences file as [AppSettings] so it is directly backed by the
 * Preference screen (setPreferencesFromResource default store).
 */
class BurnInSettings @Inject constructor(@ApplicationContext private val context: Context) {

    private val db by lazy { PreferenceManager.getDefaultSharedPreferences(context) }

    /** 0 = off, 1 = Galaxy-style overlay lock, 2 = Xiaomi-style backlight off. */
    var mode: Int
        get() = db.getString(KEY_MODE, "0")?.toIntOrNull() ?: 0
        set(value) = db.edit().putString(KEY_MODE, value.toString()).apply()

    /** Overlay scrim alpha, percent, 80..100. */
    var overlayOpacity: Int
        get() = db.getInt(KEY_OPACITY, 100).coerceIn(80, 100)
        set(value) = db.edit().putInt(KEY_OPACITY, value.coerceIn(80, 100)).apply()

    /** Idle minutes before auto-activation; 0 disables the timer. */
    var idleMinutes: Int
        get() = db.getString(KEY_IDLE, "0")?.toIntOrNull() ?: 0
        set(value) = db.edit().putString(KEY_IDLE, value.toString()).apply()

    /** scaling_max_freq cap while active, percent of the original maximum. */
    var cpuLimitPercent: Int
        get() = db.getInt(KEY_CPU_LIMIT, 50).coerceIn(20, 100)
        set(value) = db.edit().putInt(KEY_CPU_LIMIT, value.coerceIn(20, 100)).apply()

    /** Pin min/peak_refresh_rate to 60 while active. */
    var lockRefreshRate: Boolean
        get() = db.getBoolean(KEY_LOCK_REFRESH, false)
        set(value) = db.edit().putBoolean(KEY_LOCK_REFRESH, value).apply()

    /** Hard auto-release timeout in minutes, so an abandoned lock never keeps the panel dark forever. */
    var maxLockMinutes: Int
        get() = db.getString(KEY_MAX_LOCK, "10")?.toIntOrNull() ?: 10
        set(value) = db.edit().putString(KEY_MAX_LOCK, value.toString()).apply()

    companion object {
        const val KEY_MODE = "gamespace_burnin_mode"
        const val KEY_OPACITY = "gamespace_burnin_opacity"
        const val KEY_IDLE = "gamespace_burnin_idle"
        const val KEY_CPU_LIMIT = "gamespace_burnin_cpu_limit"
        const val KEY_LOCK_REFRESH = "gamespace_burnin_lock_refresh"
        const val KEY_MAX_LOCK = "gamespace_burnin_max_lock"
    }
}
