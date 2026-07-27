// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Based on com.intellij.openapi.vcs.ex.LineStatusMarkerRendererWithPopup from the IntelliJ Platform
// (platform/diff-impl), modified for Git Scope Pro.
package implementation.gutter

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import system.Defs

/**
 * Handles showing diffs for specific ranges in the Git Scope gutter.
 */
class ScopeDiffViewer(
    private val project: Project,
    private val document: Document,
    private val file: VirtualFile
) {
    companion object {
        private val LOG: Logger = Defs.getLogger(ScopeDiffViewer::class.java)
    }

    @Volatile
    private var vcsBaseContent: String? = null

    /**
     * Updates the base content from VCS.
     * Must be called before showing diffs.
     */
    fun setVcsBaseContent(baseContent: String?) {
        vcsBaseContent = baseContent
    }

    /**
     * Shows a diff dialog for the specified range.
     */
    fun showDiffForRange(editor: EditorEx, range: Range) {
        try {
            // Get the current content for the range
            val currentContent = getCurrentContentForRange(range)

            // Get the VCS base content for the range
            val vcsContent = getVcsContentForRange(range)

            // Create diff content
            val contentFactory = DiffContentFactory.getInstance()
            val leftContent = contentFactory.create(project, vcsContent, file.fileType)
            val rightContent = contentFactory.create(project, currentContent, file.fileType)

            // Create diff request
            val request = SimpleDiffRequest(
                "Git Scope Change",
                leftContent,
                rightContent,
                "Base (Scope)",
                "Current"
            )

            // Show diff in a dialog
            DiffManager.getInstance().showDiff(project, request)
        } catch (e: Exception) {
            LOG.error("Error showing diff for range", e)
        }
    }

    private fun getCurrentContentForRange(range: Range): String {
        val lines = document.text.split("\n")
        return when (range.type) {
            Range.DELETED -> extractLines(lines, range.line1 - 1, range.line1)
            else          -> extractLinesWithContext(lines, range.line1, range.line2)
        }
    }

    /**
     * Gets the VCS base content for the specified range.
     * Used by both diff viewer and rollback functionality.
     */
    fun getVcsContentForRange(range: Range, includeContext: Boolean = true): String {
        val baseContent = vcsBaseContent
        if (baseContent == null) {
            if (range.type == Range.DELETED || range.type == Range.MODIFIED) LOG.warn("VCS base content not available")
            return ""
        }
        val lines = baseContent.split("\n")
        return when (range.type) {
            Range.INSERTED           -> if (includeContext) extractLines(lines, range.vcsLine1 - 1, range.vcsLine1) else ""
            Range.DELETED, Range.MODIFIED ->
                if (includeContext) extractLinesWithContext(lines, range.vcsLine1, range.vcsLine2)
                else                     extractLines(lines, range.vcsLine1, range.vcsLine2)
            else -> ""
        }
    }

    private fun extractLines(lines: List<String>, from: Int, to: Int): String =
        if (from >= 0 && to <= lines.size) lines.subList(from, to).joinToString("\n") else ""

    private fun extractLinesWithContext(lines: List<String>, from: Int, to: Int): String {
        val start = maxOf(0, from - 1)
        val end = minOf(to + 1, lines.size)
        return if (start < lines.size) lines.subList(start, end).joinToString("\n") else ""
    }
}
