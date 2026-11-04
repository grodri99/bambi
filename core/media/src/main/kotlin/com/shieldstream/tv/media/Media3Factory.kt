package com.shieldstream.tv.media

import com.shieldstream.tv.model.Lang
import android.content.Context
import android.content.pm.ApplicationInfo
import android.media.MediaFormat
import android.os.Handler
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.shieldstream.tv.media.mkv.LenientExtractorsFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import com.shieldstream.tv.media.av1.Dav1dVideoRenderer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import android.media.MediaCodecInfo.CodecProfileLevel as AndroidProfileLevel

/**
 * Construye el ExoPlayer/Media3 optimizado para Tegra X1+:
 *  - MediaCodec con aceleración HW (HEVC/VP9 nativas).
 *  - Fallback de decodificador como último recurso.
 *  - Dolby Vision perfil 5/8 → decoder DV del Shield. Perfil 7 → capa base HEVC (HDR10), ver abajo.
 *  - Audio en español si existe, si no inglés (antes cogía la pista "default", a veces rusa).
 *  - LoadControl agresivo (buffer 50–100 s) para evitar micro-stutters.
 */
@OptIn(UnstableApi::class)
@Singleton
class Media3Factory @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun createPlayer(maxBufferSeconds: Int): ExoPlayer {
        val codecSelector = MediaCodecSelector { mimeType, secure, tunneling ->
            val infos = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, secure, tunneling)
            if (mimeType == MimeTypes.VIDEO_DOLBY_VISION) {
                // Prioriza el decodificador DV del Shield (OMX.Nvidia.DOVI.decode).
                infos.sortedByDescending { it.name.contains("dovi", ignoreCase = true) || it.name.contains(".dv", ignoreCase = true) }
            } else {
                infos
            }
        }

        val renderersFactory = ShieldRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector(codecSelector)

        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    // Audio: el del idioma de la app primero (fijo hasta reiniciar: el player se crea de nuevo).
                    .setPreferredAudioLanguages(*if (Lang.isEnglish) arrayOf("en", "eng", "es", "spa") else arrayOf("es", "spa", "en", "eng"))
                    // Subtítulos desactivados por defecto (como Netflix): se ignora la marca "default"
                    // que traen muchos releases (p. ej. checo). Los forzados sí se muestran.
                    .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT),
            )
        }

        val lowMemory = maxBufferSeconds <= 40
        val maxBufferMs = maxBufferSeconds.coerceIn(20, 100) * 1000
        val minBufferMs = minOf(if (lowMemory) 20_000 else 50_000, maxBufferMs)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs */ minBufferMs,
                /* maxBufferMs */ maxBufferMs,
                /* bufferForPlaybackMs */ if (lowMemory) 3_000 else 5_000,
                /* bufferForPlaybackAfterRebufferMs */ if (lowMemory) 5_000 else 10_000,
            )
            .setBackBuffer(/* durationMs */ if (lowMemory) 5_000 else 15_000, /* retainAfterEnd */ false)
            // Low buffer presets also cap Java heap usage more tightly on smaller TV boxes.
            .setTargetBufferBytes((if (lowMemory) 64 else 112) * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()

        return ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            // MKV con una pista comprimida (zlib): se ignora esa pista y se reproduce el resto (antes: error al abrir).
            .setMediaSourceFactory(DefaultMediaSourceFactory(context, LenientExtractorsFactory()))
            .build()
            // Log de decoders/formatos/errores (tag EventLogger) para diagnosticar pantallas negras.
            // Solo en "Bambi (pruebas)".
            .also { if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) it.addAnalyticsListener(EventLogger()) }
    }
}

/** Sustituye el renderer de vídeo por defecto por uno que entiende Dolby Vision perfil 7. */
@OptIn(UnstableApi::class)
private class ShieldRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>,
    ) {
        out.add(
            DolbyVisionAwareVideoRenderer(
                context,
                mediaCodecSelector,
                allowedVideoJoiningTimeMs,
                enableDecoderFallback,
                eventHandler,
                eventListener,
            ),
        )
        // AV1 por CPU con dav1d (el de VLC/Kodi): en 10 bits sobre ARM rinde mucho más que libgav1.
        // Si su .so no carga, supportsFormat devuelve "no soportado" y entra libgav1 (abajo) como reserva.
        out.add(Dav1dVideoRenderer(allowedVideoJoiningTimeMs, eventHandler, eventListener, 50))
        // AV1 por CPU (libgav1): la Shield no tiene AV1 por hardware. Va DETRÁS del renderer MediaCodec,
        // así que solo entra cuando ningún decoder hardware admite la pista. Se carga por reflexión:
        // el .aar nativo vive en app/libs y este módulo no depende de él al compilar.
        runCatching {
            val clazz = Class.forName("androidx.media3.decoder.av1.Libgav1VideoRenderer")
            val ctor = clazz.getConstructor(
                Long::class.javaPrimitiveType,
                Handler::class.java,
                VideoRendererEventListener::class.java,
                Int::class.javaPrimitiveType,
            )
            out.add(ctor.newInstance(allowedVideoJoiningTimeMs, eventHandler, eventListener, 50) as Renderer)
            Log.i("Media3Factory", "Decoder AV1 por software (libgav1) disponible")
        }.onFailure { Log.w("Media3Factory", "Sin decoder AV1 por software: ${it.message}") }
    }
}

/**
 * Dolby Vision PERFIL 7 (doble capa: los REMUX de UHD Blu-ray) NO lo soporta el decoder DV del
 * Shield (`OMX.Nvidia.DOVI.decode` → "Format exceeds selected codec's capabilities"): la tele se
 * queda en negro mientras suena el audio. Igual que hace Kodi, se decodifica solo la capa base
 * HEVC Main10 (HDR10) con el decoder HEVC; la capa de mejora y el RPU se ignoran.
 * Perfiles 5 y 8 siguen yendo por el decoder DV.
 */
@OptIn(UnstableApi::class)
private class DolbyVisionAwareVideoRenderer(
    context: Context,
    mediaCodecSelector: MediaCodecSelector,
    allowedJoiningTimeMs: Long,
    enableDecoderFallback: Boolean,
    eventHandler: Handler?,
    eventListener: VideoRendererEventListener?,
) : MediaCodecVideoRenderer(
    context,
    mediaCodecSelector,
    allowedJoiningTimeMs,
    enableDecoderFallback,
    eventHandler,
    eventListener,
    MAX_DROPPED_FRAMES_TO_NOTIFY,
) {
    override fun getDecoderInfos(
        mediaCodecSelector: MediaCodecSelector,
        format: Format,
        requiresSecureDecoder: Boolean,
    ): List<MediaCodecInfo> {
        // DV perfil 10 = AV1 con metadatos DV: la Shield no tiene AV1 por hardware (ni el decoder DV lo
        // decodifica: pantalla negra). Sin decoders aquí → lo coge dav1d (Dav1dVideoRenderer).
        if (isDolbyVisionAv1(format)) return emptyList()
        if (isDolbyVisionProfile7(format)) {
            val hevc = MediaCodecUtil.getDecoderInfos(MimeTypes.VIDEO_H265, requiresSecureDecoder, false)
            if (hevc.isNotEmpty()) return hevc
        }
        return super.getDecoderInfos(mediaCodecSelector, format, requiresSecureDecoder)
    }

    override fun getMediaFormat(
        format: Format,
        codecMimeType: String,
        codecMaxValues: CodecMaxValues,
        codecOperatingRate: Float,
        deviceNeedsNoPostProcessWorkaround: Boolean,
        tunnelingAudioSessionId: Int,
    ): MediaFormat {
        val mediaFormat = super.getMediaFormat(
            format,
            codecMimeType,
            codecMaxValues,
            codecOperatingRate,
            deviceNeedsNoPostProcessWorkaround,
            tunnelingAudioSessionId,
        )
        if (codecMimeType == MimeTypes.VIDEO_H265 && isDolbyVisionProfile7(format)) {
            // Media3 pone el perfil DV en el MediaFormat; al decoder HEVC hay que darle el de la capa base.
            mediaFormat.setInteger(MediaFormat.KEY_PROFILE, AndroidProfileLevel.HEVCProfileMain10HDR10)
        }
        return mediaFormat
    }

    private companion object {
        const val MAX_DROPPED_FRAMES_TO_NOTIFY = 50
    }
}

internal fun isDolbyVisionAv1(format: Format): Boolean =
    format.sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION && format.codecs?.startsWith("dav1") == true

private fun isDolbyVisionProfile7(format: Format): Boolean {
    if (format.sampleMimeType != MimeTypes.VIDEO_DOLBY_VISION) return false
    val codecs = format.codecs ?: return false
    return codecs.startsWith("dvhe.07") || codecs.startsWith("dvh1.07")
}
