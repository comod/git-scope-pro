// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Adapted from IntelliJ Platform for Git Scope Pro
package implementation.gutter

import com.intellij.diff.comparison.ComparisonManager
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.comparison.DiffTooBigException
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.DumbProgressIndicator

/**
 * Creates ranges by comparing current document with VCS base revision.
 * Uses IntelliJ's diff utilities to compute line-level differences.
 */
object RangesBuilder {

    fun createRanges(current: Document, vcs: Document): List<Range> {
        return createRanges(current.immutableCharSequence, vcs.immutableCharSequence)
    }

    fun createRanges(current: CharSequence, vcs: CharSequence): List<Range> {
        return try {
            val fragments = ComparisonManager.getInstance().compareLines(
                vcs, current, ComparisonPolicy.DEFAULT, DumbProgressIndicator.INSTANCE
            )
            fragments.map { fragment ->
                Range(
                    line1 = fragment.startLine2,
                    line2 = fragment.endLine2,
                    vcsLine1 = fragment.startLine1,
                    vcsLine2 = fragment.endLine1
                )
            }
        } catch (e: DiffTooBigException) {
            emptyList()
        }
    }
}
