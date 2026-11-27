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
 * Fallback user-activity source for the idle timer. The preferred one is the
 * InputManager gesture monitor, but MONITOR_INPUT is signature-only: a module
 * installed app outside the system uid never gets it. PowerManagerService
 * already tracks the last real user activity (touch/key) and exposes it in
 * its dump as an elapsed-realtime timestamp. The dump has to come through
 * root: a non-DUMP caller only receives the sanitized dump without the
 * activity lines.
 */
object PowerActivityClock {

    private val lineRegex = Regex("mLastUserActivityTime\\(excludingAttention\\)=(\\d+)")

    /** @return last user activity as SystemClock.elapsedRealtime(), or null. */
    fun lastUserActivityElapsed(): Long? =
        RootShell.exec("dumpsys power | grep -m2 mLastUserActivityTime")
            ?.stdout
            ?.let { dump ->
                lineRegex.findAll(dump)
                    .mapNotNull { it.groupValues[1].toLongOrNull() }
                    .maxOrNull()
            }
            ?.takeIf { it > 0 }
}
