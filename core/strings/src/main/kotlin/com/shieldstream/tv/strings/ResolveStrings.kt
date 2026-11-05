package com.shieldstream.tv.strings

import com.shieldstream.tv.domain.ResolveStreamTexts
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResourceResolveStreamTexts @Inject constructor(
    private val texts: Texts,
) : ResolveStreamTexts {
    override fun debridService(): String = texts.get(R.string.resolve_debrid_service)
    override fun requestingLink(service: String): String = texts.get(R.string.resolve_requesting_link, service)
    override fun lookingInLibrary(): String = texts.get(R.string.resolve_looking_library)
    override fun unrestrictingLink(): String = texts.get(R.string.resolve_unrestricting)
    override fun addingToRealDebrid(): String = texts.get(R.string.resolve_adding)
    override fun couldNotReadMagnet(): String = texts.get(R.string.resolve_read_magnet_failed)
    override fun noVideoFiles(): String = texts.get(R.string.resolve_no_video_files)
    override fun checkingCache(): String = texts.get(R.string.resolve_checking_cache)
    override fun sourceNotCached(): String = texts.get(R.string.resolve_not_cached)
    override fun noLinks(): String = texts.get(R.string.resolve_no_links)
    override fun resolutionError(): String = texts.get(R.string.resolve_error)
    override fun torrentStatus(status: String): String = texts.get(R.string.resolve_torrent_status, status)
}
