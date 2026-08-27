package llm.slop.liquidlsd.export

import llm.slop.liquidlsd.audio.AudioEngine
import java.io.File
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExportArchitectureTest {

    @Test
    fun testVideoResolutionPresets() {
        assertEquals(1920, VideoResolutionPreset.FHD_1080P.width)
        assertEquals(1080, VideoResolutionPreset.FHD_1080P.height)

        assertEquals(3840, VideoResolutionPreset.UHD_4K.width)
        assertEquals(2160, VideoResolutionPreset.UHD_4K.height)

        assertEquals(1080, VideoResolutionPreset.VERTICAL_9_16.width)
        assertEquals(1920, VideoResolutionPreset.VERTICAL_9_16.height)

        assertEquals(1080, VideoResolutionPreset.SQUARE_1_1.width)
        assertEquals(1080, VideoResolutionPreset.SQUARE_1_1.height)
    }

    @Test
    fun testVerticalFlipping() {
        val width = 2
        val height = 2
        // 2x2 image, RGBA = 4 bytes per pixel. Total 16 bytes.
        // Row 0 (bottom in GL): [1, 1, 1, 1], [2, 2, 2, 2]
        // Row 1 (top in GL):    [3, 3, 3, 3], [4, 4, 4, 4]
        val src = ByteBuffer.allocateDirect(16)
        src.put(byteArrayOf(
            1, 1, 1, 1,   2, 2, 2, 2,
            3, 3, 3, 3,   4, 4, 4, 4
        ))
        src.flip()

        val dst = ByteBuffer.allocateDirect(16)
        PboReadbackPipeline.flipVertical(src, dst, width, height)

        // After vertical flipping, top row should be row 1 from GL ([3, 3, 3, 3], [4, 4, 4, 4])
        // Bottom row should be row 0 from GL ([1, 1, 1, 1], [2, 2, 2, 2])
        assertEquals(3.toByte(), dst.get(0))
        assertEquals(4.toByte(), dst.get(4))
        assertEquals(1.toByte(), dst.get(8))
        assertEquals(2.toByte(), dst.get(12))
    }

    @Test
    fun testAudioDeviceListing() {
        val devices = AudioEngine.getAvailableInputDevices()
        assertNotNull(devices)
        assertTrue(devices.isNotEmpty())
        assertTrue(devices.any { it.isDefault })
    }

    @Test
    fun testVideoExportConfigCreation() {
        val file = File("test_output.mp4")
        val config = VideoExportConfig(
            outputFile = file,
            width = 1920,
            height = 1080,
            fps = 60,
            codec = VideoCodec.H264,
            superSamplingFactor = 4
        )
        assertEquals(1920, config.width)
        assertEquals(1080, config.height)
        assertEquals(60, config.fps)
        assertEquals(VideoCodec.H264, config.codec)
        assertEquals(4, config.superSamplingFactor)
    }

    @Test
    fun testBestEncoderSelection() {
        val h264Encoder = FFmpegProcessPipe.getBestEncoder(VideoCodec.H264)
        assertNotNull(h264Encoder)
        assertTrue(h264Encoder in listOf("h264_nvenc", "h264_qsv", "libopenh264", "libx264", "mpeg4"))

        val proresEncoder = FFmpegProcessPipe.getBestEncoder(VideoCodec.PRORES)
        assertNotNull(proresEncoder)
        assertTrue(proresEncoder.contains("prores"))
    }

    @Test
    fun testH264VideoEncodingExecution() {
        val outFile = File.createTempFile("test_h264_", ".mp4")
        outFile.deleteOnExit()

        val width = 320
        val height = 240
        val fps = 30
        val config = VideoExportConfig(
            outputFile = outFile,
            width = width,
            height = height,
            fps = fps,
            codec = VideoCodec.H264,
            bitrateMbps = 2
        )

        val pipe = FFmpegProcessPipe(config)
        assertTrue(pipe.start(), "FFmpeg pipe should start successfully")
        assertTrue(pipe.isAlive(), "FFmpeg process should be alive")

        // Push 30 frames (1 second of video)
        val frameBytes = ByteArray(width * height * 4) { 128.toByte() }
        for (i in 0 until 30) {
            pipe.pushFrame(frameBytes)
            assertTrue(pipe.isAlive(), "Pipe should remain alive during frame pushing")
        }

        val finished = pipe.finish(waitForExit = true)
        assertTrue(finished, "FFmpeg should finish successfully. Logs: ${pipe.getErrorLogs()}")
        assertTrue(outFile.exists(), "Output video file should exist")
        assertTrue(outFile.length() > 0, "Output video file should have non-zero size")
    }

    @Test
    fun testProResVideoEncodingExecution() {
        val outFile = File.createTempFile("test_prores_", ".mov")
        outFile.deleteOnExit()

        val width = 320
        val height = 240
        val fps = 30
        val config = VideoExportConfig(
            outputFile = outFile,
            width = width,
            height = height,
            fps = fps,
            codec = VideoCodec.PRORES,
            bitrateMbps = 5
        )

        val pipe = FFmpegProcessPipe(config)
        assertTrue(pipe.start(), "FFmpeg ProRes pipe should start successfully")
        assertTrue(pipe.isAlive(), "FFmpeg process should be alive")

        // Push 15 frames
        val frameBytes = ByteArray(width * height * 4) { 200.toByte() }
        for (i in 0 until 15) {
            pipe.pushFrame(frameBytes)
            assertTrue(pipe.isAlive(), "Pipe should remain alive during ProRes frame pushing")
        }

        val finished = pipe.finish(waitForExit = true)
        assertTrue(finished, "FFmpeg ProRes should finish successfully. Logs: ${pipe.getErrorLogs()}")
        assertTrue(outFile.exists(), "Output ProRes file should exist")
        assertTrue(outFile.length() > 0, "Output ProRes file should have non-zero size")
    }

    @Test
    fun test720pH264VideoEncoding() {
        val outFile = File.createTempFile("test_720p_60fps_", ".mp4")
        outFile.deleteOnExit()

        val width = 1280
        val height = 720
        val fps = 60
        val config = VideoExportConfig(
            outputFile = outFile,
            width = width,
            height = height,
            fps = fps,
            codec = VideoCodec.H264,
            bitrateMbps = 12
        )

        val pipe = FFmpegProcessPipe(config)
        assertTrue(pipe.start(), "FFmpeg pipe should start successfully for 720p 60fps")
        assertTrue(pipe.isAlive(), "FFmpeg process should be alive")

        // Push 60 frames (1 second of 720p60 video = 221 MB)
        val frameBytes = ByteArray(width * height * 4) { 100.toByte() }
        for (i in 0 until 60) {
            pipe.pushFrame(frameBytes)
            assertTrue(pipe.isAlive(), "Pipe should remain alive at frame $i")
        }

        val finished = pipe.finish(waitForExit = true)
        assertTrue(finished, "FFmpeg should complete successfully without broken pipes. Logs:\n${pipe.getErrorLogs()}")
        assertTrue(outFile.exists(), "Output video file should exist")
        assertTrue(outFile.length() > 1000, "Output video file should contain valid encoded data")
    }

    @Test
    fun testTimeSourceVirtualization() {
        llm.slop.liquidlsd.utils.TimeSource.clearSimulatedTime()
        assertEquals(false, llm.slop.liquidlsd.utils.TimeSource.isSimulated)

        llm.slop.liquidlsd.utils.TimeSource.setSimulatedTime(12.5, 1.0 / 60.0)
        assertEquals(true, llm.slop.liquidlsd.utils.TimeSource.isSimulated)
        assertEquals(12.5, llm.slop.liquidlsd.utils.TimeSource.getTimeSec(), 0.0001)
        assertEquals(12_500_000_000L, llm.slop.liquidlsd.utils.TimeSource.getTimeNanos())
        assertEquals(1.0 / 60.0, llm.slop.liquidlsd.utils.TimeSource.getDeltaTimeSec(), 0.0001)

        llm.slop.liquidlsd.utils.TimeSource.clearSimulatedTime()
        assertEquals(false, llm.slop.liquidlsd.utils.TimeSource.isSimulated)
    }
}
