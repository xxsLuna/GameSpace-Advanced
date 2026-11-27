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

/**
 * AP (CPU/GPU) power saving: lowers the frequency ceilings of all cpufreq
 * policies and of any discoverable GPU frequency node while the guard is
 * active, then restores the exact original values on release.
 *
 * CPU:   /sys/devices/system/cpu/cpuN/cpufreq/scaling_max_freq
 * GPU:   /sys/class/devfreq/...gpu.../max_freq (Qualcomm),
 *        /sys/kernel/gpu/gpu_max_clock (MTK),
 *        /sys/class/kgsl/kgsl-3d0/max_gpuclk (older Qualcomm)
 *
 * Nodes that refuse a write (read-only or unsupported) are skipped silently.
 */
class ClockLimiter {
    private val saved = mutableMapOf<String, String>()

    // Enumeration goes through root: app domains are SELinux-restricted on
    // parts of sysfs (and the sysfs layout differs wildly between vendors),
    // so a root-side glob is the only portable probe.
    private fun glob(patterns: List<String>): List<String> =
        RootShell.exec("ls -1 ${patterns.joinToString(" ")} 2>/dev/null")
            ?.stdout?.lines()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() && it.startsWith("/") }
            .orEmpty()

    private fun cpuNodes(): List<String> =
        glob(
            listOf(
                "/sys/devices/system/cpu/cpu[0-9]*/cpufreq/scaling_max_freq",
                "/sys/devices/system/cpu/cpufreq/policy*/scaling_max_freq",
            )
        ).distinct()

    private fun gpuNodes(): List<String> =
        glob(
            listOf(
                "/sys/class/devfreq/*gpu*/max_freq",
                "/sys/class/devfreq/*kgsl*/max_gpuclk",
                "/sys/kernel/gpu/gpu_max_clock",
                "/sys/class/kgsl/kgsl-3d0/max_gpuclk",
            )
        ).distinct()

    /**
     * Caps every discovered clock node at [percent] of its current maximum.
     * Blocking: call on an IO thread.
     */
    fun limit(percent: Int) {
        val pct = percent.coerceIn(20, 100)
        (cpuNodes() + gpuNodes()).forEach { path ->
            val current = RootShell.readNode(path)?.trim()?.toLongOrNull() ?: return@forEach
            saved[path] = current.toString()
            val target = (current * pct / 100).coerceAtLeast(1)
            if (!RootShell.writeNode(path, target.toString())) {
                Log.w(TAG, "write failed for $path")
            }
        }
    }

    /** Restores every node touched by [limit] (blocking, IO thread). */
    fun restore() {
        saved.forEach { (path, value) -> RootShell.writeNode(path, value) }
        saved.clear()
    }

    companion object {
        private const val TAG = "GameSpaceClocks"
    }
}
