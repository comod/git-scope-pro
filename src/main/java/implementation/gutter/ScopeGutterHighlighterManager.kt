// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Based on com.intellij.openapi.vcs.ex.LineStatusMarkerRenderer from the IntelliJ Platform
// (platform/diff-impl), modified for Git Scope Pro.
package implementation.gutter

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.LineMarkerRenderer
import com.intellij.openapi.editor.markup.MarkupEditorFilterFactory
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.util.concurrency.annotations.RequiresEdt
import system.Defs
import java.awt.Color
import java.awt.event.MouseEvent

/**
 * Manages the gutter range highlighter, error stripe marks, and gutter repaint for scope changes.
 * Analogous to com.intellij.openapi.vcs.ex.LineStatusMarkerRenderer in the IntelliJ Platform.
 */
internal class ScopeGutterHighlighterManager(
    private val project: Project,
    private val document: Document,
    private val rangesProvider: () -> List<Range>,
    private val hoveredRangeProvider: () -> Range?,
    private val onHoverChanged: (newHoveredRange: Range?) -> Unit,
    private val onDoAction: (editor: EditorEx, range: Range, e: MouseEvent) -> Unit
) {
    companion object {
        private val LOG: Logger = Defs.getLogger(ScopeGutterHighlighterManager::class.java)
        const val GUTTER_LAYER = 6
    }

    @Volatile private var gutterHighlighter: RangeHighlighter? = null
    private val errorStripeHighlighters: MutableList<RangeHighlighter> = mutableListOf()
    private var lastMouseY: Int = -1

    @RequiresEdt
    fun update(ranges: List<Range>) {
        if (gutterHighlighter == null || !gutterHighlighter!!.isValid) {
            createGutterHighlighter()
        }
        updateErrorStripeMarks(ranges)
        repaintGutter()
    }

    @RequiresEdt
    fun clear() {
        val markupModel = DocumentMarkupModel.forDocument(document, project, false) as? MarkupModelEx
        gutterHighlighter?.let { highlighter ->
            try {
                if (highlighter.isValid) markupModel?.removeHighlighter(highlighter)
            } catch (e: Exception) {
                LOG.warn("Error removing highlighter", e)
            }
        }
        gutterHighlighter = null
        for (highlighter in errorStripeHighlighters) {
            try {
                if (highlighter.isValid) markupModel?.removeHighlighter(highlighter)
            } catch (e: Exception) {
                LOG.warn("Error removing error stripe highlighter", e)
            }
        }
        errorStripeHighlighters.clear()
    }

    @RequiresEdt
    fun repaintGutter() {
        try {
            for (editor in EditorFactory.getInstance().getEditors(document, project)) {
                if (editor is EditorEx) editor.gutterComponentEx.repaint()
            }
        } catch (e: Exception) {
            LOG.warn("Error repainting gutter", e)
        }
    }

    @RequiresEdt
    private fun createGutterHighlighter() {
        gutterHighlighter?.let { old ->
            try {
                if (old.isValid) {
                    (DocumentMarkupModel.forDocument(document, project, false) as? MarkupModelEx)
                        ?.removeHighlighter(old)
                }
            } catch (e: Exception) {
                LOG.warn("Error removing old highlighter", e)
            }
        }
        try {
            val markupModel = DocumentMarkupModel.forDocument(document, project, true) as MarkupModelEx
            gutterHighlighter = markupModel.addRangeHighlighterAndChangeAttributes(
                null, 0, document.textLength,
                GUTTER_LAYER, HighlighterTargetArea.LINES_IN_RANGE, false
            ) { rh: RangeHighlighterEx ->
                rh.lineMarkerRenderer = createGutterMarkerRenderer()
                rh.isGreedyToLeft = true
                rh.isGreedyToRight = true
                rh.editorFilter = MarkupEditorFilterFactory.createIsNotDiffFilter()
            }
        } catch (e: Exception) {
            LOG.error("Error creating gutter highlighter", e)
        }
    }

    private fun createGutterMarkerRenderer(): LineMarkerRenderer {
        return object : LineStatusGutterMarkerRenderer() {
            override fun getPaintedRanges(): List<Range>? =
                rangesProvider().ifEmpty { null }

            override fun getHoveredRange(): Range? =
                hoveredRangeProvider()

            override fun canDoAction(editor: Editor, e: MouseEvent): Boolean {
                val result = super.canDoAction(editor, e)
                val y = e.y

                val newHoveredRange = if (result) {
                    rangesProvider().firstOrNull { range -> rangeContainsY(editor, range, y) }
                } else null

                if (newHoveredRange != hoveredRangeProvider()) {
                    onHoverChanged(newHoveredRange)
                    lastMouseY = y
                    repaintGutter()
                } else if (result && lastMouseY != y) {
                    lastMouseY = y
                }

                return result
            }

            override fun doAction(editor: Editor, e: MouseEvent) {
                e.consume()
                val editorEx = editor as? EditorEx ?: return
                val y = e.y
                val clickedRange = rangesProvider().firstOrNull { range -> rangeContainsY(editor, range, y) }
                if (clickedRange != null) {
                    onDoAction(editorEx, clickedRange, e)
                }
            }
        }
    }

    @RequiresEdt
    private fun updateErrorStripeMarks(ranges: List<Range>) {
        val markupModel = DocumentMarkupModel.forDocument(document, project, false) as? MarkupModelEx ?: return

        for (highlighter in errorStripeHighlighters) {
            try {
                if (highlighter.isValid) markupModel.removeHighlighter(highlighter)
            } catch (e: Exception) {
                LOG.warn("Error removing error stripe highlighter", e)
            }
        }
        errorStripeHighlighters.clear()

        if (ranges.isEmpty()) return

        val markupModelForCreate = DocumentMarkupModel.forDocument(document, project, true) as MarkupModelEx

        for (range in ranges) {
            try {
                val color = getErrorStripeColor(range.type) ?: continue

                val startLine = range.line1.coerceIn(0, document.lineCount - 1)
                val endLine = if (range.type == Range.DELETED) startLine
                             else (range.line2 - 1).coerceIn(startLine, document.lineCount - 1)

                val startOffset = document.getLineStartOffset(startLine)
                val endOffset = document.getLineEndOffset(endLine)

                val highlighter = markupModelForCreate.addRangeHighlighterAndChangeAttributes(
                    null, startOffset, endOffset,
                    GUTTER_LAYER, HighlighterTargetArea.LINES_IN_RANGE, false
                ) { rh: RangeHighlighterEx ->
                    rh.setErrorStripeMarkColor(color)
                    rh.isThinErrorStripeMark = true
                    rh.editorFilter = MarkupEditorFilterFactory.createIsNotDiffFilter()
                }
                errorStripeHighlighters.add(highlighter)
            } catch (e: Exception) {
                LOG.warn("Error creating error stripe highlighter for range $range", e)
            }
        }
    }

    private fun getErrorStripeColor(type: Byte): Color? {
        val editor = EditorFactory.getInstance().getEditors(document, project).firstOrNull() as? EditorEx ?: return null
        val scheme = editor.colorsScheme
        return when (type) {
            Range.INSERTED -> scheme.getColor(EditorColors.ADDED_LINES_COLOR) ?: JBColor.GREEN
            Range.DELETED  -> scheme.getColor(EditorColors.DELETED_LINES_COLOR) ?: JBColor.RED
            Range.MODIFIED -> scheme.getColor(EditorColors.MODIFIED_LINES_COLOR) ?: JBColor.BLUE
            else -> null
        }
    }
}
