package com.educatorapp.models

data class PresenceReport(
    val deviceId: String,
    val time: String,
    val isAlone: Boolean
)
