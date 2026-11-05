package com.shieldstream.tv.media

import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import com.shieldstream.tv.model.HdrType
import com.shieldstream.tv.model.VideoCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFormatInspectorTest {
    @Test
    fun `detects actual 4K HEVC HDR10 format`() {
        val info = VideoFormatInspector.inspect(
            Format.Builder()
                .setSampleMimeType(MimeTypes.VIDEO_H265)
                .setWidth(3840)
                .setHeight(2160)
                .setColorInfo(ColorInfo.Builder().setColorTransfer(C.COLOR_TRANSFER_ST2084).build())
                .build(),
            decoderName = "c2.amlogic.hevc.decoder",
        )

        assertEquals("4K", info.resolutionLabel)
        assertEquals(HdrType.HDR10, info.hdrType)
        assertEquals(VideoCodec.HEVC, info.codec)
        assertTrue(info.hardwareDecode == true)
    }

    @Test
    fun `dolby vision profile 7 reports its HDR10 base layer`() {
        val info = VideoFormatInspector.inspect(
            Format.Builder()
                .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
                .setCodecs("dvhe.07.06")
                .build(),
            decoderName = "OMX.Nvidia.h265.decode",
        )

        assertEquals(HdrType.HDR10, info.hdrType)
        assertEquals(VideoCodec.HEVC, info.codec)
    }

    @Test
    fun `supported dolby vision remains dolby vision`() {
        val info = VideoFormatInspector.inspect(
            Format.Builder()
                .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
                .setCodecs("dvh1.08.07")
                .build(),
            decoderName = "OMX.Nvidia.DOVI.decode",
        )

        assertEquals(HdrType.DOLBY_VISION, info.hdrType)
    }

    @Test
    fun `dolby vision AV1 reports decoded video without DV metadata`() {
        val info = VideoFormatInspector.inspect(
            Format.Builder()
                .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
                .setCodecs("dav1.10.01")
                .setColorInfo(ColorInfo.Builder().setColorTransfer(C.COLOR_TRANSFER_ST2084).build())
                .build(),
            decoderName = "c2.amlogic.av1.decoder",
        )

        assertEquals(HdrType.HDR10, info.hdrType)
        assertEquals(VideoCodec.AV1, info.codec)
    }

    @Test
    fun `dav1d software output does not claim HDR`() {
        val info = VideoFormatInspector.inspect(
            Format.Builder()
                .setSampleMimeType(MimeTypes.VIDEO_AV1)
                .setColorInfo(ColorInfo.Builder().setColorTransfer(C.COLOR_TRANSFER_ST2084).build())
                .build(),
            decoderName = "Dav1dVideoRenderer",
        )

        assertEquals(HdrType.SDR, info.hdrType)
        assertEquals(VideoCodec.AV1, info.codec)
        assertFalse(info.hardwareDecode == true)
    }

    @Test
    fun `HLG is distinguished from SDR`() {
        val info = VideoFormatInspector.inspect(
            Format.Builder()
                .setSampleMimeType(MimeTypes.VIDEO_H265)
                .setColorInfo(ColorInfo.Builder().setColorTransfer(C.COLOR_TRANSFER_HLG).build())
                .build(),
        )

        assertEquals(HdrType.HLG, info.hdrType)
        assertEquals(null, info.hardwareDecode)
    }
}
