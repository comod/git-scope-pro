// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Based on com.intellij.openapi.vcs.ex.LineStatusMarkerRenderer from the IntelliJ Platform
// (platform/diff-impl), modified for Git Scope Pro.
package implementation.gutter

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.editor.markup.ActiveGutterRenderer
import com.intellij.openapi.editor.markup.LineMarkerRendererEx
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import settings.GitScopeSettings
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.MouseEvent

/**
 * Returns (x, endX) for the gutter VCS marker area.
 * Delegates to LineStatusMarkerDrawUtil.getGutterArea() via reflection (which uses
 * @ApiStatus.Internal offset methods internally), falling back to a public-API derivation.
 */
internal fun getGutterArea(editor: EditorEx): Pair<Int, Int> {
    // Primary: exact IDE positioning via reflection
    val area = utils.PlatformApiReflection.getGutterArea(editor)
    if (area != null) return Pair(area[0], area[1])

    // Fallback: derive from public APIs (new UI only — old UI was removed in 2024.1)
    val gutter = editor.gutterComponentEx
    val areaWidth = JBUI.scale(4)
    val endX = gutter.whitespaceSeparatorOffset
    return Pair(endX - areaWidth, endX)
}

/**
 * Returns true if the pixel y falls within the painted area of [range].
 * DELETED ranges have line1 == line2 so y1 == y2; they are given the same
 * fixed height (8 px) used when painting them.
 */
internal fun rangeContainsY(editor: Editor, range: Range, y: Int): Boolean {
    val y1 = editor.logicalPositionToXY(LogicalPosition(range.line1, 0)).y
    val y2 = if (range.type == Range.DELETED) y1 + JBUI.scale(8)
             else editor.logicalPositionToXY(LogicalPosition(range.line2, 0)).y
    return y in y1..y2
}

/**
 * Renders line status markers in the editor gutter.
 * Paints colored rectangles (green/blue/red) for different change types.
 * Implements ActiveGutterRenderer to handle mouse interactions.
 */
abstract class LineStatusGutterMarkerRenderer : LineMarkerRendererEx, ActiveGutterRenderer {

    /**
     * Returns the list of ranges to be painted in the gutter.
     */
    abstract fun getPaintedRanges(): List<Range>?
    
    /**
     * Returns the currently hovered range, if any.
     */
    open fun getHoveredRange(): Range? = null

    override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
        // Mirror LineStatusTracker.isAvailableAt(): skip when the line marker area is hidden
        if (!editor.settings.isLineMarkerAreaShown) return
        val ranges = getPaintedRanges() ?: return
        if (ranges.isEmpty()) return

        paintDefault(editor as EditorEx, g, ranges)
    }

    override fun getPosition() = LineMarkerRendererEx.Position.LEFT

    /**
     * Default painting logic for line status markers.
     * Paints colored rectangles in the gutter for each range.
     */
    private fun paintDefault(editor: EditorEx, g: Graphics, ranges: List<Range>) {
        val gutter = editor.gutterComponentEx

        for (range in ranges) {
            paintRange(editor, g, gutter, range)
        }
    }

    private fun paintRange(editor: EditorEx, g: Graphics, gutter: EditorGutterComponentEx, range: Range) {
        val color = getColorForChangeType(editor, range.type)
        
        // Enable anti-aliasing for smooth rounded corners
        val g2d = g as Graphics2D
        val oldHints = g2d.renderingHints
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2d.color = color

        // Calculate x position based on settings
        val settings = GitScopeSettings.getInstance()

        // Check if this range is being hovered
        val hoveredRange = getHoveredRange()
        val isHovered = (hoveredRange != null &&
                        hoveredRange.line1 == range.line1 &&
                        hoveredRange.line2 == range.line2 &&
                        hoveredRange.type == range.type)

        // lineMarkerAreaWidth is @ApiStatus.Internal on EditorGutterComponentEx.
        // getGutterArea() already reflects it: areaWidth == gutterArea.second - gutterArea.first.
        // Using it here means normal and hover thickness are always platform-consistent
        // (correct on both Windows and Linux without hardcoded pixel guesses).
        val gutterArea = getGutterArea(editor)
        val areaWidth = gutterArea.second - gutterArea.first
        // Hover expands by (areaWidth - 1 px), matching the IDE's own VCS marker behaviour.
        val hoverExpansion = maxOf(areaWidth - JBUI.scale(1), JBUI.scale(1))

        val x: Int
        val width: Int
        if (settings.isSeparateGutterRendering) {
            // Separate rendering: paint just to the left of line numbers.
            // annotationsAreaOffset + annotationsAreaWidth == the left edge of the line-number
            // column (public API equivalent of the @ApiInternal lineNumberAreaOffset).
            // Subtract the marker width so the marker's right edge aligns with the line numbers.
            // maxOf guards the case where annotationsAreaWidth == 0 (no blame).
            width = if (isHovered) areaWidth + hoverExpansion else areaWidth
            x = maxOf(gutter.annotationsAreaOffset, gutter.annotationsAreaOffset + gutter.annotationsAreaWidth - areaWidth)
        } else {
            // Merged rendering: mirror LineStatusMarkerDrawUtil.getGutterArea()
            // so our markers align exactly with the IDE's own VCS change bars.
            // IDE expands left on hover, keeping right edge fixed at gutterArea.second.
            x = if (isHovered) gutterArea.first - hoverExpansion else gutterArea.first
            width = gutterArea.second - x
        }

        // Arc size for rounded corners
        // Use larger arc for hovered state to make it more visually distinct
        val normalArcSize = JBUI.scale(5)
        val hoveredArcSize = JBUI.scale(7)  // More rounded when hovered
        val arcSize = if (isHovered) hoveredArcSize else normalArcSize

        val bounds = rangeYBounds(editor, range)
        g2d.fillRoundRect(x, bounds.first, width, bounds.last - bounds.first, arcSize, arcSize)
        
        // Restore original rendering hints
        g2d.setRenderingHints(oldHints)
    }

    private fun rangeYBounds(editor: Editor, range: Range): IntRange {
        val y1 = editor.logicalPositionToXY(LogicalPosition(range.line1, 0)).y
        val y2 = if (range.type == Range.DELETED) y1 + JBUI.scale(8)
                 else editor.logicalPositionToXY(LogicalPosition(range.line2, 0)).y
        return y1..y2
    }

    private fun getColorForChangeType(editor: EditorEx, type: Byte): java.awt.Color {
        val scheme = editor.colorsScheme
        return when (type) {
            Range.INSERTED -> scheme.getColor(EditorColors.ADDED_LINES_COLOR) ?: JBColor.GREEN
            Range.DELETED -> scheme.getColor(EditorColors.DELETED_LINES_COLOR) ?: JBColor.RED
            Range.MODIFIED -> scheme.getColor(EditorColors.MODIFIED_LINES_COLOR) ?: JBColor.BLUE
            else -> JBColor.GRAY
        }
    }

    // ActiveGutterRenderer methods for mouse interaction

    /**
     * Called by IDE to check if mouse event is over a marker and should show hand cursor.
     * Returns true when mouse is over one of our ranges.
     */
    override fun canDoAction(editor: Editor, e: MouseEvent): Boolean {
        val ranges = getPaintedRanges() ?: return false
        if (ranges.isEmpty()) return false

        val editorEx = editor as? EditorEx ?: return false
        val gutter = editorEx.gutterComponentEx
        val x = e.x
        val y = e.y

        // Check if mouse is in our marker x-area (must match paint logic exactly)
        val settings = GitScopeSettings.getInstance()
        val gutterArea = getGutterArea(editorEx)
        val areaWidth = gutterArea.second - gutterArea.first
        val hoverExpansion = maxOf(areaWidth - JBUI.scale(1), JBUI.scale(1))

        if (settings.isSeparateGutterRendering) {
            val markerX = maxOf(gutter.annotationsAreaOffset, gutter.annotationsAreaOffset + gutter.annotationsAreaWidth - areaWidth)
            if (x !in (markerX - JBUI.scale(1))..(markerX + areaWidth + hoverExpansion)) {
                return false
            }
        } else {
            // Merged: use same gutter area as paint, cover full hover extent
            if (x !in (gutterArea.first - hoverExpansion - JBUI.scale(1))..(gutterArea.second + JBUI.scale(1))) {
                return false
            }
        }

        // Check if y is over any range.
        // DELETED ranges have line1 == line2, so y1 == y2 — use the painted height instead.
        return ranges.any { range -> y in rangeYBounds(editor, range) }
    }

    /**
     * Called by IDE when marker is clicked. Subclasses should implement to show popup.
     */
    override fun doAction(editor: Editor, e: MouseEvent) {
        // Subclasses override to show a diff/rollback popup for the clicked range.
    }
}
