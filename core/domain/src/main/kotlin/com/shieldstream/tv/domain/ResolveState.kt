package com.shieldstream.tv.domain

sealed interface ResolveState {
    data class Progress(val message: String) : ResolveState
    data class Ready(val url: String) : ResolveState

    data class Failed(val message: String, val notCached: Boolean = false, val blocked: Boolean = false) : ResolveState
}
