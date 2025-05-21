/*
 * Copyright (C) 2024-2025 crDroid Android Project
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
 *
 */
package com.android.systemui.keyguard.ui.view.layout.sections

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.UserHandle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.android.systemui.keyguard.MigrateClocksToBlueprint
import com.android.systemui.keyguard.shared.model.KeyguardSection
import com.android.systemui.notifications.ui.PeekDisplayView
import com.android.systemui.res.R
import javax.inject.Inject

class KeyguardPeekDisplaySection
@Inject
constructor(
    private val context: Context,
) : KeyguardSection() {
    private var peekDisplayView: PeekDisplayView? = null
    private var peekDisplayEnabled = false
    private var peekDisplayLocation = 1
    private var contentObserver: ContentObserver? = null

    private fun registerContentObserver(constraintLayout: ConstraintLayout) {
        val handler = Handler(context.mainLooper)
        contentObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                updatePeekDisplayState(constraintLayout)
            }
        }
        val contentResolver: ContentResolver = context.contentResolver
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor("peek_display_notifications"),
            false,
            contentObserver!!
        )
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor("peek_display_location"),
            false,
            contentObserver!!
        )
    }
    
    private fun unregisterContentObserver() {
        contentObserver?.let {
            context.contentResolver.unregisterContentObserver(it)
            contentObserver = null
        }
    }
    
    private fun updatePeekDisplayState(constraintLayout: ConstraintLayout) {
        peekDisplayEnabled = Settings.Secure.getIntForUser(
            context.contentResolver,
            "peek_display_notifications", 0, UserHandle.USER_CURRENT
        ) == 1
        
        peekDisplayLocation = Settings.Secure.getIntForUser(
            context.contentResolver,
            "peek_display_location", 1, UserHandle.USER_CURRENT
        )
        
        // Create or update views based on latest settings
        addOrUpdatePeekDisplayViews(constraintLayout)
    }
    
    private fun addOrUpdatePeekDisplayViews(constraintLayout: ConstraintLayout) {
        if (!peekDisplayEnabled) {
            // Hide both peek displays if disabled
            constraintLayout.findViewById<PeekDisplayView?>(R.id.peek_display_top)?.visibility = View.GONE
            constraintLayout.findViewById<PeekDisplayView?>(R.id.peek_display_bottom)?.visibility = View.GONE
            return
        }
        
        // Show the appropriate peek display based on location setting
        constraintLayout.findViewById<PeekDisplayView?>(R.id.peek_display_top)?.let { view ->
            view.visibility = if (peekDisplayLocation == 0) View.VISIBLE else View.GONE
            if (peekDisplayLocation == 0) {
                peekDisplayView = view
                view.updatePeekDisplayState()
            }
        }
        
        constraintLayout.findViewById<PeekDisplayView?>(R.id.peek_display_bottom)?.let { view ->
            view.visibility = if (peekDisplayLocation == 1) View.VISIBLE else View.GONE
            if (peekDisplayLocation == 1) {
                peekDisplayView = view
                view.updatePeekDisplayState()
            }
        }
    }

    override fun addViews(constraintLayout: ConstraintLayout) {
        if (!MigrateClocksToBlueprint.isEnabled) return

        // Get current settings
        peekDisplayEnabled = Settings.Secure.getIntForUser(
            context.contentResolver,
            "peek_display_notifications", 0, UserHandle.USER_CURRENT
        ) == 1
        
        peekDisplayLocation = Settings.Secure.getIntForUser(
            context.contentResolver,
            "peek_display_location", 1, UserHandle.USER_CURRENT
        )

        // Top peek display view
        constraintLayout.findViewById<PeekDisplayView?>(R.id.peek_display_top)?.let { existingView ->
            (existingView.parent as? ViewGroup)?.removeView(existingView)
            constraintLayout.addView(existingView)
            existingView.visibility = if (peekDisplayEnabled && peekDisplayLocation == 0) View.VISIBLE else View.GONE
            if (peekDisplayEnabled && peekDisplayLocation == 0) {
                peekDisplayView = existingView
            }
        }

        // Bottom peek display view
        constraintLayout.findViewById<PeekDisplayView?>(R.id.peek_display_bottom)?.let { existingView ->
            (existingView.parent as? ViewGroup)?.removeView(existingView)
            constraintLayout.addView(existingView)
            existingView.visibility = if (peekDisplayEnabled && peekDisplayLocation == 1) View.VISIBLE else View.GONE
            if (peekDisplayEnabled && peekDisplayLocation == 1) {
                peekDisplayView = existingView
            }
        }
        
        // Register content observer to handle settings changes
        registerContentObserver(constraintLayout)
    }

    override fun bindData(constraintLayout: ConstraintLayout) {
        // Update the peek display state to ensure it's correctly initialized
        peekDisplayView?.updatePeekDisplayState()
    }

    override fun applyConstraints(constraintSet: ConstraintSet) {
        if (!MigrateClocksToBlueprint.isEnabled) return

        // Apply constraints for peek_display_top and peek_display_bottom
        constraintSet.apply {
            // For top peek display
            connect(
                R.id.peek_display_top,
                ConstraintSet.TOP,
                ConstraintSet.PARENT_ID,
                ConstraintSet.TOP
            )
            connect(
                R.id.peek_display_top,
                ConstraintSet.START,
                ConstraintSet.PARENT_ID,
                ConstraintSet.START
            )
            connect(
                R.id.peek_display_top,
                ConstraintSet.END,
                ConstraintSet.PARENT_ID,
                ConstraintSet.END
            )
            constrainHeight(R.id.peek_display_top, ConstraintSet.WRAP_CONTENT)
            constrainWidth(R.id.peek_display_top, ConstraintSet.MATCH_CONSTRAINT)

            // For bottom peek display
            connect(
                R.id.peek_display_bottom,
                ConstraintSet.BOTTOM,
                ConstraintSet.PARENT_ID,
                ConstraintSet.BOTTOM
            )
            connect(
                R.id.peek_display_bottom,
                ConstraintSet.START,
                ConstraintSet.PARENT_ID,
                ConstraintSet.START
            )
            connect(
                R.id.peek_display_bottom,
                ConstraintSet.END,
                ConstraintSet.PARENT_ID,
                ConstraintSet.END
            )
            constrainHeight(R.id.peek_display_bottom, ConstraintSet.WRAP_CONTENT)
            constrainWidth(R.id.peek_display_bottom, ConstraintSet.MATCH_CONSTRAINT)
        }
    }

    override fun removeViews(constraintLayout: ConstraintLayout) {
        // Unregister content observer
        unregisterContentObserver()
        
        // Clean up the PeekDisplayView if needed
        constraintLayout.findViewById<PeekDisplayView?>(R.id.peek_display_top)?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
        }
        constraintLayout.findViewById<PeekDisplayView?>(R.id.peek_display_bottom)?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
        }
        peekDisplayView = null
    }
}
