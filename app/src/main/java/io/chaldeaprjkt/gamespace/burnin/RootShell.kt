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
import java.io.BufferedReader
import java.util.concurrent.TimeUnit

/**
 * Tiny synchronous root-shell helper (Magisk / KernelSU).
 *
 * Every call is blocking; callers must run these on an IO dispatcher.
 * All failures are reported as null / false results, never exceptions, so a
 * denied root prompt can only ever disable the burn-in extras, not the game
 * session itself.
 */
object RootShell {
    private const val TAG = "GameSpaceRoot"
    private const val TIMEOUT_SECONDS = 5L

    data class Output(val exitCode: Int, val stdout: String)

    fun exec(command: String): Output? = try {
        val process = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true)
            .start()
        val out = process.inputStream.bufferedReader().use(BufferedReader::readText)
        val finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            Log.w(TAG, "timeout: $command")
            null
        } else {
            Output(process.exitValue(), out.trim())
        }
    } catch (t: Throwable) {
        Log.w(TAG, "su failed: $command (${t.javaClass.simpleName})")
        null
    }

    /** Writes [value] to a sysfs node through root (echo keeps SELinux in the root domain). */
    fun writeNode(node: String, value: String): Boolean {
        val result = exec("echo $value > $node") ?: return false
        return result.exitCode == 0
    }

    /** Reads a sysfs / `settings get` line through root; null on failure. */
    fun readNode(node: String): String? =
        exec("cat $node")?.takeIf { it.exitCode == 0 && it.stdout.isNotEmpty() }?.stdout

    fun settingsGet(namespace: String, key: String): String? {
        val value = exec("settings get $namespace $key")
            ?.takeIf { it.exitCode == 0 }?.stdout
            ?.trim() ?: return null
        return if (value == "null") null else value
    }

    fun settingsPut(namespace: String, key: String, value: String): Boolean =
        exec("settings put $namespace $key $value")?.exitCode == 0

    fun settingsDelete(namespace: String, key: String): Boolean =
        exec("settings delete $namespace $key")?.exitCode == 0

    fun isRootAvailable(): Boolean = exec("id -u")?.stdout == "0"
}
