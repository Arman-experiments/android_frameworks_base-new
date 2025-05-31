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
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
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

    companion object {
        private const val TAG = "InfoWidgetsSection"
        private const val PEEK_DISPLAY_LOCATION_TOP = 0
        private const val PEEK_DISPLAY_LOCATION_BOTTOM = 1
        
        // Top margin values
        private const val TOP_MARGIN_DEFAULT = 148
        private const val TOP_MARGIN_WITH_PEEK_DISPLAY = 108
        private const val TOP_MARGIN_CONSTRAINT_DEFAULT = 140
        private const val TOP_MARGIN_CONSTRAINT_WITH_PEEK_DISPLAY = 100
    }

    private var infoWidgetsView: View? = null
    private var constraintLayoutRef: ConstraintLayout? = null
    private var contentObserver: ContentObserver? = null
    private var screenStateReceiver: BroadcastReceiver? = null
    private val handler = Handler(context.mainLooper)
    
    // Peek display state tracking
    private var peekDisplayEnabled = false
    private var peekDisplayLocation = PEEK_DISPLAY_LOCATION_BOTTOM
    private var peekDisplayTriggered = false

    private fun registerContentObserver() {
        Log.d(TAG, "registerContentObserver called")
        val handler = Handler(context.mainLooper)
        contentObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                Log.d(TAG, "Peek display settings changed, updating info widgets layout")
                updatePeekDisplayState()
                constraintLayoutRef?.let { layout ->
                    updateInfoWidgetsConstraints(layout)
                }
            }
        }
        val contentResolver: ContentResolver = context.contentResolver
        
        // Register observers for peek display settings
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
        Log.d(TAG, "unregisterContentObserver called")
        contentObserver?.let {
            context.contentResolver.unregisterContentObserver(it)
            contentObserver = null
        }
    }

    private fun registerScreenStateReceiver() {
        Log.d(TAG, "registerScreenStateReceiver called")
        
        screenStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        Log.d(TAG, "Screen turned ON - checking peek display trigger state")
                        // When screen turns on, peek display might get triggered
                        if (peekDisplayEnabled) {
                            peekDisplayTriggered = true
                            Log.d(TAG, "Peek display will be triggered, adjusting info widgets margin")
                            constraintLayoutRef?.let { layout ->
                                updateInfoWidgetsConstraints(layout)
                            }
                        }
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        Log.d(TAG, "User unlocked device - resetting peek display trigger state")
                        // When user unlocks, reset the trigger state
                        peekDisplayTriggered = false
                        constraintLayoutRef?.let { layout ->
                            updateInfoWidgetsConstraints(layout)
                        }
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        Log.d(TAG, "Screen turned OFF - resetting peek display trigger state")
                        // Reset trigger state when screen goes off
                        peekDisplayTriggered = false
                    }
                }
            }
        }
        
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        
        context.registerReceiver(screenStateReceiver, filter)
        Log.d(TAG, "Screen state receiver registered")
    }
    
    private fun unregisterScreenStateReceiver() {
        Log.d(TAG, "unregisterScreenStateReceiver called")
        screenStateReceiver?.let {
            try {
                context.unregisterReceiver(it)
                screenStateReceiver = null
                Log.d(TAG, "Screen state receiver unregistered")
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering screen state receiver", e)
            }
        }
    }
    
    private fun updatePeekDisplayState() {
        peekDisplayEnabled = Settings.Secure.getIntForUser(
            context.contentResolver,
            "peek_display_notifications", 0, UserHandle.USER_CURRENT
        ) == 1
        
        peekDisplayLocation = Settings.Secure.getIntForUser(
            context.contentResolver,
            "peek_display_location", PEEK_DISPLAY_LOCATION_BOTTOM, UserHandle.USER_CURRENT
        )
        
        Log.d(TAG, "updatePeekDisplayState - enabled: $peekDisplayEnabled, location: $peekDisplayLocation")
    }
    
    private fun shouldUseReducedMargin(): Boolean {
        // Use reduced margin when:
        // 1. Peek display is enabled AND located at top AND triggered, OR
        // 2. Peek display is enabled AND located at top (always on)
        val shouldReduce = peekDisplayEnabled && peekDisplayLocation == PEEK_DISPLAY_LOCATION_TOP && 
                          (peekDisplayTriggered || peekDisplayEnabled)
        
        Log.d(TAG, "shouldUseReducedMargin: $shouldReduce (enabled: $peekDisplayEnabled, location: $peekDisplayLocation, triggered: $peekDisplayTriggered)")
        return shouldReduce
    }
    
    private fun updateInfoWidgetsConstraints(constraintLayout: ConstraintLayout) {
        Log.d(TAG, "updateInfoWidgetsConstraints called")
        
        val constraintSet = ConstraintSet()
        constraintSet.clone(constraintLayout)
        applyConstraints(constraintSet)
        constraintSet.applyTo(constraintLayout)
        
        // Force layout refresh
        constraintLayout.requestLayout()
        constraintLayout.invalidate()
        
        Log.d(TAG, "Info widgets constraints updated")
    }

    override fun addViews(constraintLayout: ConstraintLayout) {
        if (!MigrateClocksToBlueprint.isEnabled) return

        // Store reference to constraint layout
        constraintLayoutRef = constraintLayout

        // Get current peek display settings
        updatePeekDisplayState()

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
        
        // Register observers for peek display state changes
        registerContentObserver()
        registerScreenStateReceiver()
        
        Log.d(TAG, "Info widgets view added with peek display integration")
    }

    override fun bindData(constraintLayout: ConstraintLayout) {
        // The ProgressImageView components handle their own data binding
        // through their onAttachedToWindow/onDetachedFromWindow lifecycle
    }

    override fun applyConstraints(constraintSet: ConstraintSet) {
        if (!MigrateClocksToBlueprint.isEnabled) return

        // Determine margin values based on peek display state
        val useReducedMargin = shouldUseReducedMargin()
        val topMargin = if (useReducedMargin) TOP_MARGIN_WITH_PEEK_DISPLAY else TOP_MARGIN_DEFAULT
        val constraintTopMargin = if (useReducedMargin) TOP_MARGIN_CONSTRAINT_WITH_PEEK_DISPLAY else TOP_MARGIN_CONSTRAINT_DEFAULT
        
        Log.d(TAG, "applyConstraints - using reduced margin: $useReducedMargin, topMargin: $topMargin, constraintTopMargin: $constraintTopMargin")

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
                    topMargin
                )
            } else if (constraintSet.getConstraint(R.id.clock_ls) != null) {
                connect(
                    R.id.keyguard_info_widgets,
                    ConstraintSet.TOP,
                    R.id.clock_ls,
                    ConstraintSet.BOTTOM,
                    topMargin
                )
            } else if (constraintSet.getConstraint(R.id.keyguard_slice_view) != null) {
                connect(
                    R.id.keyguard_info_widgets,
                    ConstraintSet.TOP,
                    R.id.keyguard_slice_view,
                    ConstraintSet.BOTTOM,
                    topMargin
                )
            } else {
                // Last resort: position below the small clock
                connect(
                    R.id.keyguard_info_widgets,
                    ConstraintSet.TOP,
                    R.id.lockscreen_clock_view,
                    ConstraintSet.BOTTOM,
                    topMargin
                )
            }

            // Set dimensions
            constrainHeight(R.id.keyguard_info_widgets, ConstraintSet.WRAP_CONTENT)
            constrainWidth(R.id.keyguard_info_widgets, ConstraintSet.MATCH_CONSTRAINT)

            // Set appropriate margins matching the XML structure
            setMargin(R.id.keyguard_info_widgets, ConstraintSet.START, 0)
            setMargin(R.id.keyguard_info_widgets, ConstraintSet.END, 0)
            setMargin(R.id.keyguard_info_widgets, ConstraintSet.TOP, constraintTopMargin)

            // Ensure proper layering within the status area
            setElevation(R.id.keyguard_info_widgets, 1f)

            // Remove existing barrier first to avoid conflicts
            removeFromBarrier(R.id.smart_space_barrier_bottom)

            // Create a comprehensive list of views that should be above notifications
            val barrierViews = mutableListOf<Int>()

            // Add views that exist in the constraint set
            listOf(
                R.id.keyguard_slice_view,
                R.id.keyguard_weather,
                R.id.clock_ls,
                R.id.lockscreen_clock_view,
                R.id.keyguard_info_widgets
            ).forEach { viewId ->
                if (constraintSet.getConstraint(viewId) != null) {
                    barrierViews.add(viewId)
                }
            }

            // Only create barrier if we have views to reference
            if (barrierViews.isNotEmpty()) {
                createBarrier(
                    R.id.smart_space_barrier_bottom,
                    Barrier.BOTTOM,
                    0,
                    *barrierViews.toIntArray()
                )
            }

            // Position notifications below the barrier with proper spacing
            listOf(
                R.id.left_aligned_notification_icon_container,
                R.id.right_aligned_notification_icon_container,
                R.id.notification_stack_scroller
            ).forEach { notificationId ->
                if (constraintSet.getConstraint(notificationId) != null) {
                    // Clear any existing top constraints first
                    clear(notificationId, ConstraintSet.TOP)

                    if (barrierViews.isNotEmpty()) {
                        // Connect to barrier if it exists
                        connect(
                            notificationId,
                            ConstraintSet.TOP,
                            R.id.smart_space_barrier_bottom,
                            ConstraintSet.BOTTOM,
                            context.resources.getDimensionPixelSize(R.dimen.below_clock_padding_start_icons)
                        )
                    } else {
                        // Fallback: connect directly to info widgets
                        connect(
                            notificationId,
                            ConstraintSet.TOP,
                            R.id.keyguard_info_widgets,
                            ConstraintSet.BOTTOM,
                            context.resources.getDimensionPixelSize(R.dimen.below_clock_padding_start_icons)
                        )
                    }
                }
            }

            // Additional safety: ensure info widgets don't overlap with notification area
            // by setting a maximum height constraint if needed
            val maxHeight = context.resources.displayMetrics.heightPixels / 3
            constrainMaxHeight(R.id.keyguard_info_widgets, maxHeight)
        }
    }

    override fun removeViews(constraintLayout: ConstraintLayout) {
        Log.d(TAG, "removeViews called")
        
        // Unregister observers
        unregisterContentObserver()
        unregisterScreenStateReceiver()
        
        // Clear references
        constraintLayoutRef = null
        
        // Remove pending callbacks
        handler.removeCallbacksAndMessages(null)
        
        // Remove view
        infoWidgetsView?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
        }
        infoWidgetsView = null
    }
}
