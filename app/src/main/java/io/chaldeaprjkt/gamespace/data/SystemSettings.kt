/*
 * Copyright (C) 2021 Chaldeaprjkt
 *               2022 crDroid Android Project
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
package io.chaldeaprjkt.gamespace.data

import android.content.Context
import android.provider.Settings
import io.chaldeaprjkt.gamespace.utils.GameModeUtils
import javax.inject.Inject

class SystemSettings @Inject constructor(
    context: Context,
    private val gameModeUtils: GameModeUtils
) {

    private val resolver = context.contentResolver

    /*
     * Upstream used Settings.System.*ForUser(USER_CURRENT) everywhere, which
     * is fine for an android.uid.system app but demands INTERACT_ACROSS_USERS
     * _FULL for a module-installed app with its own uid. Plain public access
     * targets the calling user just the same; writes fall back to root
     * `settings put` when the caller lacks WRITE_SETTINGS, so game-list and
     * gesture toggles keep working in every install scenario.
     */
    private fun getSystemString(key: String): String? =
        Settings.System.getString(resolver, key)

    private fun getSystemInt(key: String, def: Int): Int =
        getSystemString(key)?.toIntOrNull() ?: def

    private fun putSystem(key: String, value: String): Boolean =
        try {
            Settings.System.putString(resolver, key, value)
        } catch (e: SecurityException) {
            io.chaldeaprjkt.gamespace.burnin.RootShell.settingsPut("system", key, value)
        }

    var headsup
        get() = Settings.Global.getInt(
            resolver, Settings.Global.HEADS_UP_NOTIFICATIONS_ENABLED, 1) == 1
        set(it) {
            Settings.Global.putInt(
                resolver, Settings.Global.HEADS_UP_NOTIFICATIONS_ENABLED,
                it.toInt()
            )
        }

    var autoBrightness
        get() =
            getSystemInt(
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        set(auto) {
            putSystem(
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                (if (auto) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL).toString()
            )
        }

    // The upstream LineageSettings.System.KEY_THREE_FINGERS_SWIPE_ACTION
    // resolves to this crDroid Settings.System key (verified on a crDroid
    // 10.6 / Android 14 device).
    var threeScreenshot
        get() = getSystemInt("three_finger_gesture", 0)
        set(value) {
            putSystem("three_finger_gesture", value.toString())
        }

    var userGames
        get() =
            getSystemString(Settings.System.GAMESPACE_GAME_LIST)
                ?.split(";")
                ?.toList()?.filter { it.isNotEmpty() }
                ?.map { UserGame.fromSettings(it) } ?: emptyList()
        set(games) {
            putSystem(
                Settings.System.GAMESPACE_GAME_LIST,
                if (games.isEmpty()) "" else
                    games.joinToString(";") { it.toString() }
            )
            gameModeUtils.setupBatteryMode(games.isNotEmpty())
        }

    var adbEnabled
        get() = Settings.Global.getInt(
            resolver, Settings.Global.ADB_ENABLED, 0
        ) == 1
        set(it) {
            Settings.Global.putInt(
                resolver, Settings.Global.ADB_ENABLED,
                it.toInt()
            )
        }

    private fun Boolean.toInt() = if (this) 1 else 0
}
