package com.shieldstream.tv.domain

/**
 * Recuerda la pista de audio y el subtítulo elegidos a mano para cada serie/película, por perfil.
 * En subtítulos, [SUBTITLES_OFF] significa que el usuario los apagó expresamente.
 */
interface TrackMemoryRepository {
    fun audio(mediaKey: String): String?

    fun subtitle(mediaKey: String): String?

    fun saveAudio(mediaKey: String, language: String)

    fun saveSubtitle(mediaKey: String, language: String)

    fun saveSubtitlesOff(mediaKey: String)

    companion object {
        const val SUBTITLES_OFF = "off"
    }
}
