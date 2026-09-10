package rpc

import kotlinx.serialization.Serializable

@Serializable
data class GutterRangeDto(
    val line1: Int,
    val line2: Int,
    val vcsLine1: Int,
    val vcsLine2: Int
)

@Serializable
data class GutterFileDataDto(
    val filePath: String,
    val ranges: List<GutterRangeDto>,
    /**
     * Full contents of the diff base / HEAD revision. Contents dominate the message size (the
     * ranges are a handful of ints), so the backend includes them only when they changed since the
     * last message for this file within the same subscription; the frontend caches the previous
     * values and reuses them. [contentsIncluded] distinguishes "unchanged, reuse cache" from a
     * genuinely null [headContent].
     */
    val baseContent: String? = null,
    val headContent: String? = null,
    val scopeRanges: List<GutterRangeDto>? = null,
    val scopeDisplayName: String = "",
    val separateGutterRendering: Boolean = false,
    val contentsIncluded: Boolean = true
)
