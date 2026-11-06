package com.shieldstream.tv.domain

import kotlinx.coroutines.flow.StateFlow

interface IthilAccessRepository {
    val unlocked: StateFlow<Boolean>
    val isUnlocked: Boolean

    fun unlock()
}
