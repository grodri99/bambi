package com.shieldstream.tv.domain

import com.shieldstream.tv.model.Profile
import kotlinx.coroutines.flow.StateFlow

interface ProfileRepository {
    val profiles: StateFlow<List<Profile>>
    val active: StateFlow<Profile>

    fun select(id: String)

    fun save(profile: Profile): Profile

    fun draft(): Profile

    fun delete(id: String)
}
