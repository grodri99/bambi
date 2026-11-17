package com.shieldstream.tv.data.repository

import android.content.Context
import com.shieldstream.tv.domain.ProfileRepository
import com.shieldstream.tv.domain.TrackMemoryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackMemoryRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profiles: ProfileRepository,
) : TrackMemoryRepository {

    override fun audio(mediaKey: String): String? = prefs().getString(key("a", mediaKey), null)

    override fun subtitle(mediaKey: String): String? = prefs().getString(key("s", mediaKey), null)

    override fun saveAudio(mediaKey: String, language: String) {
        prefs().edit().putString(key("a", mediaKey), language).apply()
    }

    override fun saveSubtitle(mediaKey: String, language: String) {
        prefs().edit().putString(key("s", mediaKey), language).apply()
    }

    override fun saveSubtitlesOff(mediaKey: String) {
        prefs().edit().putString(key("s", mediaKey), TrackMemoryRepository.SUBTITLES_OFF).apply()
    }

    private fun prefs() = context.getSharedPreferences(
        "track_memory_${profiles.active.value.id}",
        Context.MODE_PRIVATE,
    )

    private fun key(kind: String, mediaKey: String) = "$kind:$mediaKey"
}
