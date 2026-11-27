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

import android.util.Log
import java.io.File

/**
 * Mode 2 controller: physically blanks the LCD/OLED backlight by writing 0
 * to the panel brightness node, keeping the GPU/frame pipeline and the game
 * process alive (unlike PowerManager.goToSleep(), which pauses the game).
 *
 * Node discovery first tries the standard /sys/class/backlight/NAME/brightness,
 * then falls back to MTK/Qualcomm style /sys/class/leds/NAME/brightness nodes
 * (the begonia-class MTK devices expose no /sys/class/backlight at all).
 * The previous brightness value and max_brightness are cached in memory so a
 * crash-free restore is always possible; if the node cannot be driven the
 * caller must fall back to Mode 1.
 */
class BacklightController {
    private val node: String?
    private val maxBrightness: Int

    init {
        // Discovery itself must run as root: an app-domain process cannot even
        // list /sys/class/leds (SELinux sysfs_leds), so directory enumeration
        // here would always come back empty. One su-side glob covers both the
        // standard and the MTK/Qualcomm vendor node layouts.
        val listing = RootShell.exec(
            "ls -1 /sys/class/backlight/*/brightness " +
                "/sys/class/leds/*backlight*/brightness /sys/class/leds/*lcd*/brightness " +
                "/sys/class/leds/*screen*/brightness 2>/dev/null"
        )?.takeIf { it.stdout.isNotEmpty() }?.stdout
        node = listing
            ?.lines()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.firstOrNull { RootShell.readNode(it) != null }
        maxBrightness = node
            ?.let { RootShell.readNode(it.substringBeforeLast('/') + "/max_brightness") }
            ?.toIntOrNull() ?: 255
        Log.i(TAG, "backlight node=$node max=$maxBrightness")
    }

    /** True when a writable brightness node was found on this device. */
    fun isSupported(): Boolean = node != null

    private var originalValue: String? = null

    /**
     * Caches the current brightness and writes 0 (verified by read-back).
     * @return true when the panel actually went dark (else caller falls back).
     */
    fun turnOff(): Boolean {
        val node = node ?: return false
        val original = RootShell.readNode(node) ?: return false
        originalValue = original
        if (!RootShell.writeNode(node, "0")) {
            originalValue = null
            return false
        }
        val now = RootShell.readNode(node)?.toIntOrNull() ?: -1
        if (now != 0) {
            RootShell.writeNode(node, original)
            originalValue = null
            return false
        }
        return true
    }

    /**
     * Restores the cached brightness (idempotent; safe after a failed run or
     * a process restart, in which case nothing is known to be stale).
     */
    fun restore() {
        val node = node ?: return
        val original = originalValue ?: run {
            // Lost in a crash: if the panel somehow reads 0, push max so the
            // user is not left with a black screen.
            if (RootShell.readNode(node)?.toIntOrNull() == 0) {
                RootShell.writeNode(node, maxBrightness.toString())
            }
            return
        }
        RootShell.writeNode(node, original)
        originalValue = null
    }

    companion object {
        private const val TAG = "GameSpaceBacklight"
    }
}
