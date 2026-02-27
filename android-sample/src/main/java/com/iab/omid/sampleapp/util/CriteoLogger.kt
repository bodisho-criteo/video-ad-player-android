package com.iab.omid.sampleapp.util

import android.util.Log
import java.net.URL

/**
 * Internal logging utility. Provides level & category filtering along with specialized helpers.
 */
internal object CriteoLogger {
    // Log levels with priority
    enum class Level(val priority: Int) {
        DEBUG(0),
        INFO(1),
        WARNING(2),
        ERROR(3),
        CRITICAL(4);
    }

    // Log categories for subsystems
    enum class Category {GENERAL, NETWORK, BEACON, VIDEO, VAST, OMID, UI }

    // Configuration (volatile for multi-thread visibility)
    @Volatile var minimumLevel: Level = Level.DEBUG

    // Enabled categories for logging: if empty -> all categories enabled; otherwise only those in the set.
    private val enabledCategories: MutableSet<Category> = mutableSetOf()

    // Public logging methods
    fun debug(message: String, category: Category = Category.GENERAL) = log(Level.DEBUG, message, category)
    fun info(message: String, category: Category = Category.GENERAL) = log(Level.INFO, message, category)
    fun warning(message: String, category: Category = Category.GENERAL) = log(Level.WARNING, message, category)
    fun error(message: String, category: Category = Category.GENERAL) = log(Level.ERROR, message, category)
    fun critical(message: String, category: Category = Category.GENERAL) = log(Level.CRITICAL, message, category)

    // Specialized helper logging methods
    fun network(message: String, url: URL? = null, statusCode: Int? = null) {
        val msg = buildString {
            append(message)
            if (url != null) append(" | URL: ${url.toExternalForm()}")
            if (statusCode != null) append(" | Status: $statusCode")
        }
        log(Level.INFO, msg, Category.NETWORK)
    }

    fun beacon(message: String, url: URL? = null, success: Boolean? = null) {
        val level = if (success == false) Level.WARNING else Level.INFO
        val msg = buildString {
            append(message)
            if (url != null) append(" | ${url.toExternalForm()}")
            if (success != null) append(" | success=$success")
        }
        log(level, msg, Category.BEACON)
    }

    fun video(message: String, currentTime: Double? = null, duration: Double? = null) {
        val msg = buildString {
            append(message)
            if (currentTime != null && duration != null) append(" | ${"%.1f".format(currentTime)}/${"%.1f".format(duration)}s")
        }
        log(Level.INFO, msg, Category.VIDEO)
    }

    // Configuration
    fun setEnabledCategories(vararg categories: Category) {
        synchronized(this) {
            enabledCategories.clear()
            enabledCategories.addAll(categories)
        }
    }

    fun enableAllCategories() {
        synchronized(this) {
            enabledCategories.clear()
        }
    }

    fun isCategoryEnabled(category: Category): Boolean = synchronized(this) {
        enabledCategories.isEmpty() || category in enabledCategories
    }

    // Core implementation with JVM test fallback.
    private fun log(level: Level, message: String, category: Category) {
        if (level.priority < minimumLevel.priority) return
        if (!isCategoryEnabled(category)) return

        val tag = "com.omid.demo.${category.name.lowercase()}"
        try {
            when (level) {
                Level.DEBUG -> Log.d(tag, message)
                Level.INFO -> Log.i(tag, message)
                Level.WARNING -> Log.w(tag, message)
                Level.ERROR -> Log.e(tag, message)
                Level.CRITICAL -> Log.wtf(tag, message)
            }
        } catch (e: RuntimeException) {
            // Fallback for local JVM unit tests without Android log implementation.
            println("[fallback-log][$tag][${level.name}] $message")
        }
    }
}
