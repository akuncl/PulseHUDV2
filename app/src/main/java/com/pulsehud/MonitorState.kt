package com.pulsehud

import android.content.Context
import java.util.ArrayDeque

object MonitorState {
    private val lock = Any()
    private val eventTimes = ArrayDeque<Long>()
    @Volatile var totalClicks: Long = 0
        private set
    @Volatile var peakCps: Int = 0
        private set
    @Volatile var hudFps: Int = 0
        private set

    private var frameCount = 0
    private var frameWindowStart = System.nanoTime()

    fun recordClick(context: Context) {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            totalClicks++
            eventTimes.addLast(now)
            trim(now, 10_000L)
            HudPrefs.prefs(context).edit().putLong("total_clicks", totalClicks).apply()
        }
    }

    fun restore(context: Context) {
        totalClicks = HudPrefs.prefs(context).getLong("total_clicks", 0L)
    }

    fun currentCps(windowMs: Long): Int {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            trim(now, 10_000L)
            val cutoff = now - windowMs.coerceAtLeast(100L)
            val count = eventTimes.count { it >= cutoff }
            val cps = (count * 1000.0 / windowMs.coerceAtLeast(100L)).toInt()
            if (cps > peakCps) peakCps = cps
            return cps
        }
    }

    fun recordHudFrame() {
        synchronized(lock) {
            frameCount++
            val now = System.nanoTime()
            val elapsed = now - frameWindowStart
            if (elapsed >= 1_000_000_000L) {
                hudFps = (frameCount * 1_000_000_000.0 / elapsed).toInt()
                frameCount = 0
                frameWindowStart = now
            }
        }
    }

    fun resetSession(context: Context) {
        synchronized(lock) {
            totalClicks = 0
            peakCps = 0
            eventTimes.clear()
        }
        HudPrefs.prefs(context).edit().putLong("total_clicks", 0L).apply()
    }

    private fun trim(now: Long, retentionMs: Long) {
        val cutoff = now - retentionMs
        while (eventTimes.isNotEmpty() && eventTimes.peekFirst() < cutoff) {
            eventTimes.removeFirst()
        }
    }
}

object HudPrefs {
    fun prefs(context: Context) = context.getSharedPreferences("pulsehud", Context.MODE_PRIVATE)
    fun bool(context: Context, key: String, default: Boolean = true) =
        prefs(context).getBoolean(key, default)
    fun int(context: Context, key: String, default: Int) =
        prefs(context).getInt(key, default)
    fun str(context: Context, key: String, default: String) =
        prefs(context).getString(key, default) ?: default

    fun put(context: Context, key: String, value: Any) {
        val e = prefs(context).edit()
        when (value) {
            is Boolean -> e.putBoolean(key, value)
            is Int -> e.putInt(key, value)
            is Long -> e.putLong(key, value)
            is String -> e.putString(key, value)
        }
        e.apply()
    }
}
