/*
 * Copyright (C) 2025 the RisingOS Revived Android Project
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
package com.android.systemui.keyguard.ui.view.layout.sections

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.os.UserHandle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.Barrier
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.android.systemui.keyguard.MigrateClocksToBlueprint
import com.android.systemui.keyguard.shared.model.KeyguardSection
import com.android.systemui.res.R
import javax.inject.Inject

class InfoWidgetsSection
@Inject
constructor(
    private val context: Context,
) : KeyguardSection() {
    
    private var infoWidgetsView: View? = null
    private var isScreenOn = false // Default to screen off (AOD)
    private var screenStateReceiver: BroadcastReceiver? = null
    private var constraintLayoutRef: ConstraintLayout? = null

    private fun isPeekDisplayEnabled(): Boolean {
        return Settings.Secure.getIntForUser(
            context.contentResolver,
            "peek_display_notifications", 0, UserHandle.USER_CURRENT
        ) == 1
    }

    private fun isScreenInteractive(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isInteractive
    }

    private fun registerScreenStateReceiver() {
        screenStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        isScreenOn = true
                        updateConstraintsForScreenState()
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        isScreenOn = false
                        updateConstraintsForScreenState()
                    }
                }
            }
        }
        
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        
        context.registerReceiver(screenStateReceiver, filter)
    }
    
    private fun unregisterScreenStateReceiver() {
        screenStateReceiver?.let {
            try {
                context.unregisterReceiver(it)
                screenStateReceiver = null
            } catch (e: Exception) {
                // Ignore if already unregistered
            }
        }
    }

    private fun updateConstraintsForScreenState() {
        constraintLayoutRef?.let { layout ->
            val constraintSet = ConstraintSet()
            constraintSet.clone(layout)
            applyConstraints(constraintSet)
            constraintSet.applyTo(layout)
        }
    }
    
    override fun addViews(constraintLayout: ConstraintLayout) {
        if (!MigrateClocksToBlueprint.isEnabled) return
        
        // Store reference for screen state updates
        constraintLayoutRef = constraintLayout
        
        // Initialize screen state
        isScreenOn = isScreenInteractive()
        
        // Register screen state receiver
        registerScreenStateReceiver()
        
        // Remove existing view with the same ID if it exists
        constraintLayout.findViewById<View?>(R.id.keyguard_info_widgets)?.let { existingView ->
            (existingView.parent as? ViewGroup)?.removeView(existingView)
        }
        
        // Inflate the info widgets layout
        infoWidgetsView = LayoutInflater.from(context).inflate(
            R.layout.keyguard_info_widgets,
            constraintLayout,
            false
        ).apply {
            id = R.id.keyguard_info_widgets
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        constraintLayout.addView(infoWidgetsView)
    }
    
    override fun bindData(constraintLayout: ConstraintLayout) {
        // The ProgressImageView components handle their own data binding
        // through their onAttachedToWindow/onDetachedFromWindow lifecycle
    }
    
    override fun applyConstraints(constraintSet: ConstraintSet) {
        if (!MigrateClocksToBlueprint.isEnabled) return
        
        val peekDisplayEnabled = isPeekDisplayEnabled()
        
        // Determine margins based on screen state and peek display state
        val (topMarginConstraint, topMarginLayout) = when {
            peekDisplayEnabled -> Pair(108, 100) // Always 108,100 when peek display is on
            isScreenOn -> Pair(108, 100) // 108,100 when screen is on/wake
            else -> Pair(148, 140) // 148,140 when on AOD (screen off)
        }
        
        constraintSet.apply {
            // Position info widgets within the keyguard_status_area
            connect(
                R.id.keyguard_info_widgets,
                ConstraintSet.START,
                ConstraintSet.PARENT_ID,
                ConstraintSet.START
            )
            connect(
                R.id.keyguard_info_widgets,
                ConstraintSet.END,
                ConstraintSet.PARENT_ID,
                ConstraintSet.END
            )
            
            // Position below the weather view or clock_ls if available
            if (constraintSet.getConstraint(R.id.keyguard_weather) != null) {
                connect(
                    R.id.keyguard_info_widgets,
                    ConstraintSet.TOP,
                    R.id.keyguard_weather,
                    ConstraintSet.BOTTOM,
                    topMarginConstraint
                )
            } else if (constraintSet.getConstraint(R.id.clock_ls) != null) {
                connect(
                    R.id.keyguard_info_widgets,
                    ConstraintSet.TOP,
                    R.id.clock_ls,
                    ConstraintSet.BOTTOM,
                    topMarginConstraint
                )
            } else if (constraintSet.getConstraint(R.id.keyguard_slice_view) != null) {
                connect(
                    R.id.keyguard_info_widgets,
                    ConstraintSet.TOP,
                    R.id.keyguard_slice_view,
                    ConstraintSet.BOTTOM,
                    topMarginConstraint
                )
            } else {
                // Last resort: position below the small clock
                connect(
                    R.id.keyguard_info_widgets,
                    ConstraintSet.TOP,
                    R.id.lockscreen_clock_view,
                    ConstraintSet.BOTTOM,
                    topMarginConstraint
                )
            }
            
            // Set dimensions
            constrainHeight(R.id.keyguard_info_widgets, ConstraintSet.WRAP_CONTENT)
            constrainWidth(R.id.keyguard_info_widgets, ConstraintSet.MATCH_CONSTRAINT)
            
            // Set appropriate margins matching the XML structure
            setMargin(R.id.keyguard_info_widgets, ConstraintSet.START, 0)
            setMargin(R.id.keyguard_info_widgets, ConstraintSet.END, 0)
            setMargin(R.id.keyguard_info_widgets, ConstraintSet.TOP, topMarginLayout)
            
            // Ensure proper layering within the status area
            setElevation(R.id.keyguard_info_widgets, 1f)
            
            // Update the barrier to include info widgets for proper notification positioning
            // This ensures notifications appear below all status area content
            createBarrier(
                R.id.smart_space_barrier_bottom,
                Barrier.BOTTOM,
                0,
                *intArrayOf(
                    R.id.keyguard_slice_view,
                    R.id.keyguard_weather,
                    R.id.clock_ls,
                    R.id.keyguard_info_widgets
                )
            )
            
            // Ensure notification icons are positioned below the barrier
            if (constraintSet.getConstraint(R.id.left_aligned_notification_icon_container) != null) {
                connect(
                    R.id.left_aligned_notification_icon_container,
                    ConstraintSet.TOP,
                    R.id.smart_space_barrier_bottom,
                    ConstraintSet.BOTTOM,
                    context.resources.getDimensionPixelSize(R.dimen.below_clock_padding_start_icons)
                )
            }
        }
    }
    
    override fun removeViews(constraintLayout: ConstraintLayout) {
        // Unregister screen state receiver
        unregisterScreenStateReceiver()
        
        // Clear references
        constraintLayoutRef = null
        
        infoWidgetsView?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
        }
        infoWidgetsView = null
    }
}
