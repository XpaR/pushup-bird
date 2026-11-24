package com.pushupbird

import android.content.Context
import com.pushupbird.logic.HeadSignal
import kotlin.math.abs

/** Local-only preferences. No account, no network. */
class GameSettings(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Optional sensitivity. Stored by name so an older, much hotter range
     * value cannot come back after this calmer mapping.
     */
    var halfRange: Float
        get() = when (prefs.getString(KEY_TRAVEL, TRAVEL_MEDIUM)) {
            TRAVEL_SMALL -> HeadSignal.SMALL_RANGE
            TRAVEL_LARGE -> HeadSignal.LARGE_RANGE
            else -> HeadSignal.MEDIUM_RANGE
        }
        set(value) {
            val name = when {
                abs(value - HeadSignal.SMALL_RANGE) <= abs(value - HeadSignal.MEDIUM_RANGE) &&
                    abs(value - HeadSignal.SMALL_RANGE) <= abs(value - HeadSignal.LARGE_RANGE) -> TRAVEL_SMALL
                abs(value - HeadSignal.LARGE_RANGE) < abs(value - HeadSignal.MEDIUM_RANGE) -> TRAVEL_LARGE
                else -> TRAVEL_MEDIUM
            }
            prefs.edit().putString(KEY_TRAVEL, name).apply()
        }

    var best: Int
        get() = prefs.getInt(KEY_BEST, 0)
        set(value) {
            prefs.edit().putInt(KEY_BEST, value).apply()
        }

    var cameraAsked: Boolean
        get() = prefs.getBoolean(KEY_ASKED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ASKED, value).apply()
        }

    /** @return true when [score] is a new personal best. */
    fun recordScore(score: Int): Boolean {
        if (score > best) {
            best = score
            return true
        }
        return false
    }

    private companion object {
        const val PREFS = "pushup_bird"
        const val KEY_TRAVEL = "travel"
        const val KEY_BEST = "best"
        const val KEY_ASKED = "camera_asked"
        const val TRAVEL_SMALL = "small"
        const val TRAVEL_MEDIUM = "medium"
        const val TRAVEL_LARGE = "large"
    }
}
