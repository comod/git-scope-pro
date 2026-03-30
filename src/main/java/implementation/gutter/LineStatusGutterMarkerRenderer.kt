// Adapted from IntelliJ Platform for Git Scope Pro
package implementation.gutter

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.editor.markup.ActiveGutterRenderer
import com.intellij.openapi.editor.markup.LineMarkerRendererEx
import com.intellij.util.ui.JBUI
import settings.GitScopeSettings
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.MouseEvent

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

        // Width is thicker when hovered (matching IDE behavior)
        val normalWidth = JBUI.scale(4)
        val hoveredWidth = JBUI.scale(6)  // Changed from 7 to 6
        val width = if (isHovered) hoveredWidth else normalWidth

        val x: Int
        if (settings.isSeparateGutterRendering) {
            // Separate rendering: paint just to the left of line numbers.
            // annotationsAreaOffset + annotationsAreaWidth == the left edge of the line-number
            // column (public API equivalent of the @ApiInternal lineNumberAreaOffset).
            // Subtract normalWidth so the marker's right edge aligns with the line numbers
            // rather than its left edge, preventing it from covering the first digit.
            // maxOf guards the case where annotationsAreaWidth == 0 (no blame), which would
            // produce a negative x without the clamp.
            x = maxOf(gutter.annotationsAreaOffset, gutter.annotationsAreaOffset + gutter.annotationsAreaWidth - normalWidth)
        } else {
            // Merged rendering: paint at far right of gutter (where IDE VCS markers are)
            // Expand to the left when hovering (x shifts left, width increases)
            val baseX = gutter.whitespaceSeparatorOffset - JBUI.scale(3)  // Moved 1px right (was -4)
            x = if (isHovered) {
                baseX - (hoveredWidth - normalWidth)  // Shift left by 2px when hovering
            } else {
                baseX
            }
        }

        // Arc size for rounded corners
        // Use larger arc for hovered state to make it more visually distinct
        val normalArcSize = JBUI.scale(5)
        val hoveredArcSize = JBUI.scale(7)  // More rounded when hovered
        val arcSize = if (isHovered) hoveredArcSize else normalArcSize

        when (range.type) {
            Range.INSERTED -> {
                // Paint for inserted lines (has current lines, no VCS lines)
                val y1 = editor.logicalPositionToXY(com.intellij.openapi.editor.LogicalPosition(range.line1, 0)).y
                val y2 = editor.logicalPositionToXY(com.intellij.openapi.editor.LogicalPosition(range.line2, 0)).y
                val height = y2 - y1

                // Draw rounded rectangle
                g2d.fillRoundRect(x, y1, width, height, arcSize, arcSize)
            }
            Range.DELETED -> {
                // Paint for deleted lines (no current lines, has VCS lines)
                val y = editor.logicalPositionToXY(com.intellij.openapi.editor.LogicalPosition(range.line1, 0)).y
                val height = JBUI.scale(8)  // Fixed height for deleted marker

                // Draw rounded rectangle at the deletion point
                g2d.fillRoundRect(x, y, width, height, arcSize, arcSize)
            }
            Range.MODIFIED -> {
                // Paint for modified lines (has both current and VCS lines)
                val y1 = editor.logicalPositionToXY(com.intellij.openapi.editor.LogicalPosition(range.line1, 0)).y
                val y2 = editor.logicalPositionToXY(com.intellij.openapi.editor.LogicalPosition(range.line2, 0)).y
                val height = y2 - y1

                // Draw rounded rectangle
                g2d.fillRoundRect(x, y1, width, height, arcSize, arcSize)
            }
        }
        
        // Restore original rendering hints
        g2d.setRenderingHints(oldHints)
    }

    private fun getColorForChangeType(editor: EditorEx, type: Byte): java.awt.Color {
        val scheme = editor.colorsScheme
        return when (type) {
            Range.INSERTED -> scheme.getColor(EditorColors.ADDED_LINES_COLOR) ?: java.awt.Color.GREEN
            Range.DELETED -> scheme.getColor(EditorColors.DELETED_LINES_COLOR) ?: java.awt.Color.RED
            Range.MODIFIED -> scheme.getColor(EditorColors.MODIFIED_LINES_COLOR) ?: java.awt.Color.BLUE
            else -> java.awt.Color.GRAY
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

        // Check if mouse is in our marker x-area
        val settings = GitScopeSettings.getInstance()
        val markerX: Int
        val detectionWidth: Int

        if (settings.isSeparateGutterRendering) {
            // Separate: marker right-edge aligns with line numbers (public API, blame-aware)
            markerX = maxOf(gutter.annotationsAreaOffset, gutter.annotationsAreaOffset + gutter.annotationsAreaWidth - JBUI.scale(4))
            detectionWidth = JBUI.scale(9)  // 4px normal + 2px expansion + margin
        } else {
            // Merged: expands to the left, aligned with IDE
            markerX = gutter.whitespaceSeparatorOffset - JBUI.scale(3)
            detectionWidth = JBUI.scale(9)  // Need to check left side too when it expands
        }

        // Check x bounds (allow detection on both sides for merged mode)
        if (settings.isSeparateGutterRendering) {
            // Separate: check from marker to the right
            if (x !in (markerX - JBUI.scale(1))..(markerX + detectionWidth)) {
                return false
            }
        } else {
            // Merged: check from left (when expanded) to right
            if (x !in (markerX - JBUI.scale(3))..(markerX + detectionWidth)) {
                return false
            }
        }

        // Check if y is over any range
        return ranges.any { range ->
            val y1 = editor.logicalPositionToXY(com.intellij.openapi.editor.LogicalPosition(range.line1, 0)).y
            val y2 = editor.logicalPositionToXY(com.intellij.openapi.editor.LogicalPosition(range.line2, 0)).y
            y in y1..y2
        }
    }

    /**
     * Called by IDE when marker is clicked. Subclasses should implement to show popup.
     */
    override fun doAction(editor: Editor, e: MouseEvent) {
        // Will be implemented by ScopeLineStatusMarkerRenderer
    }
}
