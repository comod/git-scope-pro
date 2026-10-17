package implementation.gutter

import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import settings.GitScopeSettings
import java.awt.Color

/**
 * Editor color scheme keys for the Git Scope gutter markers (Editor | Color Scheme | Git Scope).
 * Each key falls back to the IDE's own VCS change color, so the markers look like the IDE's
 * until a scheme (user-edited or bundled by a theme) defines its own.
 */
object ScopeGutterColors {
    @JvmField
    val ADDED_LINES: ColorKey =
        ColorKey.createColorKeyWithFallback("GIT_SCOPE_ADDED_LINES_COLOR", EditorColors.ADDED_LINES_COLOR)

    @JvmField
    val MODIFIED_LINES: ColorKey =
        ColorKey.createColorKeyWithFallback("GIT_SCOPE_MODIFIED_LINES_COLOR", EditorColors.MODIFIED_LINES_COLOR)

    @JvmField
    val DELETED_LINES: ColorKey =
        ColorKey.createColorKeyWithFallback("GIT_SCOPE_DELETED_LINES_COLOR", EditorColors.DELETED_LINES_COLOR)

    /** Scheme color for [type], without the fade applied (used for the error stripe). */
    fun getColor(scheme: EditorColorsScheme, type: Byte): Color? = when (type) {
        Range.INSERTED -> scheme.getColor(ADDED_LINES) ?: JBColor.GREEN
        Range.MODIFIED -> scheme.getColor(MODIFIED_LINES) ?: JBColor.BLUE
        Range.DELETED -> scheme.getColor(DELETED_LINES) ?: JBColor.RED
        else -> null
    }

    /** Scheme color for [type] with the "Fade Git Scope gutter markers" opacity applied. */
    fun getGutterColor(scheme: EditorColorsScheme, type: Byte): Color {
        val color = getColor(scheme, type) ?: JBColor.GRAY
        val settings = GitScopeSettings.getInstance()
        if (!settings.isFadeGutterMarkers) return color
        val opacity = settings.gutterMarkerOpacity
            .coerceIn(GitScopeSettings.MIN_GUTTER_MARKER_OPACITY, GitScopeSettings.MAX_GUTTER_MARKER_OPACITY)
        return ColorUtil.withAlpha(color, opacity / 100.0)
    }
}
