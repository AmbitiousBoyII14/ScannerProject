package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.ConnectionHistory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class HistoryRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("dark_tunnel_history", Context.MODE_PRIVATE)
    private val keyHistory = "connection_history_json"

    private val _history = MutableStateFlow<List<ConnectionHistory>>(emptyList())
    val history: StateFlow<List<ConnectionHistory>> = _history.asStateFlow()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        val raw = prefs.getString(keyHistory, null)
        val list = mutableListOf<ConnectionHistory>()
        if (!raw.isNullOrBlank()) {
            try {
                val array = JSONArray(raw)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        ConnectionHistory(
                            id = obj.getString("id"),
                            serverName = obj.getString("serverName"),
                            countryCode = obj.optString("countryCode", "US"),
                            flagEmoji = obj.optString("flagEmoji", "🌐"),
                            protocol = obj.optString("protocol", "SSH+WS"),
                            startedAt = obj.getLong("startedAt"),
                            endedAt = obj.getLong("endedAt"),
                            durationSeconds = obj.getLong("durationSeconds"),
                            bytesSent = obj.getLong("bytesSent"),
                            bytesReceived = obj.getLong("bytesReceived"),
                            success = obj.optBoolean("success", true),
                            failureReason = if (obj.has("failureReason")) obj.getString("failureReason") else null
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        _history.value = list
    }

    fun addHistory(record: ConnectionHistory) {
        val current = _history.value.toMutableList()
        current.add(0, record)
        if (current.size > 50) {
            current.removeAt(current.lastIndex)
        }
        _history.value = current
        saveToPrefs(current)
    }

    fun clearHistory() {
        _history.value = emptyList()
        prefs.edit().remove(keyHistory).apply()
    }

    private fun saveToPrefs(list: List<ConnectionHistory>) {
        val array = JSONArray()
        list.forEach {
            val obj = JSONObject().apply {
                put("id", it.id)
                put("serverName", it.serverName)
                put("countryCode", it.countryCode)
                put("flagEmoji", it.flagEmoji)
                put("protocol", it.protocol)
                put("startedAt", it.startedAt)
                put("endedAt", it.endedAt)
                put("durationSeconds", it.durationSeconds)
                put("bytesSent", it.bytesSent)
                put("bytesReceived", it.bytesReceived)
                put("success", it.success)
                it.failureReason?.let { reason -> put("failureReason", reason) }
            }
            array.put(obj)
        }
        prefs.edit().putString(keyHistory, array.toString()).apply()
    }
}
