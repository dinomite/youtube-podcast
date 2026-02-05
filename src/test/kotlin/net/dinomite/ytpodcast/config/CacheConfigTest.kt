package net.dinomite.ytpodcast.config

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class CacheConfigTest {
    @Test
    fun `CacheConfig holds maxSize`() {
        val config = CacheConfig(
            maxSize = 5368709120L,
            maxCount = 100,
            directory = "/tmp/cache"
        )

        config.maxSize shouldBe 5368709120L
    }

    @Test
    fun `CacheConfig holds maxCount`() {
        val config = CacheConfig(
            maxSize = 5368709120L,
            maxCount = 100,
            directory = "/tmp/cache"
        )

        config.maxCount shouldBe 100
    }

    @Test
    fun `CacheConfig holds directory`() {
        val config = CacheConfig(
            maxSize = 5368709120L,
            maxCount = 100,
            directory = "/tmp/cache"
        )

        config.directory shouldBe "/tmp/cache"
    }

    @Test
    fun `CacheConfig allows zero for unlimited size`() {
        val config = CacheConfig(
            maxSize = 0L,
            maxCount = 100,
            directory = "/tmp/cache"
        )

        config.maxSize shouldBe 0L
    }

    @Test
    fun `CacheConfig allows zero for unlimited count`() {
        val config = CacheConfig(
            maxSize = 5368709120L,
            maxCount = 0,
            directory = "/tmp/cache"
        )

        config.maxCount shouldBe 0
    }
}
