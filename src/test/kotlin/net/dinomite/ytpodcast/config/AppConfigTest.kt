package net.dinomite.ytpodcast.config

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AppConfigTest {
    @Test
    fun `AppConfig holds baseUrl`() {
        val config = AppConfig(baseUrl = "http://example.com", tempDir = "/tmp")

        config.baseUrl shouldBe "http://example.com"
    }

    @Test
    fun `AppConfig holds tempDir`() {
        val config = AppConfig(baseUrl = "", tempDir = "/custom/temp")

        config.tempDir shouldBe "/custom/temp"
    }

    @Test
    fun `AppConfig allows empty tempDir`() {
        val config = AppConfig(baseUrl = "", tempDir = "")

        config.tempDir shouldBe ""
    }
}
