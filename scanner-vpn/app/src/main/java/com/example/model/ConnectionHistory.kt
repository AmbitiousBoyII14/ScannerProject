package com.example.model

data class ConnectionHistory(
    val id: String,
    val serverName: String,
    val countryCode: String,
    val flagEmoji: String,
    val protocol: String,
    val startedAt: Long,
    val endedAt: Long,
    val durationSeconds: Long,
    val bytesSent: Long,
    val bytesReceived: Long,
    val success: Boolean,
    val failureReason: String? = null
)
