package com.shieldstream.tv.domain

import com.shieldstream.tv.model.ShowEpisodeStatus

interface EpisodeReleaseRepository {
    suspend fun status(showId: String): ShowEpisodeStatus?

    suspend fun isComplete(showId: String): Boolean
}
