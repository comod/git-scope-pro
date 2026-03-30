// Adapted from IntelliJ Platform for Git Scope Pro
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
        return when (range.type) {
            Range.DELETED -> {
                // For deleted ranges, get context line above (if available)
                val text = document.text
                val lines = text.split("\n")

                // Get one line above for context
                if (range.line1 > 0 && range.line1 <= lines.size) {
                    lines[range.line1 - 1]
                } else {
                    ""
                }
            }
            else -> {
                // Get lines from current document with context
                val text = document.text
                val lines = text.split("\n")

                // Add one line above and one line below for context
                val startLine = maxOf(0, range.line1 - 1)
                val endLine = minOf(range.line2 + 1, lines.size)

                if (startLine < lines.size) {
                    lines.subList(startLine, endLine).joinToString("\n")
                } else {
                    ""
                }
            }
        }
    }

    /**
     * Gets the VCS base content for the specified range.
     * Used by both diff viewer and rollback functionality.
     */
    fun getVcsContentForRange(range: Range, includeContext: Boolean = true): String {
        return when (range.type) {
            Range.INSERTED -> {
                // For inserted ranges, there's nothing in VCS to restore (they were added locally)
                val baseContent = vcsBaseContent
                if (baseContent != null && includeContext) {
                    val lines = baseContent.split("\n")

                    // Get one line above for context
                    if (range.vcsLine1 > 0 && range.vcsLine1 <= lines.size) {
                        lines[range.vcsLine1 - 1]
                    } else {
                        ""
                    }
                } else {
                    ""
                }
            }
            Range.DELETED -> {
                // For deleted ranges, restore the deleted content
                val baseContent = vcsBaseContent
                if (baseContent != null) {
                    val lines = baseContent.split("\n")

                    if (includeContext) {
                        // With context: one line above and below
                        val startLine = maxOf(0, range.vcsLine1 - 1)
                        val endLine = minOf(range.vcsLine2 + 1, lines.size)
                        if (startLine < lines.size) {
                            lines.subList(startLine, endLine).joinToString("\n")
                        } else {
                            ""
                        }
                    } else {
                        // Without context: exact range only
                        if (range.vcsLine1 >= 0 && range.vcsLine2 <= lines.size) {
                            lines.subList(range.vcsLine1, range.vcsLine2).joinToString("\n")
                        } else {
                            ""
                        }
                    }
                } else {
                    LOG.warn("VCS base content not available")
                    ""
                }
            }
            Range.MODIFIED -> {
                // For modified ranges, get the base version
                val baseContent = vcsBaseContent
                if (baseContent != null) {
                    val lines = baseContent.split("\n")

                    if (includeContext) {
                        // With context: one line above and below
                        val startLine = maxOf(0, range.vcsLine1 - 1)
                        val endLine = minOf(range.vcsLine2 + 1, lines.size)
                        if (startLine < lines.size) {
                            lines.subList(startLine, endLine).joinToString("\n")
                        } else {
                            ""
                        }
                    } else {
                        // Without context: exact range only
                        if (range.vcsLine1 >= 0 && range.vcsLine2 <= lines.size) {
                            lines.subList(range.vcsLine1, range.vcsLine2).joinToString("\n")
                        } else {
                            ""
                        }
                    }
                } else {
                    LOG.warn("VCS base content not available")
                    ""
                }
            }
            else -> ""
        }
    }
}
