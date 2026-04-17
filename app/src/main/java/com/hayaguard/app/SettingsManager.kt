package com.hayaguard.app

import android.content.Context
import android.content.SharedPreferences

object SettingsManager {

    private const val PREFS_NAME = "hayaguard_settings"
    private const val KEY_TIME_LIMIT_MINUTES = "time_limit_minutes"
    private const val KEY_AUTO_CLOSE_ENABLED = "auto_close_enabled"
    private const val KEY_AUTO_CLOSE_HOUR = "auto_close_hour"
    private const val KEY_AUTO_CLOSE_MINUTE = "auto_close_minute"
    private const val KEY_OVERRIDE_UNTIL = "override_until"
    private const val KEY_SNOOZE_UNTIL = "snooze_until"
    private const val KEY_BEDTIME_OVERRIDE_UNTIL = "bedtime_override_until"
    private const val KEY_HAYA_MODE_ENABLED = "haya_mode_enabled"
    private const val KEY_USER_GENDER = "user_gender"
    private const val KEY_QUICK_LENS_ENABLED = "quick_lens_enabled"
    private const val KEY_HIDE_REELS_ENABLED = "hide_reels_enabled"
    private const val KEY_FRIENDS_ONLY_ENABLED = "friends_only_enabled"
    private const val KEY_BATTERY_SAVER_ENABLED = "battery_saver_enabled"

    private lateinit var prefs: SharedPreferences

    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isHayaModeEnabled(): Boolean {
        return prefs.getBoolean(KEY_HAYA_MODE_ENABLED, false)
    }

    fun setHayaModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAYA_MODE_ENABLED, enabled).apply()
    }

    fun getUserGender(): String {
        return prefs.getString(KEY_USER_GENDER, "MALE") ?: "MALE"
    }

    fun setUserGender(gender: String) {
        prefs.edit().putString(KEY_USER_GENDER, gender).apply()
    }

    fun isQuickLensEnabled(): Boolean {
        return prefs.getBoolean(KEY_QUICK_LENS_ENABLED, false)
    }

    fun setQuickLensEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_QUICK_LENS_ENABLED, enabled).apply()
    }

    fun isHideReelsEnabled(): Boolean {
        return prefs.getBoolean(KEY_HIDE_REELS_ENABLED, false)
    }

    fun setHideReelsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_REELS_ENABLED, enabled).apply()
    }

    fun isFriendsOnlyEnabled(): Boolean {
        return prefs.getBoolean(KEY_FRIENDS_ONLY_ENABLED, false)
    }

    fun setFriendsOnlyEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FRIENDS_ONLY_ENABLED, enabled).apply()
    }

    fun isBatterySaverEnabled(): Boolean {
        return prefs.getBoolean(KEY_BATTERY_SAVER_ENABLED, false)
    }

    fun setBatterySaverEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BATTERY_SAVER_ENABLED, enabled).apply()
    }

    fun getTimeLimitMinutes(): Int {
        return prefs.getInt(KEY_TIME_LIMIT_MINUTES, 60)
    }

    fun setTimeLimitMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_TIME_LIMIT_MINUTES, minutes).apply()
    }

    fun isAutoCloseEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_CLOSE_ENABLED, false)
    }

    fun setAutoCloseEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CLOSE_ENABLED, enabled).apply()
    }

    fun getAutoCloseHour(): Int {
        return prefs.getInt(KEY_AUTO_CLOSE_HOUR, 22)
    }

    fun getAutoCloseMinute(): Int {
        return prefs.getInt(KEY_AUTO_CLOSE_MINUTE, 0)
    }

    fun setAutoCloseTime(hour: Int, minute: Int) {
        prefs.edit()
            .putInt(KEY_AUTO_CLOSE_HOUR, hour)
            .putInt(KEY_AUTO_CLOSE_MINUTE, minute)
            .apply()
    }

    fun getFormattedAutoCloseTime(): String {
        val hour = getAutoCloseHour()
        val minute = getAutoCloseMinute()
        val amPm = if (hour >= 12) "PM" else "AM"
        val displayHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return String.format("%d:%02d %s", displayHour, minute, amPm)
    }

    fun isTimeLimitExceeded(currentTimeSpentMs: Long): Boolean {
        if (isUnlimitedOverrideActive()) return false
        val snoozeUntil = prefs.getLong(KEY_SNOOZE_UNTIL, 0)
        if (System.currentTimeMillis() < snoozeUntil) return false
        val limitMs = getTimeLimitMinutes() * 60 * 1000L
        return currentTimeSpentMs >= limitMs
    }

    fun snoozeFor5Minutes() {
        val snoozeUntil = System.currentTimeMillis() + (5 * 60 * 1000)
        prefs.edit().putLong(KEY_SNOOZE_UNTIL, snoozeUntil).apply()
    }

    fun setUnlimitedOverride() {
        prefs.edit().putLong(KEY_OVERRIDE_UNTIL, Long.MAX_VALUE).apply()
    }

    fun isUnlimitedOverrideActive(): Boolean {
        val overrideUntil = prefs.getLong(KEY_OVERRIDE_UNTIL, 0)
        return System.currentTimeMillis() < overrideUntil
    }

    fun resetOverride() {
        prefs.edit()
            .putLong(KEY_OVERRIDE_UNTIL, 0)
            .putLong(KEY_SNOOZE_UNTIL, 0)
            .apply()
    }

    fun isBedtimeNow(): Boolean {
        if (!isAutoCloseEnabled()) return false
        if (isBedtimeOverrideActive()) return false
        val now = java.util.Calendar.getInstance()
        val currentHour = now.get(java.util.Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(java.util.Calendar.MINUTE)
        val closeHour = getAutoCloseHour()
        val closeMinute = getAutoCloseMinute()
        val currentTotalMinutes = currentHour * 60 + currentMinute
        val closeTotalMinutes = closeHour * 60 + closeMinute
        val wakeUpMinutes = (closeTotalMinutes + 480) % 1440
        return if (closeTotalMinutes < wakeUpMinutes) {
            currentTotalMinutes in closeTotalMinutes until wakeUpMinutes
        } else {
            currentTotalMinutes >= closeTotalMinutes || currentTotalMinutes < wakeUpMinutes
        }
    }

    fun setBedtimeOverride() {
        val overrideUntil = System.currentTimeMillis() + (8 * 60 * 60 * 1000)
        prefs.edit().putLong(KEY_BEDTIME_OVERRIDE_UNTIL, overrideUntil).apply()
    }

    fun isBedtimeOverrideActive(): Boolean {
        val overrideUntil = prefs.getLong(KEY_BEDTIME_OVERRIDE_UNTIL, 0)
        return System.currentTimeMillis() < overrideUntil
    }

    fun resetBedtimeOverride() {
        prefs.edit().putLong(KEY_BEDTIME_OVERRIDE_UNTIL, 0).apply()
    }

    fun getRemainingTimeMs(currentTimeSpentMs: Long): Long {
        val limitMs = getTimeLimitMinutes() * 60 * 1000L
        return (limitMs - currentTimeSpentMs).coerceAtLeast(0)
    }

    fun getFormattedRemainingTime(currentTimeSpentMs: Long): String {
        val remainingMs = getRemainingTimeMs(currentTimeSpentMs)
        val totalSeconds = remainingMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> String.format("%dh %dm", hours, minutes)
            minutes > 0 -> String.format("%dm %ds", minutes, seconds)
            else -> String.format("%ds", seconds)
        }
    }

    fun resetDailySession() {
        prefs.edit()
            .putLong(KEY_OVERRIDE_UNTIL, 0)
            .putLong(KEY_SNOOZE_UNTIL, 0)
            .apply()
    }
}
