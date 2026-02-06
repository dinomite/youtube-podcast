package net.dinomite.ytpodcast.config

import com.typesafe.config.ConfigFactory
import io.kotest.matchers.shouldBe
import io.ktor.server.config.HoconApplicationConfig
import java.io.InputStreamReader
import org.junit.jupiter.api.Test

class ConfigFileLoadingTest {
    @Test
    fun `loads configuration from application conf file`() {
        // Load the config file from test resources
        val config = HoconApplicationConfig(
            ConfigFactory.parseReader(
                InputStreamReader(
                    javaClass.classLoader.getResourceAsStream("test-application.conf")
                        ?: error("test-application.conf not found")
                )
            )
        )

        val appConfig = AppConfig.load(config)

        appConfig.authUsername shouldBe "testuser"
        appConfig.authPassword shouldBe "testpass"
        appConfig.baseUrl shouldBe "http://localhost:8080"
        appConfig.tempDir shouldBe "/tmp/ytpodcast"
    }
}
