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
package io.chaldeaprjkt.gamespace.widget.tiles

import android.content.Context
import android.util.AttributeSet
import android.view.View
import io.chaldeaprjkt.gamespace.R
import io.chaldeaprjkt.gamespace.utils.di.ServiceViewEntryPoint
import io.chaldeaprjkt.gamespace.utils.entryPointOf

/**
 * Game bar quick toggle for the burn-in guard. Activation is asynchronous, but
 * the state refresh posted below runs after the guard's own main-thread queue
 * entry, so the summary always reflects the real state.
 */
class BurnInTile @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : BaseTile(context, attrs) {

    private val burnInGuard by lazy { context.entryPointOf<ServiceViewEntryPoint>().burnInGuard() }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        title?.text = context.getString(R.string.burnin_tile_title)
        icon?.setImageResource(R.drawable.ic_burnin)
        isGuardActive = burnInGuard.isActive
    }

    private var isGuardActive: Boolean = false
        set(value) {
            field = value
            summary?.text = context.getString(
                if (value) R.string.state_enabled else R.string.state_disabled
            )
            isSelected = value
        }

    override fun onClick(v: View?) {
        super.onClick(v)
        burnInGuard.toggle()
        post { isGuardActive = burnInGuard.isActive }
    }
}
