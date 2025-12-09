package com.bili.bilitv

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object SearchHistoryManager {
    private const val PREFS_NAME = "bili_search_history"
    private const val KEY_HISTORY = "history"
    private const val MAX_COUNT = 20

    private val json = Json { ignoreUnknownKeys = true }

    fun load(): List<String> {
        val context = SessionManager.getAppContext() ?: return emptyList()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<String>>(stored)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(list: List<String>) {
        val context = SessionManager.getAppContext() ?: return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_HISTORY, json.encodeToString(list.take(MAX_COUNT))).apply()
    }
}

