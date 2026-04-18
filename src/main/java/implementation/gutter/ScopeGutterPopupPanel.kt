// Copyright 2000-2026 Magnus Wållberg and contributors. Use of this source code is governed by the GNU GPL v3.0 license.
package implementation.gutter

import com.intellij.diff.comparison.ComparisonManager
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.util.DiffDrawUtil
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.TextDiffType
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.progress.DumbProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ColorUtil
import com.intellij.ui.EditorTextField
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import system.Defs
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.Point
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * Builds and manages the gutter diff popup, toolbar actions, and associated editor highlights.
 * Analogous to com.intellij.openapi.vcs.ex.LineStatusMarkerPopupPanel in the IntelliJ Platform.
 */
internal class ScopeGutterPopupPanel(
    private val project: Project,
    private val document: Document,
    private val file: VirtualFile,
    private val diffViewer: ScopeDiffViewer,
    private val allRangesProvider: () -> List<Range>
) {
    companion object {
        private val LOG: Logger = Defs.getLogger(ScopeGutterPopupPanel::class.java)
    }

    private var activePopup: com.intellij.openapi.ui.popup.JBPopup? = null

    fun show(editor: EditorEx, range: Range, e: MouseEvent) {
        // Close any existing popup before opening a new one
        activePopup?.cancel()

        // Sort ranges once
        val sortedRanges = allRangesProvider().sortedBy { it.line1 }

        // Use a holder to track current index that can be updated
        var currentIndex = sortedRanges.indexOf(range)

        // Variable to hold the popup reference so actions can close it
        var popupRef: com.intellij.openapi.ui.popup.JBPopup? = null

        // Popup lifetime disposable — created early so editor highlights can be tied to it.
        val popupDisposable = Disposer.newDisposable("ScopeGutterPopup")

        // Child disposable for per-range editor highlights; replaced on prev/next navigation.
        var editorHighlightsDisposable = Disposer.newDisposable("ScopeEditorHighlights")
        Disposer.register(popupDisposable, editorHighlightsDisposable)

        // Installs (or replaces) the editor highlights for [newRange].
        fun updateEditorHighlights(newRange: Range) {
            Disposer.dispose(editorHighlightsDisposable)
            editorHighlightsDisposable = Disposer.newDisposable("ScopeEditorHighlights")
            Disposer.register(popupDisposable, editorHighlightsDisposable)
            installEditorHighlights(editor, newRange, editorHighlightsDisposable)
        }

        // Create inline diff preview (like IDE VCS gutter popup)
        val contentPanel = JPanel(BorderLayout())

        // Holder for the diff panel that can be updated
        var diffPanelHolder: JPanel? = null

        // Function to update the diff panel
        fun updateDiffPanel(newRange: Range) {
            diffPanelHolder?.let { contentPanel.remove(it) }
            val newDiffPanel = createInlineDiffPanel(editor, newRange)
            diffPanelHolder = newDiffPanel
            contentPanel.add(newDiffPanel, BorderLayout.CENTER)
            contentPanel.revalidate()
            contentPanel.repaint()
            popupRef?.let { popup ->
                popup.size = contentPanel.preferredSize
            }
        }

        // Create toolbar with action buttons (IDE VCS gutter style)
        val actionGroup = DefaultActionGroup().apply {
            // Previous Change action
            add(object : AnAction("Previous Change", "Go to previous change", AllIcons.Actions.PreviousOccurence) {
                override fun actionPerformed(event: AnActionEvent) {
                    if (currentIndex > 0) {
                        currentIndex--
                        val previousRange = sortedRanges[currentIndex]
                        editor.caretModel.moveToLogicalPosition(LogicalPosition(previousRange.line1, 0))
                        editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
                        updateDiffPanel(previousRange)
                        updateEditorHighlights(previousRange)
                    }
                }

                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = currentIndex > 0
                }
            })

            // Next Change action
            add(object : AnAction("Next Change", "Go to next change", AllIcons.Actions.NextOccurence) {
                override fun actionPerformed(event: AnActionEvent) {
                    if (currentIndex >= 0 && currentIndex < sortedRanges.size - 1) {
                        currentIndex++
                        val nextRange = sortedRanges[currentIndex]
                        editor.caretModel.moveToLogicalPosition(LogicalPosition(nextRange.line1, 0))
                        editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
                        updateDiffPanel(nextRange)
                        updateEditorHighlights(nextRange)
                    }
                }

                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = currentIndex >= 0 && currentIndex < sortedRanges.size - 1
                }
            })

            // Rollback action
            add(object : AnAction("Rollback Lines", "Rollback changes to base version", AllIcons.Actions.Rollback) {
                override fun actionPerformed(event: AnActionEvent) {
                    popupRef?.cancel()
                    rollbackRange(editor, sortedRanges[currentIndex])
                }
            })

            // Show Diff action
            add(object : AnAction("Show Diff", "Show diff for this change", AllIcons.Actions.Diff) {
                override fun actionPerformed(event: AnActionEvent) {
                    popupRef?.cancel()
                    diffViewer.showDiffForRange(editor, sortedRanges[currentIndex])
                }
            })

            // Copy action (only for non-deleted ranges)
            if (range.type != Range.DELETED) {
                add(object : AnAction("Copy", "Copy change to clipboard", AllIcons.Actions.Copy) {
                    override fun actionPerformed(event: AnActionEvent) {
                        popupRef?.cancel()
                        copyRangeToClipboard(editor, sortedRanges[currentIndex])
                    }
                })
            }

            // Scope name label — shows the active diff base (e.g. "master", "HEAD~2")
            val scopeName = try {
                project.getService(service.ViewService::class.java)?.getCurrent()?.getDisplayName() ?: ""
            } catch (_: Exception) { "" }
            if (scopeName.isNotEmpty()) {
                addSeparator()
                add(object : AnAction(), com.intellij.openapi.actionSystem.ex.CustomComponentAction {
                    override fun createCustomComponent(presentation: Presentation, place: String): javax.swing.JComponent {
                        val label = JBLabel(scopeName)
                        label.foreground = UIUtil.getContextHelpForeground()
                        label.border = JBUI.Borders.empty(0, 4, 0, 8)
                        return label
                    }
                    override fun actionPerformed(e: AnActionEvent) {}
                    override fun getActionUpdateThread() = ActionUpdateThread.EDT
                })
            }
        }

        // Create toolbar-style popup matching IDE VCS gutter popup
        val toolbar = ActionManager.getInstance().createActionToolbar(
            "GitScopeGutterPopup",
            actionGroup,
            true  // Horizontal orientation
        )
        toolbar.targetComponent = editor.component

        val toolbarComponent = toolbar.component
        toolbarComponent.border = JBUI.Borders.empty(2)

        // Add toolbar to the top of the content panel
        contentPanel.add(toolbarComponent, BorderLayout.NORTH)

        // Initialize diff panel and editor highlights for the initial range
        updateDiffPanel(range)
        updateEditorHighlights(range)

        // Create lightweight popup with toolbar and inline diff.
        // cancelOnClickOutside=false: the EditorMouseListener handles closing so the gutter
        // click is NOT consumed by the popup mechanism — enabling single-click switching.
        // setBorderColor: explicitly set the popup window border so it is consistent across
        // themes. Without it, themes that don't decorate popup windows render the toolbar
        // area without any surrounding border while the diff panel below it has one.
        val popupBorderColor = JBColor.namedColor("VersionControl.MarkerPopup.borderColor", JBColor(Gray._206, Gray._75))
        popupRef = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(contentPanel, null)
            .setRequestFocus(false)
            .setFocusable(false)
            .setMovable(false)
            .setResizable(false)
            .setCancelOnClickOutside(false)
            .setBorderColor(popupBorderColor)
            .createPopup()

        // Show just below the last line of the clicked range so the changed text stays visible.
        // For DELETED ranges line2 == line1 (no current lines), so anchor below line1.
        val anchorLine = if (range.type == Range.DELETED) range.line1 else range.line2
        val linePoint = editor.logicalPositionToXY(LogicalPosition(anchorLine, 0))
        val screenPoint = Point(linePoint).also {
            SwingUtilities.convertPointToScreen(it, editor.contentComponent)
        }
        val relativePoint = RelativePoint(Point(e.locationOnScreen.x, screenPoint.y))
        popupRef.show(relativePoint)

        // Track the active popup and add a mouse listener so clicking any marker
        // closes it first (allowing doAction to open a new one in a single click)
        val capturedPopup = popupRef
        activePopup = capturedPopup

        capturedPopup.addListener(object : JBPopupListener {
            override fun onClosed(event: LightweightWindowEvent) {
                if (activePopup === capturedPopup) activePopup = null
                Disposer.dispose(popupDisposable)
            }
        })
        editor.addEditorMouseListener(object : EditorMouseListener {
            override fun mousePressed(e: EditorMouseEvent) {
                activePopup?.cancel()
            }
        }, popupDisposable)
    }

    /**
     * Installs line-level and word-level highlights on the current editor text for [range],
     * mirroring the behaviour of the IDE's own VCS gutter popup
     * (LineStatusMarkerPopupPanel.installMasterEditorWordHighlighters).
     *
     * All highlighters are automatically removed when [disposable] is disposed (i.e. when
     * the popup closes or the user navigates to a different range).
     */
    private fun installEditorHighlights(editor: EditorEx, range: Range, disposable: Disposable) {
        // DELETED ranges have no current lines — nothing to highlight in the editor.
        if (range.type == Range.DELETED) return

        val startLine = range.line1
        val endLine   = range.line2
        val diffType  = if (range.type == Range.INSERTED) TextDiffType.INSERTED else TextDiffType.MODIFIED

        val highlighters = mutableListOf<RangeHighlighter>()

        // Line-level background — use the dimmer "ignored" variant (withIgnored=true) so the
        // highlight is subtle rather than the full opaque diff background.
        // withHideStripeMarkers / withHideGutterMarkers avoids duplicating the overview-stripe
        // and gutter icons that we already paint ourselves.
        highlighters.addAll(
            DiffDrawUtil.LineHighlighterBuilder(editor, startLine, endLine, diffType)
                .withLayerPriority(1) // DiffDrawUtil.LAYER_PRIORITY_LST
                .withIgnored(true)
                .withHideStripeMarkers(true)
                .withHideGutterMarkers(true)
                .done()
        )

        // Word-level inline highlights — only meaningful for MODIFIED ranges.
        if (range.type == Range.MODIFIED) {
            try {
                val vcsContent       = diffViewer.getVcsContentForRange(range, includeContext = false)
                val currentTextRange = DiffUtil.getLinesRange(editor.document, startLine, endLine)
                val currentContent   = editor.document.getText(currentTextRange)

                val wordDiff = ComparisonManager.getInstance().compareChars(
                    vcsContent, currentContent,
                    ComparisonPolicy.DEFAULT,
                    DumbProgressIndicator.INSTANCE
                )

                val baseOffset = currentTextRange.startOffset
                for (fragment in wordDiff) {
                    val start = baseOffset + fragment.startOffset2
                    val end   = baseOffset + fragment.endOffset2
                    if (start < end) {
                        highlighters.addAll(
                            DiffDrawUtil.InlineHighlighterBuilder(editor, start, end, DiffUtil.getDiffType(fragment))
                                .withLayerPriority(1)
                                .done()
                        )
                    }
                }
            } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
                throw e
            } catch (e: Exception) {
                LOG.warn("Error computing word diff for editor highlights", e)
            }
        }

        Disposer.register(disposable) { highlighters.forEach { it.dispose() } }
    }

    private fun createInlineDiffPanel(editor: EditorEx, range: Range): JPanel {
        val panel = JPanel(BorderLayout())

        // Get content based on range type:
        //   DELETED   → show the deleted scope-base lines (nothing exists in current)
        //   MODIFIED  → show the scope-base lines; word-level diff highlights what changed
        //   INSERTED  → nothing to show (the inserted lines are already visible in the editor)
        val content = when (range.type) {
            Range.DELETED, Range.MODIFIED -> diffViewer.getVcsContentForRange(range, includeContext = false)
            else -> ""
        }

        if (content.isEmpty()) return panel

        // Count lines to determine if we need to limit the height
        val lineCount = content.count { it == '\n' } + 1
        val maxLines = 20
        val shouldLimitHeight = lineCount > maxLines

        // Create EditorTextField with syntax highlighting and diff colors (like IDE)
        val textField = EditorTextField(content, project, file.fileType)
        textField.setBorder(null)
        textField.setOneLineMode(false)
        textField.setFontInheritedFromLAF(false)

        // Calculate reasonable width based on editor viewport
        // Use 90% of the editor's visible width as maximum
        val editorVisibleWidth = editor.scrollingModel.visibleArea.width
        val maxPopupWidth = (editorVisibleWidth * 0.9).toInt()

        // Calculate width based on content, but cap at 90% of editor width
        val lines = content.split('\n')
        val maxLineLength = lines.maxOfOrNull { it.length } ?: 80
        val charWidth = editor.colorsScheme.getFont(EditorFontType.PLAIN).size
        val contentWidth = maxLineLength * charWidth
        val preferredWidth = kotlin.math.min(contentWidth, maxPopupWidth)

        // Set preferred size to limit height if needed
        if (shouldLimitHeight) {
            val lineHeight = editor.lineHeight
            val maxHeight = lineHeight * maxLines
            textField.preferredSize = Dimension(preferredWidth, maxHeight)
        } else {
            val lineHeight = editor.lineHeight
            val contentHeight = lineHeight * lineCount
            textField.preferredSize = Dimension(preferredWidth, contentHeight)
        }

        textField.addSettingsProvider { popupEditor ->
            popupEditor.setVerticalScrollbarVisible(true)
            popupEditor.setHorizontalScrollbarVisible(true)
            popupEditor.settings.isUseSoftWraps = false
            popupEditor.isRendererMode = true
            popupEditor.setBorder(null)
            popupEditor.colorsScheme = editor.colorsScheme

            // Set background color for changed lines (like IDE VCS popup)
            val backgroundColor = editor.colorsScheme.getColor(EditorColors.CHANGED_LINES_POPUP)
                ?: editor.backgroundColor
            popupEditor.backgroundColor = backgroundColor
            popupEditor.settings.isCaretRowShown = false

            popupEditor.settings.setTabSize(editor.settings.getTabSize(project))
            popupEditor.settings.setUseTabCharacter(editor.settings.isUseTabCharacter(project))

            // Apply word-level diff highlighting for modified ranges.
            // The popup shows the scope-base (vcs) text, so highlights land at offset1
            // positions (left/old side of the diff). Fragments with an empty offset1
            // range are pure insertions in the current file and produce no highlight.
            if (range.type == Range.MODIFIED) {
                try {
                    val currentContent = run {
                        val startOffset = editor.logicalPositionToOffset(LogicalPosition(range.line1, 0))
                        val endOffset = editor.logicalPositionToOffset(LogicalPosition(range.line2, 0))
                        document.getText(TextRange(startOffset, endOffset))
                    }

                    val comparisonManager = ComparisonManager.getInstance()
                    val wordDiff = comparisonManager.compareChars(
                        content,         // vcs (left/old)
                        currentContent,  // current (right/new)
                        ComparisonPolicy.DEFAULT,
                        DumbProgressIndicator.INSTANCE
                    )

                    for (fragment in wordDiff) {
                        // Highlight at offset1 — positions within the vcs text shown in the popup
                        DiffDrawUtil.createInlineHighlighter(
                            popupEditor,
                            fragment.startOffset1,
                            fragment.endOffset1,
                            DiffUtil.getDiffType(fragment)
                        )
                    }
                } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
                    throw e
                } catch (e: Exception) {
                    LOG.warn("Error applying word diff highlighting", e)
                }
            }
        }

        // Wrap in a panel with border (like IDE)
        val editorComponent = if (shouldLimitHeight) {
            // Create a layered panel with fade-out effect
            object : JPanel(BorderLayout()) {
                override fun paintComponent(g: java.awt.Graphics) {
                    super.paintComponent(g)

                    // Draw fade-out gradient at the bottom
                    val g2d = g as Graphics2D
                    val fadeHeight = JBUI.scale(30)
                    val startY = height - fadeHeight

                    val popupBackgroundColor = editor.colorsScheme.getColor(EditorColors.CHANGED_LINES_POPUP)
                        ?: editor.backgroundColor

                    val gradient = GradientPaint(
                        0f, startY.toFloat(),
                        ColorUtil.toAlpha(popupBackgroundColor, 0),
                        0f, height.toFloat(),
                        ColorUtil.toAlpha(popupBackgroundColor, 255)
                    )
                    g2d.paint = gradient
                    g2d.fillRect(0, startY, width, fadeHeight)
                }
            }.apply {
                add(textField, BorderLayout.CENTER)
            }
        } else {
            JPanel(BorderLayout()).apply {
                add(textField, BorderLayout.CENTER)
            }
        }

        val borderColor = JBColor.namedColor("VersionControl.MarkerPopup.borderColor", JBColor(Gray._206, Gray._75))
        val outsideBorder = JBUI.Borders.customLine(borderColor, 1)
        val insideBorder = JBUI.Borders.empty(2)
        editorComponent.border = BorderFactory.createCompoundBorder(outsideBorder, insideBorder)

        val popupBackgroundColor = editor.colorsScheme.getColor(EditorColors.CHANGED_LINES_POPUP)
            ?: editor.backgroundColor
        editorComponent.background = popupBackgroundColor

        panel.add(editorComponent, BorderLayout.CENTER)
        return panel
    }

    private fun createDataContext(editor: EditorEx): DataContext {
        return DataContext { dataId ->
            when (dataId) {
                CommonDataKeys.PROJECT.name -> project
                CommonDataKeys.EDITOR.name -> editor
                CommonDataKeys.VIRTUAL_FILE.name -> file
                else -> null
            }
        }
    }

    private fun rollbackRange(editor: EditorEx, range: Range) {
        WriteCommandAction.runWriteCommandAction(project) {
            try {
                LOG.debug("Rolling back range type=${range.type}, line1=${range.line1}, line2=${range.line2}, vcsLine1=${range.vcsLine1}, vcsLine2=${range.vcsLine2}")

                when (range.type) {
                    Range.INSERTED -> {
                        // For inserted lines, delete them
                        val startOffset = editor.logicalPositionToOffset(LogicalPosition(range.line1, 0))
                        val endOffset = editor.logicalPositionToOffset(LogicalPosition(range.line2, 0))
                        LOG.debug("Deleting inserted range: startOffset=$startOffset, endOffset=$endOffset")
                        document.deleteString(startOffset, endOffset)
                    }
                    Range.DELETED -> {
                        // For deleted lines, insert the base content (without context)
                        val baseContent = diffViewer.getVcsContentForRange(range, includeContext = false)
                        LOG.debug("Restoring deleted content: ${baseContent.length} chars")
                        if (baseContent.isNotEmpty()) {
                            if (range.line1 >= document.lineCount) {
                                // Inserting at end of document: prepend newline separator
                                document.insertString(document.textLength, "\n" + baseContent)
                            } else {
                                // Inserting before an existing line: append newline to push it down
                                val offset = document.getLineStartOffset(range.line1)
                                document.insertString(offset, baseContent + "\n")
                            }
                        }
                    }
                    Range.MODIFIED -> {
                        // For modified lines, replace with base content (without context)
                        val baseContent = diffViewer.getVcsContentForRange(range, includeContext = false)
                        LOG.debug("Replacing modified content: ${baseContent.length} chars")
                        val startOffset = document.getLineStartOffset(range.line1)
                        val endOffset = document.getLineEndOffset(range.line2 - 1)
                        document.replaceString(startOffset, endOffset, baseContent)
                    }
                }
                LOG.debug("Successfully rolled back range at line ${range.line1}")
            } catch (e: Exception) {
                LOG.error("Error rolling back range", e)
            }
        }
    }

    private fun copyRangeToClipboard(editor: EditorEx, range: Range) {
        val text = document.getText(
            TextRange(
                editor.logicalPositionToOffset(LogicalPosition(range.line1, 0)),
                editor.logicalPositionToOffset(LogicalPosition(range.line2, 0))
            )
        )
        val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(text), null)
    }
}
