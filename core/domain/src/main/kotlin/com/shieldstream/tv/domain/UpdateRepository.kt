package com.shieldstream.tv.domain

import kotlinx.coroutines.flow.StateFlow

data class AppVersion(
    val name: String,
    val isDebug: Boolean,
    val updateRepo: String,
)

data class AppUpdate(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
)

sealed interface UpdateState {
    data object Unsupported : UpdateState
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val current: String) : UpdateState
    data class Available(val current: String, val update: AppUpdate) : UpdateState
    data class Downloading(val update: AppUpdate, val progress: Float) : UpdateState
    data class NeedsPermission(val update: AppUpdate) : UpdateState
    data class Installing(val update: AppUpdate) : UpdateState
    data class Failed(val message: String, val update: AppUpdate?) : UpdateState
}

interface UpdateRepository {
    val state: StateFlow<UpdateState>

    val promptDismissed: StateFlow<Boolean>

    fun check(manual: Boolean)

    fun downloadAndInstall()

    fun dismissPrompt()
}
