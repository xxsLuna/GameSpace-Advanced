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

/**
 * Temporarily pins Settings.System min/peak_refresh_rate to 60 while the
 * guard runs (optional toggle), restoring the previous values (or deleting
 * them when unset) on release. Goes through root `settings` so no extra
 * WRITE_SETTINGS grant is needed for the system namespace.
 */
class RefreshRateLimiter {
    private var savedMin: String? = null
    private var savedPeak: String? = null
    private var applied = false

    fun lock() {
        savedMin = RootShell.settingsGet("system", "min_refresh_rate")
        savedPeak = RootShell.settingsGet("system", "peak_refresh_rate")
        applied = RootShell.settingsPut("system", "min_refresh_rate", "60") &&
                RootShell.settingsPut("system", "peak_refresh_rate", "60")
    }

    fun restore() {
        if (!applied) return
        applied = false
        listOf(
            "min_refresh_rate" to savedMin,
            "peak_refresh_rate" to savedPeak,
        ).forEach { (key, value) ->
            if (value == null) RootShell.settingsDelete("system", key)
            else RootShell.settingsPut("system", key, value)
        }
        savedMin = null
        savedPeak = null
    }
}
