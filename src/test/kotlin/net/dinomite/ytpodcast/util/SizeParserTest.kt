package net.dinomite.ytpodcast.util

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SizeParserTest {
    @Test
    fun `parseSize handles bytes`() {
        parseSize("1024") shouldBe 1024L
        parseSize("100B") shouldBe 100L
        parseSize("50 B") shouldBe 50L
    }

    @Test
    fun `parseSize handles kilobytes`() {
        parseSize("1KB") shouldBe 1024L
        parseSize("5 KB") shouldBe 5120L
        parseSize("10kb") shouldBe 10240L
    }

    @Test
    fun `parseSize handles megabytes`() {
        parseSize("1MB") shouldBe 1048576L
        parseSize("5 MB") shouldBe 5242880L
        parseSize("100mb") shouldBe 104857600L
    }

    @Test
    fun `parseSize handles gigabytes`() {
        parseSize("1GB") shouldBe 1073741824L
        parseSize("5 GB") shouldBe 5368709120L
        parseSize("10gb") shouldBe 10737418240L
    }

    @Test
    fun `parseSize handles terabytes`() {
        parseSize("1TB") shouldBe 1099511627776L
        parseSize("2 tb") shouldBe 2199023255552L
    }

    @Test
    fun `parseSize treats zero as unlimited`() {
        parseSize("0") shouldBe 0L
        parseSize("0B") shouldBe 0L
        parseSize("0 GB") shouldBe 0L
    }

    @Test
    fun `parseSize is case insensitive`() {
        parseSize("5GB") shouldBe 5368709120L
        parseSize("5Gb") shouldBe 5368709120L
        parseSize("5gB") shouldBe 5368709120L
        parseSize("5gb") shouldBe 5368709120L
    }

    @Test
    fun `parseSize throws on invalid format`() {
        shouldThrow<IllegalArgumentException> {
            parseSize("invalid")
        }
        shouldThrow<IllegalArgumentException> {
            parseSize("5XB")
        }
        shouldThrow<IllegalArgumentException> {
            parseSize("GB5")
        }
    }
}
