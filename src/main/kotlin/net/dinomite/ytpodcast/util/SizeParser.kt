package net.dinomite.ytpodcast.util

/**
 * Parses a human-readable size string into bytes.
 *
 * Supports formats like:
 * - "1024" or "1024B" - bytes
 * - "5KB" or "5 KB" - kilobytes
 * - "100MB" or "100 MB" - megabytes
 * - "5GB" or "5 GB" - gigabytes
 * - "1TB" or "1 TB" - terabytes
 *
 * Case insensitive. "0" or "0B" means unlimited (returns 0).
 *
 * @param size The size string to parse
 * @return The size in bytes, or 0 for unlimited
 * @throws IllegalArgumentException if the format is invalid
 */
fun parseSize(size: String): Long {
    val pattern = """(\d+)\s*(B|KB|MB|GB|TB)?""".toRegex(RegexOption.IGNORE_CASE)
    val match = pattern.matchEntire(size.trim())
        ?: throw IllegalArgumentException("Invalid size format: $size")

    val amount = match.groupValues[1].toLong()
    val unit = match.groupValues[2].uppercase().ifEmpty { "B" }

    return when (unit) {
        "B" -> amount
        "KB" -> amount * 1024
        "MB" -> amount * 1024 * 1024
        "GB" -> amount * 1024 * 1024 * 1024
        "TB" -> amount * 1024 * 1024 * 1024 * 1024
        else -> throw IllegalArgumentException("Unknown unit: $unit")
    }
}
