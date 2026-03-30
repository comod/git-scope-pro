// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Adapted from IntelliJ Platform for Git Scope Pro
package implementation.gutter

import com.intellij.diff.comparison.ByLine
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.comparison.ComparisonUtil
import com.intellij.diff.comparison.DiffTooBigException
import com.intellij.diff.comparison.expand
import com.intellij.diff.comparison.iterables.DiffIterableUtil
import com.intellij.diff.comparison.iterables.FairDiffIterable
import com.intellij.diff.tools.util.text.LineOffsets
import com.intellij.diff.tools.util.text.LineOffsetsUtil
import com.intellij.diff.util.DiffRangeUtil
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.DumbProgressIndicator

/**
 * Creates ranges by comparing current document with VCS base revision.
 * Uses IntelliJ's diff utilities to compute line-level differences.
 */
object RangesBuilder {

    fun createRanges(current: Document, vcs: Document): List<Range> {
        return createRanges(
            current.immutableCharSequence,
            vcs.immutableCharSequence,
            LineOffsetsUtil.create(current),
            LineOffsetsUtil.create(vcs)
        )
    }

    fun createRanges(current: CharSequence, vcs: CharSequence): List<Range> {
        return createRanges(
            current,
            vcs,
            LineOffsetsUtil.create(current),
            LineOffsetsUtil.create(vcs)
        )
    }

    private fun createRanges(
        current: CharSequence,
        vcs: CharSequence,
        currentLineOffsets: LineOffsets,
        vcsLineOffsets: LineOffsets
    ): List<Range> {
        // Extract all lines from the documents
        val vcsLines = DiffRangeUtil.getLines(vcs, vcsLineOffsets, 0, vcsLineOffsets.lineCount)
        val currentLines = DiffRangeUtil.getLines(current, currentLineOffsets, 0, currentLineOffsets.lineCount)

        val iterable = compareLines(vcsLines, currentLines)
        return createRanges(iterable)
    }

    private fun compareLines(vcsLines: List<String>, currentLines: List<String>): FairDiffIterable {
        return safeCompareLines(vcsLines, currentLines, ComparisonPolicy.DEFAULT)
    }

    private fun safeCompareLines(
        lines1: List<String>,
        lines2: List<String>,
        comparisonPolicy: ComparisonPolicy
    ): FairDiffIterable {
        return tryCompareLines(lines1, lines2, comparisonPolicy)
            ?: fastCompareLines(lines1, lines2, comparisonPolicy)
    }

    private fun tryCompareLines(
        lines1: List<String>,
        lines2: List<String>,
        comparisonPolicy: ComparisonPolicy
    ): FairDiffIterable? {
        return try {
            ByLine.compare(lines1, lines2, comparisonPolicy, DumbProgressIndicator.INSTANCE)
        } catch (e: DiffTooBigException) {
            null
        }
    }

    private fun fastCompareLines(
        lines1: List<String>,
        lines2: List<String>,
        comparisonPolicy: ComparisonPolicy
    ): FairDiffIterable {
        val range = expand(
            lines1, lines2,
            0, 0,
            lines1.size, lines2.size
        ) { line1, line2 -> ComparisonUtil.isEquals(line1, line2, comparisonPolicy) }

        val ranges = if (range.isEmpty) emptyList() else listOf(range)
        return DiffIterableUtil.fair(DiffIterableUtil.create(ranges, lines1.size, lines2.size))
    }

    private fun createRanges(iterable: FairDiffIterable): List<Range> {
        val ranges = mutableListOf<Range>()
        iterable.iterateChanges().forEach { change ->
            ranges.add(Range(
                line1 = change.start2,      // current start
                line2 = change.end2,        // current end
                vcsLine1 = change.start1,   // vcs start
                vcsLine2 = change.end1      // vcs end
            ))
        }
        return ranges
    }
}
