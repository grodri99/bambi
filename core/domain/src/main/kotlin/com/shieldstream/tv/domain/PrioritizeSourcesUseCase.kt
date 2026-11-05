package com.shieldstream.tv.domain

import com.shieldstream.tv.model.HdrType
import com.shieldstream.tv.model.StreamSource
import com.shieldstream.tv.model.VideoCodec

class PrioritizeSourcesUseCase {

    operator fun invoke(
        sources: List<StreamSource>,
        supportedHdr: Set<HdrType>,
        hardwareVideoCodecs: Set<VideoCodec> = emptySet(),
        preferCached: Boolean = true,
        preferSpanish: Boolean = false,
    ): List<StreamSource> {
        return sources
            .filterNot {
                it.videoCodec == VideoCodec.AV1 && AV1_4K.containsMatchIn(it.title) &&
                    !it.title.contains("1080p", ignoreCase = true) && VideoCodec.AV1 !in hardwareVideoCodecs
            }
            .map { source ->
                source.copy(
                    softwareDecode = source.softwareDecode &&
                        (source.videoCodec == null || source.videoCodec !in hardwareVideoCodecs),
                )
            }
            .sortedWith(
                compareByDescending<StreamSource> { if (preferCached) it.isCached else true }
                    .thenByDescending { if (preferSpanish) it.spanishAudioScore else 0 }
                    .thenBy { it.softwareDecode }
                    .thenByDescending { effectiveHdrRank(it.hdrType, supportedHdr) }
                    .thenByDescending { it.quality.rank }
                    .thenByDescending { it.seeders ?: 0 }
                    .thenByDescending { it.sizeBytes },
            )
    }

    private companion object {
        val AV1_4K = Regex("(?<![a-z0-9])(2160p?|4k|uhd)(?![a-z0-9])", RegexOption.IGNORE_CASE)
    }

    private fun effectiveHdrRank(sourceHdr: HdrType, supported: Set<HdrType>): Int {
        if (supported.isEmpty()) return sourceHdr.rank
        return supported
            .filter { it.rank <= sourceHdr.rank }
            .maxOfOrNull { it.rank }
            ?: 0
    }
}
