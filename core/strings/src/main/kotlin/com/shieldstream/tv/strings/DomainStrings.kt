package com.shieldstream.tv.strings

import androidx.annotation.StringRes
import com.shieldstream.tv.domain.ApiService
import com.shieldstream.tv.domain.SourceProvider

@get:StringRes
val ApiService.titleResource: Int
    get() = when (this) {
        ApiService.REAL_DEBRID -> R.string.service_title_real_debrid
        ApiService.TMDB -> R.string.service_title_tmdb
        ApiService.SUBDL -> R.string.service_title_subdl
        ApiService.AIOSTREAMS -> R.string.service_title_aiostreams
        ApiService.TORBOX -> R.string.service_title_torbox
        ApiService.PREMIUMIZE -> R.string.service_title_premiumize
        ApiService.ALLDEBRID -> R.string.service_title_alldebrid
        ApiService.DEBRID_LINK -> R.string.service_title_debrid_link
        ApiService.OFFCLOUD -> R.string.service_title_offcloud
    }

@get:StringRes
val ApiService.keyNameResource: Int
    get() = when (this) {
        ApiService.REAL_DEBRID -> R.string.service_api_token
        ApiService.TMDB -> R.string.service_api_key_v3
        ApiService.AIOSTREAMS -> R.string.service_manifest_url
        else -> R.string.service_api_key
    }

@get:StringRes
val ApiService.whereToGetResource: Int
    get() = when (this) {
        ApiService.REAL_DEBRID -> R.string.service_where_real_debrid
        ApiService.TMDB -> R.string.service_where_tmdb
        ApiService.SUBDL -> R.string.service_where_subdl
        ApiService.AIOSTREAMS -> R.string.service_where_aiostreams
        ApiService.TORBOX -> R.string.service_where_torbox
        ApiService.PREMIUMIZE -> R.string.service_where_premiumize
        ApiService.ALLDEBRID -> R.string.service_where_alldebrid
        ApiService.DEBRID_LINK -> R.string.service_where_debrid_link
        ApiService.OFFCLOUD -> R.string.service_where_offcloud
    }

@get:StringRes
val ApiService.usedForResource: Int
    get() = when (this) {
        ApiService.REAL_DEBRID -> R.string.service_used_real_debrid
        ApiService.TMDB -> R.string.service_used_tmdb
        ApiService.SUBDL -> R.string.service_used_subdl
        ApiService.AIOSTREAMS -> R.string.service_used_aiostreams
        else -> R.string.service_used_torrentio
    }

@get:StringRes
val SourceProvider.titleResource: Int
    get() = when (this) {
        SourceProvider.TORRENTIO -> R.string.provider_torrentio
        SourceProvider.PEERFLIX -> R.string.provider_peerflix
        SourceProvider.KNABEN -> R.string.provider_knaben
        SourceProvider.THEPIRATEBAY -> R.string.provider_pirate_bay
        SourceProvider.NYAA -> R.string.provider_nyaa
        SourceProvider.COMET -> R.string.provider_comet
        SourceProvider.AIOSTREAMS -> R.string.provider_aiostreams
    }

@get:StringRes
val SourceProvider.sectionResource: Int
    get() = when (this) {
        SourceProvider.KNABEN, SourceProvider.THEPIRATEBAY, SourceProvider.NYAA -> R.string.provider_pirate_bay
        else -> titleResource
    }
