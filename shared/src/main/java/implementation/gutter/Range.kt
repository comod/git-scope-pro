// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Based on com.intellij.openapi.vcs.ex.Range from the IntelliJ Platform
// (platform/diff-impl), modified for Git Scope Pro.
package implementation.gutter

/**
 * Represents a range of changed lines between current document and VCS base revision.
 * Stores half-open intervals [start, end).
 */
open class Range(
    val line1: Int,           // Current document start line
    val line2: Int,           // Current document end line (exclusive)
    val vcsLine1: Int,        // VCS base start line
    val vcsLine2: Int,        // VCS base end line (exclusive)
    val innerRanges: List<InnerRange>?
) {
    constructor(line1: Int, line2: Int, vcsLine1: Int, vcsLine2: Int) : this(line1, line2, vcsLine1, vcsLine2, null)
    constructor(range: Range) : this(range.line1, range.line2, range.vcsLine1, range.vcsLine2)

    class InnerRange(val line1: Int, val line2: Int, val type: Byte)

    fun hasLines(): Boolean = line1 != line2
    fun hasVcsLines(): Boolean = vcsLine1 != vcsLine2

    val type: Byte
        get() {
            if (!hasLines() && !hasVcsLines()) return MODIFIED
            if (!hasLines()) return DELETED
            if (!hasVcsLines()) return INSERTED
            return MODIFIED
        }

    override fun toString(): String = "[$vcsLine1, $vcsLine2) - [$line1, $line2)"

    companion object {
        const val EQUAL: Byte = 0    // whitespace-only difference (not displayed)
        const val MODIFIED: Byte = 1
        const val INSERTED: Byte = 2
        const val DELETED: Byte = 3
    }
}
