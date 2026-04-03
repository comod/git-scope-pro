// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Based on com.intellij.openapi.vcs.ex.LineStatusTrackerBase from the IntelliJ Platform
// (platform/diff-impl), modified for Git Scope Pro.
package implementation.gutter

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.TextAnnotationGutterProvider
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter

/**
 * Manages line status markers for scope changes in the editor gutter.
 * Orchestrates the gutter highlighter, popup panel, hover state, and filler.
 * Analogous to com.intellij.openapi.vcs.ex.LineStatusMarkerRendererWithPopup in the IntelliJ Platform.
 */
class ScopeLineStatusMarkerRenderer(
    private val project: Project,
    private val document: Document,
    private val file: VirtualFile,
    parentDisposable: Disposable
) : Disposable {

    @Volatile
    private var currentRanges: List<Range> = emptyList()

    @Volatile
    private var disposed = false

    @Volatile
    private var hoveredRange: Range? = null

    private val diffViewer: ScopeDiffViewer = ScopeDiffViewer(project, document, file)

    private val popupPanel = ScopeGutterPopupPanel(project, document, file, diffViewer) { currentRanges }

    private val gutterHighlighterManager = ScopeGutterHighlighterManager(
        project, document,
        rangesProvider = { currentRanges },
        hoveredRangeProvider = { hoveredRange },
        onHoverChanged = { newHoveredRange -> hoveredRange = newHoveredRange },
        onDoAction = { editor, range, e -> popupPanel.show(editor, range, e) }
    )

    // Filler that reserves annotation area width to push line numbers right in separate mode.
    // Managed dynamically: re-registers after blame close, unregisters when setting is off.
    @Volatile private var fillerDesired = false
    @Volatile private var suppressFillerCallback = false

    private val gutterFiller: TextAnnotationGutterProvider.Filler = object : TextAnnotationGutterProvider.Filler {
        override fun getWidth(): Int = JBUI.scale(6)
        override fun getLineText(line: Int, editor: Editor?): String? = null
        override fun getToolTip(line: Int, editor: Editor?): String? = null
        override fun getStyle(line: Int, editor: Editor?) = com.intellij.openapi.editor.colors.EditorFontType.PLAIN
        override fun getColor(line: Int, editor: Editor?): com.intellij.openapi.editor.colors.ColorKey? = null
        override fun getBgColor(line: Int, editor: Editor?): java.awt.Color? = null
        override fun getPopupActions(line: Int, editor: Editor?): List<com.intellij.openapi.actionSystem.AnAction> = emptyList()
        override fun useMargin(): Boolean = false
        override fun gutterClosed() {
            // Re-register after external removal (e.g. blame closeAllAnnotations)
            if (!suppressFillerCallback && fillerDesired && !disposed) {
                ApplicationManager.getApplication().invokeLater {
                    if (fillerDesired && !disposed) ensureFillerOnAllEditors()
                }
            }
        }
    }

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
        override fun mouseEntered(e: MouseEvent) {
            // Re-evaluate on entry so any hover state stuck from a previous session
            // (fast mouse exit, OS focus change, popup covering gutter) is corrected.
            updateHoverState(e)
        }
    }

    init {
        Disposer.register(parentDisposable, this)
        installMouseListeners()
    }

    private fun installMouseListeners() {
        // Called from init, which always runs on EDT (renderer construction is EDT-only).
        // Install synchronously — no invokeLater — so the editor that triggered this
        // renderer's creation is found immediately via getEditors() without a timing race.
        val factory = EditorFactory.getInstance()
        for (editor in factory.getEditors(document, project)) {
            installOnEditor(editor)
        }
        // Also catch split editors opened after this renderer is created.
        factory.addEditorFactoryListener(object : EditorFactoryListener {
            override fun editorCreated(event: EditorFactoryEvent) {
                val editor = event.editor
                if (!disposed && editor.document == document) {
                    installOnEditor(editor)
                }
            }
        }, this@ScopeLineStatusMarkerRenderer)
        syncFillerWithSetting()
    }

    private fun installOnEditor(editor: Editor) {
        if (editor is EditorEx) {
            val gutter = editor.gutterComponentEx
            gutter.addMouseMotionListener(mouseMotionListener)
            gutter.addMouseListener(mouseListener)
        }
    }

    private fun updateHoverState(e: MouseEvent) {
        val editor = EditorFactory.getInstance().getEditors(document, project)
            .filterIsInstance<EditorEx>()
            .firstOrNull { it.gutterComponentEx == e.source } ?: return

        val gutter = editor.gutterComponentEx
        val x = e.x
        val y = e.y

        // Check if in marker area — must match canDoAction logic exactly so that
        // hover is cleared precisely when the painted marker is no longer under the cursor.
        val gitScopeSettings = settings.GitScopeSettings.getInstance()
        val gutterArea = getGutterArea(editor)
        val areaWidth = gutterArea.second - gutterArea.first
        val hoverExpansion = maxOf(areaWidth - JBUI.scale(1), JBUI.scale(1))
        val inMarkerArea: Boolean

        if (gitScopeSettings.isSeparateGutterRendering) {
            val markerX = maxOf(gutter.annotationsAreaOffset, gutter.annotationsAreaOffset + gutter.annotationsAreaWidth - areaWidth)
            inMarkerArea = x in (markerX - JBUI.scale(1))..(markerX + areaWidth + hoverExpansion)
        } else {
            inMarkerArea = x in (gutterArea.first - hoverExpansion - JBUI.scale(1))..(gutterArea.second + JBUI.scale(1))
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
            gutterHighlighterManager.repaintGutter()
        }
    }

    /**
     * Syncs filler registration with the current [GitScopeSettings.isSeparateGutterRendering] value.
     * Called from [updateRanges] (on every scope update) and [installMouseListeners].
     */
    private fun syncFillerWithSetting() {
        val shouldHaveFiller = settings.GitScopeSettings.getInstance().isSeparateGutterRendering
        if (shouldHaveFiller != fillerDesired) {
            fillerDesired = shouldHaveFiller
            ensureFillerOnAllEditors()
        }
    }

    /**
     * Ensures the filler is registered on all editors if [fillerDesired], or removed if not.
     * Uses [suppressFillerCallback] to prevent [gutterClosed] from re-registering during
     * intentional removal.
     */
    private fun ensureFillerOnAllEditors() {
        suppressFillerCallback = true
        try {
            for (editor in EditorFactory.getInstance().getEditors(document, project)) {
                if (editor is EditorEx) {
                    // Always close first to avoid duplicates (no-op if not present)
                    editor.gutter.closeTextAnnotations(listOf(gutterFiller))
                    if (fillerDesired) {
                        editor.gutter.registerTextAnnotation(gutterFiller)
                    }
                }
            }
        } finally {
            suppressFillerCallback = false
        }
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

        // Sync filler with current setting (handles setting toggle and re-registration after blame close)
        syncFillerWithSetting()

        currentRanges = ranges
        gutterHighlighterManager.update(ranges)
    }

    /**
     * Clears all ranges and removes the highlighter.
     */
    @RequiresEdt
    fun clear() {
        if (disposed) return
        currentRanges = emptyList()
        gutterHighlighterManager.clear()
    }

    override fun dispose() {
        disposed = true

        ApplicationManager.getApplication().invokeLater {
            removeMouseListeners()
            clear()
        }
    }

    private fun removeMouseListeners() {
        fillerDesired = false
        ensureFillerOnAllEditors()

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
