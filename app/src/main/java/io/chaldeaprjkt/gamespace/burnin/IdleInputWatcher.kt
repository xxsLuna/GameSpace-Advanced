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
import android.hardware.input.InputManager
import android.os.Looper
import android.util.Log
import android.view.InputChannel
import android.view.InputEvent
import android.view.InputEventReceiver

/**
 * Watches every touch reaching the foreground game via
 * InputManager.monitorGestureInput (@hide, MONITOR_INPUT) so the idle timer
 * can distinguish "AFK farming" from "actively playing". The @hide surface is
 * reached by reflection; MONITOR_INPUT is signature-only, so a module install
 * outside the system uid is refused (no crash) and the idle timer falls back
 * to PowerActivityClock.
 */
class IdleInputWatcher(
    private val context: Context,
    private val onUserActivity: () -> Unit,
) {
    private val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager

    private class WatchReceiver(
        channel: InputChannel,
        looper: Looper,
        private val onEvent: () -> Unit,
    ) : InputEventReceiver(channel, looper) {
        override fun dispatchInputEvent(seq: Int, event: InputEvent?) {
            onEvent()
        }
    }

    private var monitor: Any? = null
    private var receiver: WatchReceiver? = null

    /** @return true when the monitor is live. */
    fun start(): Boolean = try {
        val factory = inputManager.javaClass.methods.firstOrNull {
            it.name == "monitorGestureInput" && it.parameterTypes.size == 2
        } ?: return false
        val created = factory.invoke(inputManager, context, "gamespace-burnin") ?: return false
        val channel = created.javaClass.getMethod("getToken").invoke(created) as InputChannel
        receiver = WatchReceiver(channel, Looper.getMainLooper()) {
            try {
                onUserActivity()
            } catch (ignored: Throwable) {
            }
        }
        monitor = created
        true
    } catch (t: Throwable) {
        Log.w(TAG, "monitorGestureInput unavailable (${t.javaClass.simpleName})")
        false
    }

    fun stop() {
        try {
            receiver?.dispose()
        } catch (ignored: Throwable) {
        }
        try {
            monitor?.javaClass?.getMethod("dispose")?.invoke(monitor)
        } catch (ignored: Throwable) {
        }
        receiver = null
        monitor = null
    }

    companion object {
        private const val TAG = "GameSpaceIdleWatch"
    }
}
