package com.example.ytpodcast.service

import com.example.ytpodcast.models.AudioData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import ws.schild.jave.Encoder
import ws.schild.jave.MultimediaObject
import ws.schild.jave.encode.AudioAttributes
import ws.schild.jave.encode.EncodingAttributes
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.deleteIfExists

class AudioConverterService {
    private val logger = LoggerFactory.getLogger(AudioConverterService::class.java)
    private val encoder = Encoder()
    
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
    
    suspend fun isValidAudioFormat(data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            // Simple check for common audio format signatures
            when {
                // MP3
                data.size >= 3 && (
                    (data[0] == 0xFF.toByte() && (data[1].toInt() and 0xE0) == 0xE0) || // MPEG-1/2 Audio
                    (data[0] == 'I'.code.toByte() && data[1] == 'D'.code.toByte() && data[2] == '3'.code.toByte()) // ID3v2
                ) -> true
                
                // MP4/M4A
                data.size >= 12 && 
                data[4] == 'f'.code.toByte() && 
                data[5] == 't'.code.toByte() && 
                data[6] == 'y'.code.toByte() && 
                data[7] == 'p'.code.toByte() -> true
                
                // WebM/Opus
                data.size >= 4 && 
                data[0] == 0x1A.toByte() && 
                data[1] == 0x45.toByte() && 
                data[2] == 0xDF.toByte() && 
                data[3] == 0xA3.toByte() -> true
                
                // OGG
                data.size >= 4 && 
                data[0] == 'O'.code.toByte() && 
                data[1] == 'g'.code.toByte() && 
                data[2] == 'g'.code.toByte() && 
                data[3] == 'S'.code.toByte() -> true
                
                else -> false
            }
        } catch (e: Exception) {
            logger.warn("Failed to validate audio format", e)
            false
        }
    }
}