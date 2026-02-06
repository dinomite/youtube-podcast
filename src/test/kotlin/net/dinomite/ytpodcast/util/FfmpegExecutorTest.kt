package net.dinomite.ytpodcast.util

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class FfmpegExecutorTest {
    @Test
    fun `buildConvertCommand builds correct ffmpeg command`() {
        val command = FfmpegExecutor.buildConvertCommand("/tmp/input.webm")

        command shouldBe listOf(
            "ffmpeg",
            "-i", "/tmp/input.webm",
            "-codec:a", "libmp3lame",
            "-q:a", "0",
            "-f", "mp3",
            "pipe:1",
        )
    }
}
