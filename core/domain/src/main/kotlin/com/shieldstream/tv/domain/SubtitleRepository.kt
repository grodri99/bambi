package com.shieldstream.tv.domain

import com.shieldstream.tv.model.ExternalSubtitle
import com.shieldstream.tv.model.SubtitleFile
import com.shieldstream.tv.model.SubtitleQuery

interface SubtitleRepository {
    suspend fun search(query: SubtitleQuery): List<ExternalSubtitle>

    suspend fun download(subtitle: ExternalSubtitle, query: SubtitleQuery): SubtitleFile
}
