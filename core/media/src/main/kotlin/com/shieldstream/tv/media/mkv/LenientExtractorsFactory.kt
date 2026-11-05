package com.shieldstream.tv.media.mkv

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser

@OptIn(UnstableApi::class)
class LenientExtractorsFactory : ExtractorsFactory {

    private val delegate = DefaultExtractorsFactory()

    private var subtitleParserFactory: SubtitleParser.Factory = DefaultSubtitleParserFactory()
    private var textTrackTranscodingEnabled = true

    @Synchronized
    override fun experimentalSetTextTrackTranscodingEnabled(enabled: Boolean): ExtractorsFactory {
        delegate.experimentalSetTextTrackTranscodingEnabled(enabled)
        textTrackTranscodingEnabled = enabled
        return this
    }

    @Synchronized
    override fun setSubtitleParserFactory(factory: SubtitleParser.Factory): ExtractorsFactory {
        delegate.setSubtitleParserFactory(factory)
        subtitleParserFactory = factory
        return this
    }

    @Synchronized
    override fun experimentalSetCodecsToParseWithinGopSampleDependencies(codecs: Int): ExtractorsFactory {
        delegate.experimentalSetCodecsToParseWithinGopSampleDependencies(codecs)
        return this
    }

    @Synchronized
    override fun createExtractors(): Array<Extractor> = swapMatroska(delegate.createExtractors())

    @Synchronized
    override fun createExtractors(uri: Uri, responseHeaders: Map<String, List<String>>): Array<Extractor> =
        swapMatroska(delegate.createExtractors(uri, responseHeaders))

    private fun swapMatroska(extractors: Array<Extractor>): Array<Extractor> = Array(extractors.size) { i ->
        val e = extractors[i]
        if (e is MatroskaExtractor) {
            LenientMatroskaExtractor(
                subtitleParserFactory,
                if (textTrackTranscodingEnabled) 0 else LenientMatroskaExtractor.FLAG_EMIT_RAW_SUBTITLE_DATA,
            )
        } else {
            e
        }
    }
}
