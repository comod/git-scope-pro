package implementation.gutter

import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import com.intellij.openapi.editor.colors.TextAttributesKey
import javax.swing.Icon

/** The "Git Scope" page under Settings | Editor | Color Scheme. */
class ScopeGutterColorSettingsPage : ColorSettingsPage {

    override fun getDisplayName(): String = DISPLAY_NAME

    override fun getIcon(): Icon? = null

    override fun getHighlighter(): SyntaxHighlighter = PlainSyntaxHighlighter()

    override fun getDemoText(): String =
        "Git Scope gutter markers show changes relative to the selected scope target.\n" +
        "Each color defaults to the matching IDE color under Version Control."

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey>? = null

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = emptyArray()

    override fun getColorDescriptors(): Array<ColorDescriptor> = arrayOf(
        ColorDescriptor("Gutter//Added lines", ScopeGutterColors.ADDED_LINES, ColorDescriptor.Kind.BACKGROUND),
        ColorDescriptor("Gutter//Modified lines", ScopeGutterColors.MODIFIED_LINES, ColorDescriptor.Kind.BACKGROUND),
        ColorDescriptor("Gutter//Deleted lines", ScopeGutterColors.DELETED_LINES, ColorDescriptor.Kind.BACKGROUND),
    )

    companion object {
        const val DISPLAY_NAME = "Git Scope"
    }
}
