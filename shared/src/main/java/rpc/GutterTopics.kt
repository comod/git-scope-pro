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
    val baseContent: String,
    val headContent: String? = null,
    val scopeRanges: List<GutterRangeDto>? = null,
    val scopeDisplayName: String = "",
    val separateGutterRendering: Boolean = false
)
