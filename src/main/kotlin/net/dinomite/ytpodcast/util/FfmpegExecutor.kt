package net.dinomite.ytpodcast.util

import java.io.InputStream
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory

/**
 * Executes ffmpeg to convert audio files to MP3 format, streaming the output.
 *
 * The conversion process reads from a file and writes MP3 data to stdout,
 * which is exposed as an InputStream for streaming to clients.
 */
open class FfmpegExecutor {
    private val logger = LoggerFactory.getLogger(FfmpegExecutor::class.java)

    /**
     * Starts an ffmpeg process that converts the input file to MP3, streaming to stdout.
     *
     * The caller is responsible for:
     * - Reading the returned InputStream to completion
     * - Calling [ConversionProcess.waitFor] after reading to check for errors
     * - Destroying the process if reading is abandoned
     *
     * @param inputFile Path to the raw audio file
     * @return A [ConversionProcess] wrapping the ffmpeg process
     */
    open fun startConversion(inputFile: String): ConversionProcess {
        val command = buildConvertCommand(inputFile)
        logger.debug("Starting ffmpeg: {}", command.joinToString(" "))

        val process = ProcessBuilder(command)
            .redirectErrorStream(false)
            .start()

        return FfmpegConversionProcess(process)
    }

    companion object {
        fun buildConvertCommand(inputPath: String): List<String> = listOf(
            "ffmpeg",
            "-i", inputPath,
            "-codec:a", "libmp3lame",
            "-q:a", "0",
            "-f", "mp3",
            "pipe:1",
        )
    }
}

/**
 * Wraps a running ffmpeg conversion process.
 */
interface ConversionProcess {
    /** The MP3 audio stream (ffmpeg's stdout) */
    val inputStream: InputStream

    /**
     * Waits for the ffmpeg process to complete and checks for errors.
     *
     * @param timeoutMinutes Maximum time to wait
     * @throws FfmpegException if the process times out or exits with non-zero code
     */
    fun waitFor(timeoutMinutes: Long = 10)

    /** Forcibly destroys the ffmpeg process. */
    fun destroy()
}

class FfmpegConversionProcess(private val process: Process) : ConversionProcess {
    override val inputStream: InputStream get() = process.inputStream

    override fun waitFor(timeoutMinutes: Long) {
        val completed = process.waitFor(timeoutMinutes, TimeUnit.MINUTES)
        if (!completed) {
            process.destroyForcibly()
            throw FfmpegException("ffmpeg timed out after $timeoutMinutes minutes")
        }
        val exitCode = process.exitValue()
        if (exitCode != 0) {
            val stderr = process.errorStream.bufferedReader().readText()
            throw FfmpegException("ffmpeg failed with exit code $exitCode: $stderr")
        }
    }

    override fun destroy() {
        process.destroyForcibly()
    }
}

class FfmpegException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
