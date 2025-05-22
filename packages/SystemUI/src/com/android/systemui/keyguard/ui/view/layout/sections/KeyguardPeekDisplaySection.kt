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
    
    companion object {
        private const val TAG = "KeyguardPeekDisplaySection"
    }
    
    private var peekDisplayTopView: PeekDisplayView? = null
    private var peekDisplayBottomView: PeekDisplayView? = null
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
            peekDisplayTopView?.visibility = View.GONE
            peekDisplayBottomView?.visibility = View.GONE
            return
        }
        
        // Show the appropriate peek display based on location setting
        val topVisible = peekDisplayLocation == 0
        val bottomVisible = peekDisplayLocation == 1
        
        Log.d(TAG, "Setting visibility - top: $topVisible, bottom: $bottomVisible")
        
        peekDisplayTopView?.visibility = if (topVisible) View.VISIBLE else View.GONE
        peekDisplayBottomView?.visibility = if (bottomVisible) View.VISIBLE else View.GONE
        
        // Update the active view state
        if (topVisible) {
            Log.d(TAG, "Updating top view state")
            peekDisplayTopView?.updatePeekDisplayState()
        } else if (bottomVisible) {
            Log.d(TAG, "Updating bottom view state")
            peekDisplayBottomView?.updatePeekDisplayState()
        }
    }

    override fun addViews(constraintLayout: ConstraintLayout) {
        Log.d(TAG, "addViews called - MigrateClocksToBlueprint.isEnabled: ${MigrateClocksToBlueprint.isEnabled}")
        
        // Remove the blueprint check temporarily for debugging
        // if (!MigrateClocksToBlueprint.isEnabled) return

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
            // Create top peek display view
            if (peekDisplayTopView == null) {
                Log.d(TAG, "Creating top peek display view")
                peekDisplayTopView = PeekDisplayView(context).apply {
                    id = R.id.peek_display_top
                    layoutParams = ConstraintLayout.LayoutParams(
                        ConstraintLayout.LayoutParams.MATCH_CONSTRAINT,
                        ConstraintLayout.LayoutParams.WRAP_CONTENT
                    )
                    // Force visibility for debugging
                    visibility = View.VISIBLE
                    setBackgroundColor(0x44FF0000) // Semi-transparent red for debugging
                }
                constraintLayout.addView(peekDisplayTopView)
                Log.d(TAG, "Top peek display view added to constraint layout")
            }

            // Create bottom peek display view
            if (peekDisplayBottomView == null) {
                Log.d(TAG, "Creating bottom peek display view")
                peekDisplayBottomView = PeekDisplayView(context).apply {
                    id = R.id.peek_display_bottom
                    layoutParams = ConstraintLayout.LayoutParams(
                        ConstraintLayout.LayoutParams.MATCH_CONSTRAINT,
                        ConstraintLayout.LayoutParams.WRAP_CONTENT
                    )
                    // Force visibility for debugging
                    visibility = View.VISIBLE
                    setBackgroundColor(0x4400FF00) // Semi-transparent green for debugging
                }
                constraintLayout.addView(peekDisplayBottomView)
                Log.d(TAG, "Bottom peek display view added to constraint layout")
            }
            
            Log.d(TAG, "ConstraintLayout child count: ${constraintLayout.childCount}")
            
            // Set initial visibility (comment out for debugging)
            // updatePeekDisplayVisibility()
            
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
            if (peekDisplayLocation == 0) {
                peekDisplayTopView?.updatePeekDisplayState()
            } else {
                peekDisplayBottomView?.updatePeekDisplayState()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in bindData", e)
        }
    }

    override fun applyConstraints(constraintSet: ConstraintSet) {
        Log.d(TAG, "applyConstraints called")
        
        // Remove the blueprint check temporarily for debugging
        // if (!MigrateClocksToBlueprint.isEnabled) return

        try {
            // Apply constraints for peek_display_top
            constraintSet.apply {
                connect(
                    R.id.peek_display_top,
                    ConstraintSet.TOP,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.TOP,
                    16 // Add some margin for debugging
                )
                connect(
                    R.id.peek_display_top,
                    ConstraintSet.START,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.START,
                    16 // Add some margin for debugging
                )
                connect(
                    R.id.peek_display_top,
                    ConstraintSet.END,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.END,
                    16 // Add some margin for debugging
                )
                constrainHeight(R.id.peek_display_top, 100) // Fixed height for debugging
                constrainWidth(R.id.peek_display_top, ConstraintSet.MATCH_CONSTRAINT)

                // Apply constraints for peek_display_bottom
                connect(
                    R.id.peek_display_bottom,
                    ConstraintSet.BOTTOM,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.BOTTOM,
                    16 // Add some margin for debugging
                )
                connect(
                    R.id.peek_display_bottom,
                    ConstraintSet.START,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.START,
                    16 // Add some margin for debugging
                )
                connect(
                    R.id.peek_display_bottom,
                    ConstraintSet.END,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.END,
                    16 // Add some margin for debugging
                )
                constrainHeight(R.id.peek_display_bottom, 100) // Fixed height for debugging
                constrainWidth(R.id.peek_display_bottom, ConstraintSet.MATCH_CONSTRAINT)
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
            // Remove the views from the layout
            peekDisplayTopView?.let { view ->
                constraintLayout.removeView(view)
                Log.d(TAG, "Removed top peek display view")
            }
            peekDisplayBottomView?.let { view ->
                constraintLayout.removeView(view)
                Log.d(TAG, "Removed bottom peek display view")
            }
            
            // Clear references
            peekDisplayTopView = null
            peekDisplayBottomView = null
        } catch (e: Exception) {
            Log.e(TAG, "Error in removeViews", e)
        }
    }
}
