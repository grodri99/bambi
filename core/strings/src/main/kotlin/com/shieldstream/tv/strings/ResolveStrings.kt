package com.shieldstream.tv.strings

import com.shieldstream.tv.domain.ResolveStreamTexts
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResourceResolveStreamTexts @Inject constructor(
    private val texts: Texts,
) : ResolveStreamTexts {
    override fun requestingLink(service: String): String = texts.get(R.string.resolve_requesting_link, service)
    override fun lookingInLibrary(service: String): String = texts.get(R.string.resolve_looking_library, service)
    override fun unrestrictingLink(): String = texts.get(R.string.resolve_unrestricting)
    override fun addingToDebrid(service: String): String = texts.get(R.string.resolve_adding, service)
    override fun couldNotReadMagnet(service: String): String = texts.get(R.string.resolve_read_magnet_failed, service)
    override fun noVideoFiles(): String = texts.get(R.string.resolve_no_video_files)
    override fun checkingCache(service: String): String = texts.get(R.string.resolve_checking_cache, service)
    override fun sourceNotCached(service: String): String = texts.get(R.string.resolve_not_cached, service)
    override fun noLinks(service: String): String = texts.get(R.string.resolve_no_links, service)
    override fun directMagnetUnsupported(service: String): String = texts.get(R.string.resolve_direct_magnet_unsupported, service)
    override fun resolutionError(): String = texts.get(R.string.resolve_error)
    override fun torrentStatus(service: String, status: String): String = texts.get(R.string.resolve_torrent_status, service, status)
}
