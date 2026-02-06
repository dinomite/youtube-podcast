package net.dinomite.ytpodcast.testsupport

import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import net.dinomite.ytpodcast.util.ConversionProcess
import net.dinomite.ytpodcast.util.FfmpegExecutor

/**
 * Stub FfmpegExecutor that returns the input file content as-is (no actual conversion).
 */
class StubFfmpegExecutor : FfmpegExecutor() {
    override fun startConversion(inputFile: String): ConversionProcess = object : ConversionProcess {
        override val inputStream: InputStream = FileInputStream(File(inputFile))
        override fun waitFor(timeoutMinutes: Long) {}
        override fun destroy() {}
    }
}
