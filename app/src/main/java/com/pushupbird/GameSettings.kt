package com.pushupbird

import android.content.Context
import com.pushupbird.logic.HeadSignal

/** Local-only preferences. No account, no network. */
class GameSettings(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var halfRange: Float
        get() = prefs.getFloat(KEY_RANGE, HeadSignal.MEDIUM_RANGE)
        set(value) {
            prefs.edit().putFloat(KEY_RANGE, value).apply()
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
        const val KEY_RANGE = "half_range"
        const val KEY_BEST = "best"
        const val KEY_ASKED = "camera_asked"
    }
}
