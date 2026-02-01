package net.dinomite.ytpodcast.service

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.AbstractBooleanAssert
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AudioConverterServiceTest {
    private lateinit var audioConverterService: AudioConverterService

    @BeforeEach
    fun setup() {
        audioConverterService = AudioConverterService()
    }

    @Nested
    inner class IsValidAudioFormat {
        @Test
        fun `should detect MP3 with ID3 tag`() {
            runBlocking {
                val mp3Data = byteArrayOf(
                    'I'.code.toByte(),
                    'D'.code.toByte(),
                    '3'.code.toByte(),
                    0x03,
                    0x00,
                    0x00,
                    0x00,
                    0x00
                )

                val isValid = audioConverterService.isValidAudioFormat(mp3Data)
                assertThat(isValid).isTrue()
            }
        }

        @Test
        fun `should detect MP3 with MPEG header`() {
            runBlocking {
                val mp3Data = byteArrayOf(
                    0xFF.toByte(),
                    0xFB.toByte(),
                    0x90.toByte(),
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00
                )

                val isValid = audioConverterService.isValidAudioFormat(mp3Data)
                assertThat(isValid).isTrue()
            }
        }

        @Test
        fun `should detect MP4 format`() {
            runBlocking {
                val mp4Data = byteArrayOf(
                    0x00, 0x00, 0x00, 0x20,
                    'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
                    'M'.code.toByte(), '4'.code.toByte(), 'A'.code.toByte(), ' '.code.toByte()
                )

                val isValid = audioConverterService.isValidAudioFormat(mp4Data)
                assertThat(isValid).isTrue()
            }
        }

        @Test
        fun `should detect WebM format`() {
            runBlocking {
                val webmData = byteArrayOf(
                    0x1A.toByte(),
                    0x45.toByte(),
                    0xDF.toByte(),
                    0xA3.toByte(),
                    0x00,
                    0x00,
                    0x00,
                    0x00
                )

                val isValid = audioConverterService.isValidAudioFormat(webmData)
                assertThat(isValid).isTrue()
            }
        }

        @Test
        fun `should detect OGG format`() {
            runBlocking {
                val oggData = byteArrayOf(
                    'O'.code.toByte(),
                    'g'.code.toByte(),
                    'g'.code.toByte(),
                    'S'.code.toByte(),
                    0x00,
                    0x02,
                    0x00,
                    0x00
                )

                val isValid = audioConverterService.isValidAudioFormat(oggData)
                assertThat(isValid).isTrue()
            }
        }

        @Test
        fun `should return false for invalid format`() {
            runBlocking {
                val invalidData = byteArrayOf(0x00, 0x00, 0x00, 0x00)

                val isValid = audioConverterService.isValidAudioFormat(invalidData)
                assertThat(isValid).isFalse()
            }
        }

        @Test
        fun `should handle empty array`() {
            runBlocking {
                val emptyData = byteArrayOf()

                val isValid = audioConverterService.isValidAudioFormat(emptyData)
                assertThat(isValid).isFalse()
            }
        }
    }
}
