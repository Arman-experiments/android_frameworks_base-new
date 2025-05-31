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
import android.content.Context
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
    
    override fun addViews(constraintLayout: ConstraintLayout) {
        if (!MigrateClocksToBlueprint.isEnabled) return
        
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
                    108
                )
            } else if (constraintSet.getConstraint(R.id.clock_ls) != null) {
                connect(
                    R.id.keyguard_info_widgets,
                    ConstraintSet.TOP,
                    R.id.clock_ls,
                    ConstraintSet.BOTTOM,
                    108
                )
            } else if (constraintSet.getConstraint(R.id.keyguard_slice_view) != null) {
                connect(
                    R.id.keyguard_info_widgets,
                    ConstraintSet.TOP,
                    R.id.keyguard_slice_view,
                    ConstraintSet.BOTTOM,
                    108
                )
            } else {
                // Last resort: position below the small clock
                connect(
                    R.id.keyguard_info_widgets,
                    ConstraintSet.TOP,
                    R.id.lockscreen_clock_view,
                    ConstraintSet.BOTTOM,
                    108
                )
            }
            
            // Set dimensions
            constrainHeight(R.id.keyguard_info_widgets, ConstraintSet.WRAP_CONTENT)
            constrainWidth(R.id.keyguard_info_widgets, ConstraintSet.MATCH_CONSTRAINT)
            
            // Set appropriate margins matching the XML structure
            setMargin(R.id.keyguard_info_widgets, ConstraintSet.START, 0)
            setMargin(R.id.keyguard_info_widgets, ConstraintSet.END, 0)
            setMargin(R.id.keyguard_info_widgets, ConstraintSet.TOP, 100)
            
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
        infoWidgetsView?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
        }
        infoWidgetsView = null
    }
}
