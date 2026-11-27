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
package io.chaldeaprjkt.gamespace.utils

import android.app.GameManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioManager
import android.net.Uri
import android.service.notification.NotificationListenerService
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.window.TaskFpsCallback
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.Executor
import java.util.function.Consumer

/**
 * Bridge to framework internals that exist on the crDroid/AOSP bootclasspath
 * but are not part of the compile SDK.
 *
 * Two flavours are used on purpose:
 *  - direct static calls for hidden *classes* that our compileOnly stub jar
 *    declares (Settings.*ForUser, AudioSystem, UserHandle constants): dex
 *    linkage is never subject to the hidden-API reflection policy;
 *  - reflection for hidden *members of public classes* (Context.*AsUser,
 *    WindowManager fps callbacks, ActivityTaskManager, ScreenshotHelper,
 *    IStatusBarService, IDeviceIdleController) with a safe public-API or
 *    no-op fallback, so the app degrades gracefully instead of crashing on
 *    foreign or newer ROMs.
 */
object HiddenApi {
    private const val TAG = "GameSpaceHiddenApi"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val directExecutor = Executor { it.run() }

    private fun service(name: String): IBinder? = try {
        Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java)
            .invoke(null, name) as? IBinder
    } catch (t: Throwable) {
        Log.w(TAG, "ServiceManager.getService($name) failed", t)
        null
    }

    private fun asInterface(binder: IBinder, className: String): Any? = try {
        Class.forName("$className\$Stub")
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, binder)
    } catch (t: Throwable) {
        Log.w(TAG, "asInterface($className) failed", t)
        null
    }

    private fun invokeByArgCount(target: Any, name: String, args: Array<Any?>): Boolean = try {
        val m: Method = target.javaClass.methods.firstOrNull { cand ->
            cand.name == name && cand.parameterTypes.size == args.size
        } ?: return false
        m.invoke(target, *args)
        true
    } catch (t: Throwable) {
        Log.w(TAG, "$name failed", t)
        false
    }

    /* ---------------------------------------------------------------------
     * Focused root task (android.app.ActivityTaskManager @hide)
     * ------------------------------------------------------------------- */

    private fun focusedRootTaskInfo(): Any? = try {
        val atm = Class.forName("android.app.ActivityTaskManager")
            .getMethod("getService")
            .invoke(null) ?: return null
        atm.javaClass.getMethod("getFocusedRootTaskInfo").invoke(atm)
    } catch (t: Throwable) {
        Log.w(TAG, "focusedRootTaskInfo failed", t)
        null
    }

    fun focusedTaskId(): Int? = focusedRootTaskInfo()?.let { info ->
        try {
            info.javaClass.getField("taskId").getInt(info)
        } catch (t: Throwable) {
            Log.w(TAG, "taskId lookup failed", t)
            null
        }
    }

    fun focusedTopActivityPackage(): String? = focusedRootTaskInfo()?.let { info ->
        try {
            (info.javaClass.getField("topActivity").get(info) as? ComponentName)?.packageName
        } catch (t: Throwable) {
            Log.w(TAG, "topActivity lookup failed", t)
            null
        }
    }

    /* ---------------------------------------------------------------------
     * WindowManager fps callbacks (@hide, ACCESS_FPS_COUNTER is granted via
     * the privapp-permissions whitelist shipped with the Magisk module)
     * ------------------------------------------------------------------- */

    fun registerTaskFpsCallback(wm: WindowManager, taskId: Int, callback: TaskFpsCallback) {
        invokeByArgCount(wm, "registerTaskFpsCallback", arrayOf(taskId, directExecutor, callback))
    }

    fun unregisterTaskFpsCallback(wm: WindowManager, callback: TaskFpsCallback) {
        invokeByArgCount(wm, "unregisterTaskFpsCallback", arrayOf(callback))
    }

    /* ---------------------------------------------------------------------
     * Context.*AsUser: the app always runs as its current user, so the
     * public no-user variants are equivalent; reflection is tried first to
     * keep upstream semantics, falling back to public APIs.
     * ------------------------------------------------------------------- */

    private fun currentUserHandle(): Any? = try {
        Class.forName("android.os.UserHandle").getField("CURRENT").get(null)
    } catch (t: Throwable) {
        null
    }

    fun bindService(
        context: Context,
        intent: Intent,
        connection: ServiceConnection,
        flags: Int
    ): Boolean {
        try {
            val uh = currentUserHandle()
            if (uh != null) {
                val m = context.javaClass.getMethod(
                    "bindServiceAsUser", Intent::class.java, ServiceConnection::class.java,
                    Int::class.javaPrimitiveType, uh.javaClass
                )
                return m.invoke(context, intent, connection, flags, uh) as Boolean
            }
        } catch (t: Throwable) {
            Log.w(TAG, "bindServiceAsUser failed, falling back", t)
        }
        return context.bindService(intent, connection, flags)
    }

    fun startService(context: Context, intent: Intent) {
        try {
            val uh = currentUserHandle()
            if (uh != null) {
                val m = context.javaClass.getMethod(
                    "startServiceAsUser", Intent::class.java, uh.javaClass
                )
                m.invoke(context, intent, uh)
                return
            }
        } catch (t: Throwable) {
            Log.w(TAG, "startServiceAsUser failed, falling back", t)
        }
        context.startService(intent)
    }

    fun stopService(context: Context, intent: Intent) {
        try {
            val uh = currentUserHandle()
            if (uh != null) {
                val m = context.javaClass.getMethod(
                    "stopServiceAsUser", Intent::class.java, uh.javaClass
                )
                m.invoke(context, intent, uh)
                return
            }
        } catch (t: Throwable) {
            Log.w(TAG, "stopServiceAsUser failed, falling back", t)
        }
        context.stopService(intent)
    }

    /* ---------------------------------------------------------------------
     * DeviceIdleController power-save whitelist (battery saver suppression)
     * ------------------------------------------------------------------- */

    fun deviceIdleWhitelist(pkg: String, enable: Boolean) {
        val binder = service("devic_idle_controller") ?: return
        val svc = asInterface(binder, "android.os.IDeviceIdleController") ?: return
        try {
            val listed = svc.javaClass
                .getMethod("isPowerSaveWhitelistApp", String::class.java)
                .invoke(svc, pkg) as? Boolean ?: false
            when {
                enable && !listed -> svc.javaClass
                    .getMethod("addPowerSaveWhitelistApp", String::class.java)
                    .invoke(svc, pkg)
                !enable && listed -> svc.javaClass
                    .getMethod("removePowerSaveWhitelistApp", String::class.java)
                    .invoke(svc, pkg)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "deviceIdle whitelist toggle failed", t)
        }
    }

    /* ---------------------------------------------------------------------
     * IStatusBarService gesture blocking (crDroid framework addition)
     * ------------------------------------------------------------------- */

    /** @return true when the ROM honoured the request; false when unsupported. */
    fun setGesturalNavBlocked(blocked: Boolean): Boolean {
        val binder = service(Context.STATUS_BAR_SERVICE) ?: return false
        val svc = asInterface(binder, "com.android.internal.statusbar.IStatusBarService")
            ?: return false
        return invokeByArgCount(svc, "setBlockedGesturalNavigation", arrayOf(blocked))
    }

    /* ---------------------------------------------------------------------
     * ScreenshotHelper (com.android.internal.util, crDroid signature variant)
     * ------------------------------------------------------------------- */

    fun takeScreenshot(context: Context, onComplete: ((Uri?) -> Unit)? = null) {
        try {
            val helper = Class.forName("com.android.internal.util.ScreenshotHelper")
                .getConstructor(Context::class.java)
                .newInstance(context)
            val m = helper.javaClass.methods.firstOrNull { cand ->
                cand.name == "takeScreenshot" &&
                    cand.parameterTypes.any { it == Handler::class.java }
            } ?: return
            val args = m.parameterTypes.map { p ->
                when {
                    p == Int::class.javaPrimitiveType -> 0
                    p == Handler::class.java -> mainHandler
                    p.isEnum && p.simpleName == "ScreenshotSource" ->
                        p.enumConstants.firstOrNull { e ->
                            (e as Enum<*>).name == "SCREENSHOT_GLOBAL_ACTIONS"
                        } ?: p.enumConstants.first()
                    p == Consumer::class.java -> Consumer<Any> { onComplete?.invoke(null) }
                    p.isInterface -> Proxy.newProxyInstance(
                        p.classLoader, arrayOf(p),
                        InvocationHandler { _, _, _ ->
                            onComplete?.invoke(null)
                            false
                        }
                    )
                    else -> null
                }
            }.toTypedArray()
            m.invoke(helper, *args)
        } catch (t: Throwable) {
            Log.w(TAG, "takeScreenshot failed", t)
        }
    }

    /* ---------------------------------------------------------------------
     * AudioManager ringer mode (@hide internal variant, bypasses zen rules)
     * ------------------------------------------------------------------- */

    fun getRingerModeInternal(am: AudioManager): Int = try {
        am.javaClass.getMethod("getRingerModeInternal").invoke(am) as Int
    } catch (t: Throwable) {
        Log.w(TAG, "getRingerModeInternal failed, falling back", t)
        am.ringerMode
    }

    fun setRingerModeInternal(am: AudioManager, mode: Int) {
        try {
            am.javaClass.getMethod("setRingerModeInternal", Int::class.javaPrimitiveType)
                .invoke(am, mode)
        } catch (t: Throwable) {
            Log.w(TAG, "setRingerModeInternal failed, falling back", t)
            am.ringerMode = mode
        }
    }

    /* ---------------------------------------------------------------------
     * GameManager per-app mode (@hide, MANAGE_GAME_MODE is whitelisted)
     * ------------------------------------------------------------------- */

    fun gameManagerAvailableModes(gm: GameManager, pkg: String): IntArray? = try {
        gm.javaClass.getMethod("getAvailableGameModes", String::class.java)
            .invoke(gm, pkg) as? IntArray
    } catch (t: Throwable) {
        Log.w(TAG, "getAvailableGameModes failed", t)
        null
    }

    fun gameManagerSetMode(gm: GameManager, pkg: String, mode: Int) {
        try {
            gm.javaClass.getMethod(
                "setGameMode", String::class.java, Int::class.javaPrimitiveType
            ).invoke(gm, pkg, mode)
        } catch (t: Throwable) {
            Log.w(TAG, "setGameMode failed", t)
        }
    }

    /* ---------------------------------------------------------------------
     * NotificationListenerService system registration (@hide; works without
     * user binding but only for system callers)
     * ------------------------------------------------------------------- */

    fun registerAsSystemService(
        listener: NotificationListenerService,
        context: Context,
        component: ComponentName,
        userId: Int
    ): Boolean = try {
        val m = listener.javaClass.methods.firstOrNull {
            it.name == "registerAsSystemService" && it.parameterTypes.size == 3
        } ?: return false
        m.invoke(listener, context, component, userId)
        true
    } catch (t: Throwable) {
        Log.w(TAG, "registerAsSystemService failed", t)
        false
    }

    fun unregisterAsSystemService(listener: NotificationListenerService) {
        try {
            listener.javaClass.getMethod("unregisterAsSystemService").invoke(listener)
        } catch (t: Throwable) {
            Log.w(TAG, "unregisterAsSystemService failed", t)
        }
    }
}
