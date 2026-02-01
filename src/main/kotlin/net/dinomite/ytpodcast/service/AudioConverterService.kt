package net.dinomite.ytpodcast.service

import java.nio.file.Files
import java.util.UUID
import kotlin.io.path.deleteIfExists
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.dinomite.ytpodcast.models.AudioData
import org.slf4j.LoggerFactory
import ws.schild.jave.Encoder
import ws.schild.jave.MultimediaObject
import ws.schild.jave.encode.AudioAttributes
import ws.schild.jave.encode.EncodingAttributes

class AudioConverterService {
    private val logger = LoggerFactory.getLogger(AudioConverterService::class.java)
    private val encoder = Encoder()

    @Suppress("Detekt:TooGenericExceptionCaught", "Detekt:TooGenericExceptionThrown")
    suspend fun convertToMp3(audioData: ByteArray, videoId: String): AudioData = withContext(Dispatchers.IO) {
        val tempDir = Files.createTempDirectory("ytpodcast")
        val inputFile = tempDir.resolve("input_${videoId}_${UUID.randomUUID()}")
        val outputFile = tempDir.resolve("output_${videoId}_${UUID.randomUUID()}.mp3")

        try {
            // Write input data to temporary file
            Files.write(inputFile, audioData)

            // Set up audio encoding attributes
            val audioAttributes = AudioAttributes().apply {
                setCodec("libmp3lame")
                setBitRate(128000) // 128 kbps
                setChannels(2)
                setSamplingRate(44100)
            }

            val encodingAttributes = EncodingAttributes().apply {
                setAudioAttributes(audioAttributes)
                setOutputFormat("mp3")
            }

            // Convert audio
            val source = MultimediaObject(inputFile.toFile())
            encoder.encode(source, outputFile.toFile(), encodingAttributes)

            // Read converted file
            val mp3Data = Files.readAllBytes(outputFile)

            logger.info("Successfully converted audio for video $videoId to MP3")

            AudioData(
                data = mp3Data,
                contentType = "audio/mpeg"
            )
        } catch (e: Exception) {
            logger.error("Failed to convert audio for video $videoId", e)
            throw RuntimeException("Audio conversion failed: ${e.message}", e)
        } finally {
            // Clean up temporary files
            inputFile.deleteIfExists()
            outputFile.deleteIfExists()
            tempDir.deleteIfExists()
        }
    }

    @Suppress("Detekt:TooGenericExceptionCaught")
    suspend fun isValidAudioFormat(data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            when {
                data.isMp3() -> true
                data.isMp4() -> true
                data.isWebM() -> true
                data.isOgg() -> true
                else -> false
            }
        } catch (e: Exception) {
            logger.warn("Failed to validate audio format", e)
            false
        }
    }
}

fun ByteArray.isMp4(): Boolean = size >= 12 &&
    this[4] == 'f'.code.toByte() &&
    this[5] == 't'.code.toByte() &&
    this[6] == 'y'.code.toByte() &&
    this[7] == 'p'.code.toByte()

fun ByteArray.isMp3(): Boolean = size >= 3 && (
    (this[0] == 0xFF.toByte() && (this[1].toInt() and 0xE0) == 0xE0) || // MPEG-1/2 Audio
        (this[0] == 'I'.code.toByte() && this[1] == 'D'.code.toByte() && this[2] == '3'.code.toByte()) // ID3v2
    )

fun ByteArray.isWebM(): Boolean = size >= 4 &&
    this[0] == 0x1A.toByte() &&
    this[1] == 0x45.toByte() &&
    this[2] == 0xDF.toByte() &&
    this[3] == 0xA3.toByte()

fun ByteArray.isOgg(): Boolean = size >= 4 &&
    this[0] == 'O'.code.toByte() &&
    this[1] == 'g'.code.toByte() &&
    this[2] == 'g'.code.toByte() &&
    this[3] == 'S'.code.toByte()
