/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.android.systemui.shared.clocks

import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Rect
import android.icu.text.NumberFormat
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.VisibleForTesting
import com.android.systemui.customization.R
import com.android.systemui.log.core.MessageBuffer
import com.android.systemui.plugins.clocks.AlarmData
import com.android.systemui.plugins.clocks.ClockAnimations
import com.android.systemui.plugins.clocks.ClockConfig
import com.android.systemui.plugins.clocks.ClockController
import com.android.systemui.plugins.clocks.ClockEvents
import com.android.systemui.plugins.clocks.ClockFaceConfig
import com.android.systemui.plugins.clocks.ClockFaceController
import com.android.systemui.plugins.clocks.ClockFaceEvents
import com.android.systemui.plugins.clocks.ClockMessageBuffers
import com.android.systemui.plugins.clocks.ClockReactiveSetting
import com.android.systemui.plugins.clocks.ClockSettings
import com.android.systemui.plugins.clocks.CustomClockFaceLayout
import com.android.systemui.plugins.clocks.WeatherData
import com.android.systemui.plugins.clocks.ZenData
import java.io.PrintWriter
import java.util.Locale
import java.util.TimeZone

private val TAG = CustomClockController::class.simpleName

class CustomClockController(
    private val ctx: Context,
    private val layoutInflater: LayoutInflater,
    private val resources: Resources,
    private val settings: ClockSettings?,
    private val hasStepClockAnimation: Boolean = false,
    messageBuffers: ClockMessageBuffers? = null,
) : ClockController {
    override val smallClock: CustomClockFaceController
    override val largeClock: CustomLargeClockFaceController
    private val clocks: List<View>
    protected var onSecondaryDisplay: Boolean = false

    override val events: CustomClockEvents
    override val config: ClockConfig by lazy {
        ClockConfig(
            CUSTOM_CLOCK_ID,
            CUSTOM_CLOCK_NAME,
            resources.getString(R.string.clock_default_description),
        )
    }

    init {
        val parent = FrameLayout(ctx)
        val isAutomatedDepthClock = Settings.Secure.getInt(ctx.contentResolver, Settings.Secure.LOCKSCREEN_DEPTH_CLOCK_AUTO, 0) != 0
        val currentAutoDepthClock = Settings.Secure.getInt(ctx.contentResolver, Settings.Secure.LOCKSCREEN_DEPTH_CLOCK_AUTO_STYLE, 0)

        smallClock =
            CustomClockFaceController(
                layoutInflater.inflate(R.layout.elixir_default_small, parent, false)
                    as ClockLayout,
                settings?.seedColor,
                messageBuffers?.smallClockMessageBuffer,
            )
        largeClock = if (isAutomatedDepthClock && currentAutoDepthClock == 0) CustomLargeClockFaceController(
                layoutInflater.inflate(R.layout.elixir_default_depth_large, parent, false)
                    as ClockLayout,
                settings?.seedColor,
                messageBuffers?.largeClockMessageBuffer,
            ) else if (isAutomatedDepthClock && currentAutoDepthClock == 1) CustomLargeClockFaceController(
                layoutInflater.inflate(R.layout.elixir_default_depth_large_side, parent, false)
                    as ClockLayout,
                settings?.seedColor,
                messageBuffers?.largeClockMessageBuffer,
            ) else if (isAutomatedDepthClock && currentAutoDepthClock == 2) CustomLargeClockFaceController(
                layoutInflater.inflate(R.layout.elixir_default_depth_large_extra, parent, false)
                    as ClockLayout,
                settings?.seedColor,
                messageBuffers?.largeClockMessageBuffer,
            ) else if (isAutomatedDepthClock) CustomLargeClockFaceController(
                layoutInflater.inflate(R.layout.elixir_default_depth_large, parent, false)
                    as ClockLayout,
                settings?.seedColor,
                messageBuffers?.largeClockMessageBuffer,
            ) else CustomLargeClockFaceController(
                layoutInflater.inflate(R.layout.elixir_default_large, parent, false)
                    as ClockLayout,
                settings?.seedColor,
                messageBuffers?.largeClockMessageBuffer,
            )
        clocks = listOf(smallClock.view, largeClock.view)

        events = CustomClockEvents()
        events.onLocaleChanged(Locale.getDefault())
    }

    override fun initialize(resources: Resources, dozeFraction: Float, foldFraction: Float) {
        largeClock.recomputePadding(null)
        largeClock.animations = LargeClockAnimations(largeClock.view, dozeFraction, foldFraction)
        smallClock.animations = CustomClockAnimations(smallClock.view, dozeFraction, foldFraction)
        events.onColorPaletteChanged(resources)
        events.onTimeZoneChanged(TimeZone.getDefault())
        smallClock.events.onTimeTick()
        largeClock.events.onTimeTick()
    }

    open inner class CustomClockFaceController(
        override val view: View,
        var seedColor: Int?,
        messageBuffer: MessageBuffer?,
    ) : ClockFaceController {

        override val config = ClockFaceConfig()
        override val layout =
            CustomClockFaceLayout(view).apply {
                views[0].id =
                    resources.getIdentifier("lockscreen_clock_view", "id", ctx.packageName)
            }

        override var animations: CustomClockAnimations = CustomClockAnimations(view, 0f, 0f)
            internal set

        init {
        }

        override val events =
            object : ClockFaceEvents {
                override fun onTimeTick() {
                }

                override fun onRegionDarknessChanged(isRegionDark: Boolean) {
                }

                override fun onTargetRegionChanged(targetRegion: Rect?) {
                    recomputePadding(targetRegion)
                }

                override fun onFontSettingChanged(fontSizePx: Float) {
                    recomputePadding(null)
                }

                override fun onSecondaryDisplayChanged(onSecondaryDisplay: Boolean) {
                    this@CustomClockController.onSecondaryDisplay = onSecondaryDisplay
                    recomputePadding(null)
                }
            }
        open fun recomputePadding(targetRegion: Rect?) {}
    }

    inner class CustomLargeClockFaceController(
        view: View,
        seedColor: Int?,
        messageBuffer: MessageBuffer?,
    ) : CustomClockFaceController(view, seedColor, messageBuffer) {
        override val layout =
            CustomClockFaceLayout(view).apply {
                views[0].id =
                    resources.getIdentifier("lockscreen_clock_view_large", "id", ctx.packageName)
            }
        override val config =
            ClockFaceConfig(hasCustomPositionUpdatedAnimation = hasStepClockAnimation)

        init {
            animations = LargeClockAnimations(view, 0f, 0f)
        }

        override fun recomputePadding(targetRegion: Rect?) {
            // We center the view within the targetRegion instead of within the parent
            // view by computing the difference and adding that to the padding.
            val lp = view.getLayoutParams() as FrameLayout.LayoutParams
            lp.topMargin =
                if (onSecondaryDisplay) {
                    // On the secondary display we don't want any additional top/bottom margin.
                    0
                } else {
                    val parent = view.parent
                    val yDiff =
                        if (targetRegion != null && parent is View && parent.isLaidOut())
                            targetRegion.centerY() - parent.height / 2f
                        else 0f
                    (-0.5f * view.bottom + yDiff).toInt()
                }
            view.setLayoutParams(lp)
        }
    }

    inner class CustomClockEvents : ClockEvents {
        override var isReactiveTouchInteractionEnabled: Boolean = false

        override fun onTimeFormatChanged(is24Hr: Boolean) =
            clocks.forEach {  }

        override fun onTimeZoneChanged(timeZone: TimeZone) =
            clocks.forEach {  }

        override fun onColorPaletteChanged(resources: Resources) {

        }

        override fun onSeedColorChanged(seedColor: Int?) {
            largeClock.seedColor = seedColor
            smallClock.seedColor = seedColor
        }

        override fun onLocaleChanged(locale: Locale) {
        }

        override fun onWeatherDataChanged(data: WeatherData) {}

        override fun onAlarmDataChanged(data: AlarmData) {}

        override fun onZenDataChanged(data: ZenData) {}

        override fun onReactiveAxesChanged(axes: List<ClockReactiveSetting>) {}
    }

    open inner class CustomClockAnimations(
        val view: View,
        dozeFraction: Float,
        foldFraction: Float,
    ) : ClockAnimations {
        internal val dozeState = AnimationState(dozeFraction)
        private val foldState = AnimationState(foldFraction)

        init {
        }

        override fun enter() {
        }

        override fun charge() {
        }

        override fun fold(fraction: Float) {
            val (hasChanged, hasJumped) = foldState.update(fraction)
            if (hasChanged) {
            }
        }

        override fun doze(fraction: Float) {
            val (hasChanged, hasJumped) = dozeState.update(fraction)
            if (hasChanged) {
            }
        }

        override fun onPickerCarouselSwiping(swipingFraction: Float) {
            // TODO(b/278936436): refactor this part when we change recomputePadding
            // when on the side, swipingFraction = 0, translationY should offset
            // the top margin change in recomputePadding to make clock be centered
            view.translationY = 0.5f * view.bottom * (1 - swipingFraction)
        }

        override fun onPositionUpdated(fromLeft: Int, direction: Int, fraction: Float) {}

        override fun onPositionUpdated(distance: Float, fraction: Float) {}
    }

    inner class LargeClockAnimations(
        view: View,
        dozeFraction: Float,
        foldFraction: Float,
    ) : CustomClockAnimations(view, dozeFraction, foldFraction) {
        override fun onPositionUpdated(fromLeft: Int, direction: Int, fraction: Float) {
        }

        override fun onPositionUpdated(distance: Float, fraction: Float) {
        }
    }

    class AnimationState(var fraction: Float) {
        var isActive: Boolean = fraction > 0.5f
        fun update(newFraction: Float): Pair<Boolean, Boolean> {
            if (newFraction == fraction) {
                return Pair(isActive, false)
            }
            val wasActive = isActive
            val hasJumped =
                (fraction == 0f && newFraction == 1f) || (fraction == 1f && newFraction == 0f)
            isActive = newFraction > fraction
            fraction = newFraction
            return Pair(wasActive != isActive, hasJumped)
        }
    }

    override fun dump(pw: PrintWriter) {
        pw.print("smallClock=")

        pw.print("largeClock=")
    }

    companion object {
        @VisibleForTesting const val DOZE_COLOR = Color.WHITE
        private const val FORMAT_NUMBER = 1234567890
    }
}
