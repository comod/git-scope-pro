// Adapted from IntelliJ Platform for Git Scope Pro
package implementation.gutter

import com.intellij.diff.comparison.ComparisonManager
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.util.DiffDrawUtil
import com.intellij.diff.util.DiffUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
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
import com.intellij.openapi.progress.DumbProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ColorUtil
import com.intellij.ui.EditorTextField
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import system.Defs
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import javax.swing.BorderFactory
import javax.swing.JPanel

/**
 * Manages line status markers for scope changes in the editor gutter.
 * Creates and updates a single range highlighter that paints markers for all changed ranges.
 */
class ScopeLineStatusMarkerRenderer(
    private val project: Project,
    private val document: Document,
    private val file: VirtualFile,
    parentDisposable: Disposable
) : Disposable {

    companion object {
        private val LOG: Logger = Defs.getLogger(ScopeLineStatusMarkerRenderer::class.java)
        private const val GUTTER_LAYER = 6  // Layer for painting in the gutter
    }

    @Volatile
    private var gutterHighlighter: RangeHighlighter? = null

    // Per-range highlighters for the thin error stripe marks on the right side of the editor
    private val errorStripeHighlighters: MutableList<RangeHighlighter> = mutableListOf()

    @Volatile
    private var currentRanges: List<Range> = emptyList()

    @Volatile
    private var disposed = false

    @Volatile
    private var hoveredRange: Range? = null

    private var lastMouseY: Int = -1

    // Tracks the currently-open gutter popup so it can be closed before a new one opens
    private var activePopup: com.intellij.openapi.ui.popup.JBPopup? = null

    private val diffViewer: ScopeDiffViewer = ScopeDiffViewer(project, document, file)

    // Mouse listener to detect when mouse exits the gutter area
    private val mouseMotionListener = object : MouseMotionAdapter() {
        override fun mouseMoved(e: MouseEvent) {
            // This will be called frequently; we use it to clear hover when outside marker area
            updateHoverState(e)
        }
    }

    private val mouseListener = object : MouseAdapter() {
        override fun mouseExited(e: MouseEvent) {
            clearHover()
        }
    }

    init {
        Disposer.register(parentDisposable, this)
        installMouseListeners()
    }

    private fun installMouseListeners() {
        ApplicationManager.getApplication().invokeLater {
            if (!disposed) {
                val editors = EditorFactory.getInstance().getEditors(document, project)
                for (editor in editors) {
                    if (editor is EditorEx) {
                        val gutter = editor.gutterComponentEx
                        gutter.addMouseMotionListener(mouseMotionListener)
                        gutter.addMouseListener(mouseListener)
                    }
                }
            }
        }
    }

    private fun updateHoverState(e: MouseEvent) {
        val editor = EditorFactory.getInstance().getEditors(document, project)
            .filterIsInstance<EditorEx>()
            .firstOrNull { it.gutterComponentEx == e.source } ?: return

        val gutter = editor.gutterComponentEx
        val x = e.x
        val y = e.y

        // Check if in marker area (must match canDoAction logic)
        val gitScopeSettings = settings.GitScopeSettings.getInstance()
        val inMarkerArea: Boolean

        if (gitScopeSettings.isSeparateGutterRendering) {
            val markerX = maxOf(gutter.annotationsAreaOffset, gutter.annotationsAreaOffset + gutter.annotationsAreaWidth - JBUI.scale(4))
            inMarkerArea = x in (markerX - JBUI.scale(1))..(markerX + JBUI.scale(9))
        } else {
            // Merged: use same gutter area as paint, expanded by hover margin
            val gutterArea = getGutterArea(editor)
            inMarkerArea = x in (gutterArea.first - JBUI.scale(3))..(gutterArea.second + JBUI.scale(3))
        }

        // Check if mouse is over any range (y-axis)
        val overRange = if (inMarkerArea) {
            currentRanges.any { range -> rangeContainsY(editor, range, y) }
        } else {
            false
        }

        // Clear hover if not in marker area OR not over any range
        if (!inMarkerArea || !overRange) {
            clearHover()
        }
    }

    private fun clearHover() {
        if (hoveredRange != null) {
            hoveredRange = null
            lastMouseY = -1
            repaintGutter()
        }
    }
    
    /**
     * Returns true if the pixel y falls within the painted area of [range].
     * DELETED ranges have line1 == line2 so y1 == y2; give them the same
     * fixed height (8 px) used when painting them.
     */
    private fun rangeContainsY(editor: Editor, range: Range, y: Int): Boolean {
        val y1 = editor.logicalPositionToXY(com.intellij.openapi.editor.LogicalPosition(range.line1, 0)).y
        val y2 = if (range.type == Range.DELETED) y1 + JBUI.scale(8)
                 else editor.logicalPositionToXY(com.intellij.openapi.editor.LogicalPosition(range.line2, 0)).y
        return y in y1..y2
    }

    private fun showPopupMenu(editor: EditorEx, range: Range, e: MouseEvent) {
        // Close any existing popup before opening a new one
        activePopup?.cancel()

        // Sort ranges once
        val sortedRanges = currentRanges.sortedBy { it.line1 }

        // Use a holder to track current index that can be updated
        var currentIndex = sortedRanges.indexOf(range)

        // Variable to hold the popup reference so actions can close it
        var popupRef: com.intellij.openapi.ui.popup.JBPopup? = null

        // Create inline diff preview (like IDE VCS gutter popup)
        val contentPanel = JPanel(BorderLayout())

        // Holder for the diff panel that can be updated
        var diffPanelHolder: JPanel? = null

        // Function to update the diff panel
        fun updateDiffPanel(newRange: Range) {
            // Remove old diff panel if exists
            diffPanelHolder?.let { contentPanel.remove(it) }

            // Create new diff panel
            val newDiffPanel = createInlineDiffPanel(editor, newRange)
            diffPanelHolder = newDiffPanel

            // Add to content panel
            contentPanel.add(newDiffPanel, BorderLayout.CENTER)

            // Refresh the panel and resize popup
            contentPanel.revalidate()
            contentPanel.repaint()

            // Resize the popup to fit the new content
            popupRef?.let { popup ->
                val newSize = contentPanel.preferredSize
                popup.size = newSize
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
                        editor.caretModel.moveToLogicalPosition(com.intellij.openapi.editor.LogicalPosition(previousRange.line1, 0))
                        editor.scrollingModel.scrollToCaret(com.intellij.openapi.editor.ScrollType.CENTER)
                        updateDiffPanel(previousRange)
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
                        editor.caretModel.moveToLogicalPosition(com.intellij.openapi.editor.LogicalPosition(nextRange.line1, 0))
                        editor.scrollingModel.scrollToCaret(com.intellij.openapi.editor.ScrollType.CENTER)
                        updateDiffPanel(nextRange)
                    }
                }

                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = currentIndex >= 0 && currentIndex < sortedRanges.size - 1
                }
            })

            // Rollback action
            add(object : AnAction("Rollback Lines", "Rollback changes to base version", AllIcons.Actions.Rollback) {
                override fun actionPerformed(event: AnActionEvent) {
                    popupRef?.cancel()  // Close popup immediately
                    rollbackRange(editor, sortedRanges[currentIndex])
                }
            })

            // Show Diff action
            add(object : AnAction("Show Diff", "Show diff for this change", AllIcons.Actions.Diff) {
                override fun actionPerformed(event: AnActionEvent) {
                    popupRef?.cancel()  // Close popup immediately
                    diffViewer.showDiffForRange(editor, sortedRanges[currentIndex])
                }
            })

            // Copy action (only for non-deleted ranges)
            if (range.type != Range.DELETED) {
                add(object : AnAction("Copy", "Copy change to clipboard", AllIcons.Actions.Copy) {
                    override fun actionPerformed(event: AnActionEvent) {
                        popupRef?.cancel()  // Close popup immediately
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
                    override fun createCustomComponent(
                        presentation: Presentation,
                        place: String
                    ): javax.swing.JComponent {
                        val label = com.intellij.ui.components.JBLabel(scopeName)
                        label.foreground = com.intellij.util.ui.UIUtil.getContextHelpForeground()
                        label.border = JBUI.Borders.emptyLeft(4)
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

        // Initialize diff panel with the initial range
        updateDiffPanel(range)

        // Create lightweight popup with toolbar and inline diff.
        // cancelOnClickOutside=false: the EditorMouseListener handles closing so the gutter
        // click is NOT consumed by the popup mechanism — enabling single-click switching.
        popupRef = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(contentPanel, null)
            .setRequestFocus(false)
            .setFocusable(false)
            .setMovable(false)
            .setResizable(false)
            .setCancelOnClickOutside(false)
            .createPopup()

        // Show at mouse position relative to the gutter component
        val relativePoint = RelativePoint(e)
        popupRef.show(relativePoint)

        // Track the active popup and add a mouse listener so clicking any marker
        // closes it first (allowing doAction to open a new one in a single click)
        val capturedPopup = popupRef
        activePopup = capturedPopup

        val popupDisposable = Disposer.newDisposable("ScopeGutterPopup")
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
        val charWidth = editor.colorsScheme.getFont(com.intellij.openapi.editor.colors.EditorFontType.PLAIN).size
        val contentWidth = maxLineLength * charWidth
        val preferredWidth = kotlin.math.min(contentWidth, maxPopupWidth)

        // Set preferred size to limit height if needed
        if (shouldLimitHeight) {
            val lineHeight = editor.lineHeight
            val maxHeight = lineHeight * maxLines
            textField.preferredSize = java.awt.Dimension(preferredWidth, maxHeight)
        } else {
            val lineHeight = editor.lineHeight
            val contentHeight = lineHeight * lineCount
            textField.preferredSize = java.awt.Dimension(preferredWidth, contentHeight)
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
                        val startOffset = editor.logicalPositionToOffset(
                            com.intellij.openapi.editor.LogicalPosition(range.line1, 0))
                        val endOffset = editor.logicalPositionToOffset(
                            com.intellij.openapi.editor.LogicalPosition(range.line2, 0))
                        document.getText(com.intellij.openapi.util.TextRange(startOffset, endOffset))
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
                    val g2d = g as java.awt.Graphics2D
                    val fadeHeight = JBUI.scale(30)
                    val startY = height - fadeHeight

                    val popupBackgroundColor = editor.colorsScheme.getColor(EditorColors.CHANGED_LINES_POPUP)
                        ?: editor.backgroundColor

                    val gradient = java.awt.GradientPaint(
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
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            try {
                LOG.debug("Rolling back range type=${range.type}, line1=${range.line1}, line2=${range.line2}, vcsLine1=${range.vcsLine1}, vcsLine2=${range.vcsLine2}")

                when (range.type) {
                    Range.INSERTED -> {
                        // For inserted lines, delete them
                        val startOffset = editor.logicalPositionToOffset(com.intellij.openapi.editor.LogicalPosition(range.line1, 0))
                        val endOffset = editor.logicalPositionToOffset(com.intellij.openapi.editor.LogicalPosition(range.line2, 0))
                        LOG.debug("Deleting inserted range: startOffset=$startOffset, endOffset=$endOffset")
                        document.deleteString(startOffset, endOffset)
                    }
                    Range.DELETED -> {
                        // For deleted lines, insert the base content (without context)
                        val baseContent = diffViewer.getVcsContentForRange(range, includeContext = false)
                        LOG.debug("Restoring deleted content: ${baseContent.length} chars")
                        if (baseContent.isNotEmpty()) {
                            val offset = editor.logicalPositionToOffset(com.intellij.openapi.editor.LogicalPosition(range.line1, 0))
                            document.insertString(offset, baseContent + "\n")
                        }
                    }
                    Range.MODIFIED -> {
                        // For modified lines, replace with base content (without context)
                        val baseContent = diffViewer.getVcsContentForRange(range, includeContext = false)
                        LOG.debug("Replacing modified content: ${baseContent.length} chars")
                        val startOffset = editor.logicalPositionToOffset(com.intellij.openapi.editor.LogicalPosition(range.line1, 0))
                        val endOffset = editor.logicalPositionToOffset(com.intellij.openapi.editor.LogicalPosition(range.line2, 0))
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
            com.intellij.openapi.util.TextRange(
                editor.logicalPositionToOffset(com.intellij.openapi.editor.LogicalPosition(range.line1, 0)),
                editor.logicalPositionToOffset(com.intellij.openapi.editor.LogicalPosition(range.line2, 0))
            )
        )
        val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(java.awt.datatransfer.StringSelection(text), null)
    }
    

    /**
     * Updates the base content from VCS.
     * Must be called before updateRanges.
     */
    fun setVcsBaseContent(baseContent: String?) {
        diffViewer.setVcsBaseContent(baseContent)
    }

    /**
     * Updates the ranges to be painted in the gutter.
     * Must be called on EDT.
     */
    @RequiresEdt
    fun updateRanges(ranges: List<Range>) {
        if (disposed) return

        currentRanges = ranges

        // Ensure highlighter exists
        if (gutterHighlighter == null || !gutterHighlighter!!.isValid) {
            createGutterHighlighter()
        }

        // Update the thin error stripe marks on the right side of the editor
        updateErrorStripeMarks(ranges)

        // Repaint the gutter
        repaintGutter()
    }

    /**
     * Updates the thin error stripe marks on the right side of the editor.
     * Creates per-range RangeHighlighters with thin error stripe marks.
     */
    @RequiresEdt
    private fun updateErrorStripeMarks(ranges: List<Range>) {
        val markupModel = DocumentMarkupModel.forDocument(document, project, false) as? MarkupModelEx
            ?: return

        // Remove old error stripe highlighters
        for (highlighter in errorStripeHighlighters) {
            try {
                if (highlighter.isValid) {
                    markupModel.removeHighlighter(highlighter)
                }
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

                // Calculate document offsets for this range
                val startLine = range.line1.coerceIn(0, document.lineCount - 1)
                val endLine = if (range.type == Range.DELETED) {
                    // Deleted ranges have line1 == line2; use line1 for a point marker
                    startLine
                } else {
                    (range.line2 - 1).coerceIn(startLine, document.lineCount - 1)
                }

                val startOffset = document.getLineStartOffset(startLine)
                val endOffset = document.getLineEndOffset(endLine)

                val highlighter = markupModelForCreate.addRangeHighlighterAndChangeAttributes(
                    null,
                    startOffset,
                    endOffset,
                    GUTTER_LAYER,
                    HighlighterTargetArea.LINES_IN_RANGE,
                    false
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

    private fun getErrorStripeColor(type: Byte): java.awt.Color? {
        // Use the same colors as the gutter markers
        val editors = EditorFactory.getInstance().getEditors(document, project)
        val editor = editors.firstOrNull() as? EditorEx ?: return null
        val scheme = editor.colorsScheme
        return when (type) {
            Range.INSERTED -> scheme.getColor(EditorColors.ADDED_LINES_COLOR) ?: JBColor.GREEN
            Range.DELETED  -> scheme.getColor(EditorColors.DELETED_LINES_COLOR) ?: JBColor.RED
            Range.MODIFIED -> scheme.getColor(EditorColors.MODIFIED_LINES_COLOR) ?: JBColor.BLUE
            else -> null
        }
    }

    /**
     * Creates the gutter highlighter that will paint markers for all ranges.
     */
    @RequiresEdt
    private fun createGutterHighlighter() {
        if (disposed) return

        // Remove old highlighter if it exists
        gutterHighlighter?.let { oldHighlighter ->
            try {
                if (oldHighlighter.isValid) {
                    val markupModel = DocumentMarkupModel.forDocument(document, project, false) as? MarkupModelEx
                    markupModel?.removeHighlighter(oldHighlighter)
                }
            } catch (e: Exception) {
                LOG.warn("Error removing old highlighter", e)
            }
        }

        try {
            val markupModel = DocumentMarkupModel.forDocument(document, project, true) as MarkupModelEx

            // Create a range highlighter that spans the entire document
            val highlighter = markupModel.addRangeHighlighterAndChangeAttributes(
                null,
                0,
                document.textLength,
                GUTTER_LAYER,
                HighlighterTargetArea.LINES_IN_RANGE,
                false
            ) { rangeHighlighter: RangeHighlighterEx ->
                // Set the line marker renderer that will paint the gutter
                rangeHighlighter.lineMarkerRenderer = createGutterMarkerRenderer()
                rangeHighlighter.isGreedyToLeft = true
                rangeHighlighter.isGreedyToRight = true
                // Suppress in diff editors — mirrors LineStatusMarkerRenderer's approach
                rangeHighlighter.editorFilter = MarkupEditorFilterFactory.createIsNotDiffFilter()
            }

            gutterHighlighter = highlighter
        } catch (e: Exception) {
            LOG.error("Error creating gutter highlighter", e)
        }
    }

    /**
     * Creates the line marker renderer that paints the actual gutter markers.
     */
    private fun createGutterMarkerRenderer(): LineMarkerRenderer {
        return object : LineStatusGutterMarkerRenderer() {
            override fun getPaintedRanges(): List<Range>? {
                return currentRanges.ifEmpty { null }
            }

            override fun getHoveredRange(): Range? {
                return hoveredRange
            }

            override fun canDoAction(editor: Editor, e: MouseEvent): Boolean {
                val result = super.canDoAction(editor, e)
                val y = e.y

                // Update hovered range based on mouse position
                val newHoveredRange = if (result) {
                    currentRanges.firstOrNull { range -> rangeContainsY(editor, range, y) }
                } else {
                    null
                }

                // Only repaint if hover state changed
                if (newHoveredRange != hoveredRange) {
                    hoveredRange = newHoveredRange
                    lastMouseY = y
                    repaintGutter()
                } else if (result && lastMouseY != y) {
                    // Mouse moved but still over same range, track y position
                    lastMouseY = y
                }

                return result
            }

            override fun doAction(editor: Editor, e: MouseEvent) {
                // Consume the event to prevent IDE from handling it (e.g., setting breakpoints)
                e.consume()

                // Find which range was clicked
                val editorEx = editor as? EditorEx ?: return
                val y = e.y

                val clickedRange = currentRanges.firstOrNull { range -> rangeContainsY(editor, range, y) }

                if (clickedRange != null) {
                    showPopupMenu(editorEx, clickedRange, e)
                }
            }
        }
    }

    /**
     * Triggers an immediate repaint of all editor gutters for this document.
     */
    @RequiresEdt
    private fun repaintGutter() {
        if (disposed) return

        try {
            // Find all editors for this document and force their gutters to repaint
            val allEditors = EditorFactory.getInstance().getEditors(document, project)
            for (editor in allEditors) {
                if (editor is EditorEx) {
                    // Force immediate gutter repaint
                    editor.gutterComponentEx.repaint()
                }
            }
        } catch (e: Exception) {
            LOG.warn("Error repainting gutter", e)
        }
    }

    /**
     * Clears all ranges and removes the highlighter.
     */
    @RequiresEdt
    fun clear() {
        if (disposed) return

        currentRanges = emptyList()

        val markupModel = DocumentMarkupModel.forDocument(document, project, false) as? MarkupModelEx

        gutterHighlighter?.let { highlighter ->
            try {
                if (highlighter.isValid) {
                    markupModel?.removeHighlighter(highlighter)
                }
            } catch (e: Exception) {
                LOG.warn("Error removing highlighter", e)
            }
        }
        gutterHighlighter = null

        // Remove error stripe highlighters
        for (highlighter in errorStripeHighlighters) {
            try {
                if (highlighter.isValid) {
                    markupModel?.removeHighlighter(highlighter)
                }
            } catch (e: Exception) {
                LOG.warn("Error removing error stripe highlighter", e)
            }
        }
        errorStripeHighlighters.clear()
    }

    override fun dispose() {
        disposed = true

        ApplicationManager.getApplication().invokeLater {
            removeMouseListeners()
            clear()
        }
    }

    private fun removeMouseListeners() {
        val editors = EditorFactory.getInstance().getEditors(document, project)
        for (editor in editors) {
            if (editor is EditorEx) {
                val gutter = editor.gutterComponentEx
                gutter.removeMouseMotionListener(mouseMotionListener)
                gutter.removeMouseListener(mouseListener)
            }
        }
    }
}
