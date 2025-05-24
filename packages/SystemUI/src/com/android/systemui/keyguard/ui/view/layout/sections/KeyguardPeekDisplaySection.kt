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
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.Barrier
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.android.systemui.keyguard.MigrateClocksToBlueprint
import com.android.systemui.keyguard.shared.model.KeyguardSection
import com.android.systemui.notifications.ui.PeekDisplayView
import com.android.systemui.notifications.ui.PeekDisplayHolderLinearLayout
import com.android.systemui.res.R
import javax.inject.Inject

class KeyguardPeekDisplaySection
@Inject
constructor(
    private val context: Context,
) : KeyguardSection() {
    
    companion object {
        private const val TAG = "KeyguardPeekDisplaySection"
    }
    
    private var peekDisplayHolderTop: PeekDisplayHolderLinearLayout? = null
    private var peekDisplayTopView: PeekDisplayView? = null
    private var peekDisplayEnabled = false
    private var peekDisplayLocation = 1
    private var contentObserver: ContentObserver? = null

    private fun registerContentObserver(constraintLayout: ConstraintLayout) {
        Log.d(TAG, "registerContentObserver called")
        val handler = Handler(context.mainLooper)
        contentObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                Log.d(TAG, "Settings changed, updating peek display state")
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
        Log.d(TAG, "unregisterContentObserver called")
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
        
        Log.d(TAG, "updatePeekDisplayState - enabled: $peekDisplayEnabled, location: $peekDisplayLocation")
        
        // Update visibility based on settings
        updatePeekDisplayVisibility()
    }
    
    private fun updatePeekDisplayVisibility() {
        Log.d(TAG, "updatePeekDisplayVisibility - enabled: $peekDisplayEnabled, location: $peekDisplayLocation")
        
        if (!peekDisplayEnabled) {
            Log.d(TAG, "Peek display disabled, hiding all views")
            peekDisplayHolderTop?.visibility = View.GONE
            return
        }
        
        // Show the peek display only at the top for location setting 0
        val topVisible = peekDisplayLocation == 0
        
        Log.d(TAG, "Setting visibility - top: $topVisible")
        
        peekDisplayHolderTop?.visibility = if (topVisible) View.VISIBLE else View.GONE
        
        // Update the active view state
        if (topVisible) {
            Log.d(TAG, "Updating top view state")
            peekDisplayTopView?.updatePeekDisplayState()
        }
    }

    override fun addViews(constraintLayout: ConstraintLayout) {
        Log.d(TAG, "addViews called - MigrateClocksToBlueprint.isEnabled: ${MigrateClocksToBlueprint.isEnabled}")
        
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

        Log.d(TAG, "Initial settings - enabled: $peekDisplayEnabled, location: $peekDisplayLocation")

        try {
            // Remove existing view with the same ID if it exists
            constraintLayout.findViewById<View?>(R.id.peek_display_area_top)?.let { existingView ->
                (existingView.parent as? ViewGroup)?.removeView(existingView)
            }
            
            // Inflate the peek display layout
            peekDisplayHolderTop = LayoutInflater.from(context).inflate(
                R.layout.keyguard_peek_display,
                constraintLayout,
                false
            ) as PeekDisplayHolderLinearLayout
            
            peekDisplayHolderTop?.apply {
                id = R.id.peek_display_area_top
                layoutParams = ConstraintLayout.LayoutParams(
                    ConstraintLayout.LayoutParams.MATCH_PARENT,
                    ConstraintLayout.LayoutParams.WRAP_CONTENT
                )
            }
            
            // Get the PeekDisplayView from the inflated layout
            peekDisplayTopView = peekDisplayHolderTop?.findViewById<PeekDisplayView>(R.id.peek_display_top)
            
            if (peekDisplayHolderTop == null || peekDisplayTopView == null) {
                Log.w(TAG, "Could not create peek display views")
                return
            }
            
            constraintLayout.addView(peekDisplayHolderTop)
            Log.d(TAG, "Added peek display views to constraint layout")
            
            // Set initial visibility
            updatePeekDisplayVisibility()
            
            // Register content observer to handle settings changes
            registerContentObserver(constraintLayout)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in addViews", e)
        }
    }

    override fun bindData(constraintLayout: ConstraintLayout) {
        Log.d(TAG, "bindData called")
        try {
            // Update the peek display state to ensure it's correctly initialized
            if (peekDisplayLocation == 0 && peekDisplayEnabled) {
                peekDisplayTopView?.updatePeekDisplayState()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in bindData", e)
        }
    }

    override fun applyConstraints(constraintSet: ConstraintSet) {
        Log.d(TAG, "applyConstraints called")
        
        if (!MigrateClocksToBlueprint.isEnabled) return

        try {
            constraintSet.apply {
                // Position peek display within the keyguard_status_area
                connect(
                    R.id.peek_display_area_top,
                    ConstraintSet.START,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.START
                )
                connect(
                    R.id.peek_display_area_top,
                    ConstraintSet.END,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.END
                )
                
                // Position below widgets if available, otherwise below other status content
                if (constraintSet.getConstraint(R.id.keyguard_widgets) != null) {
                    connect(
                        R.id.peek_display_area_top,
                        ConstraintSet.TOP,
                        R.id.keyguard_widgets,
                        ConstraintSet.BOTTOM,
                        8
                    )
                } else if (constraintSet.getConstraint(R.id.keyguard_info_widgets) != null) {
                    connect(
                        R.id.peek_display_area_top,
                        ConstraintSet.TOP,
                        R.id.keyguard_info_widgets,
                        ConstraintSet.BOTTOM,
                        8
                    )
                } else if (constraintSet.getConstraint(R.id.clock_ls) != null) {
                    connect(
                        R.id.peek_display_area_top,
                        ConstraintSet.TOP,
                        R.id.clock_ls,
                        ConstraintSet.BOTTOM,
                        8
                    )
                } else if (constraintSet.getConstraint(R.id.keyguard_weather) != null) {
                    connect(
                        R.id.peek_display_area_top,
                        ConstraintSet.TOP,
                        R.id.keyguard_weather,
                        ConstraintSet.BOTTOM,
                        8
                    )
                } else if (constraintSet.getConstraint(R.id.keyguard_slice_view) != null) {
                    connect(
                        R.id.peek_display_area_top,
                        ConstraintSet.TOP,
                        R.id.keyguard_slice_view,
                        ConstraintSet.BOTTOM,
                        8
                    )
                } else {
                    // Last resort: position below the small clock
                    connect(
                        R.id.peek_display_area_top,
                        ConstraintSet.TOP,
                        R.id.lockscreen_clock_view,
                        ConstraintSet.BOTTOM,
                        8
                    )
                }
                
                // Set dimensions
                constrainHeight(R.id.peek_display_area_top, ConstraintSet.WRAP_CONTENT)
                constrainWidth(R.id.peek_display_area_top, ConstraintSet.MATCH_CONSTRAINT)
                
                // Set appropriate margins matching the XML structure
                setMargin(R.id.peek_display_area_top, ConstraintSet.START, 0)
                setMargin(R.id.peek_display_area_top, ConstraintSet.END, 0)
                
                // Ensure proper layering within the status area
                setElevation(R.id.peek_display_area_top, 3f)
                
                // Update the barrier to include peek display for proper notification positioning
                // This ensures notifications appear below all status area content
                createBarrier(
                    R.id.smart_space_barrier_bottom,
                    Barrier.BOTTOM,
                    0,
                    *intArrayOf(
                        R.id.keyguard_slice_view,
                        R.id.keyguard_weather,
                        R.id.clock_ls,
                        R.id.keyguard_info_widgets,
                        R.id.keyguard_widgets,
                        R.id.peek_display_area_top
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
            
            Log.d(TAG, "Constraints applied successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error in applyConstraints", e)
        }
    }

    override fun removeViews(constraintLayout: ConstraintLayout) {
        Log.d(TAG, "removeViews called")
        
        // Unregister content observer
        unregisterContentObserver()
        
        try {
            // Remove the holder view from the layout
            peekDisplayHolderTop?.let { view ->
                (view.parent as? ViewGroup)?.removeView(view)
                Log.d(TAG, "Removed peek display holder view")
            }
            
            // Clear references
            peekDisplayHolderTop = null
            peekDisplayTopView = null
        } catch (e: Exception) {
            Log.e(TAG, "Error in removeViews", e)
        }
    }
}
