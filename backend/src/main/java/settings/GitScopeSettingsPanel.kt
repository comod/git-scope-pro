package settings

import com.intellij.application.options.colors.ColorAndFontOptions
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.labelTable
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.util.ui.update.UiNotifyConnector
import service.ReviewedFilesService
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JSlider

/** UI for the Git Scope settings page, built with the Kotlin UI DSL so it lays out like the platform's own pages. */
class GitScopeSettingsPanel {
    private lateinit var separateGutterRenderingCheckBox: JBCheckBox
    private lateinit var fadeGutterMarkersCheckBox: JBCheckBox
    private lateinit var gutterMarkerOpacitySlider: JSlider
    private lateinit var scopeFileColorsCheckBox: JBCheckBox
    private lateinit var showUntrackedFilesCheckBox: JBCheckBox
    private lateinit var showDeletedFilesCheckBox: JBCheckBox
    private lateinit var navigationSkipsHiddenFilesCheckBox: JBCheckBox
    private lateinit var clearReviewedButton: JButton
    private lateinit var reviewedStatsLabel: JLabel

    val panel: DialogPanel = panel {
        group("Gutter Rendering") {
            row {
                separateGutterRenderingCheckBox = checkBox("Separate Git Scope and IDE gutter rendering")
                    .comment("When enabled, Git Scope gutter markers are rendered to the left of the line numbers")
                    .component
            }
            lateinit var fadeCell: Cell<JBCheckBox>
            row {
                fadeCell = checkBox("Fade Git Scope gutter markers")
                    .comment("Paints Git Scope gutter markers semi-transparent so they stand back from the IDE's own markers")
                fadeGutterMarkersCheckBox = fadeCell.component
            }
            indent {
                row("Opacity:") {
                    gutterMarkerOpacitySlider = slider(
                        GitScopeSettings.MIN_GUTTER_MARKER_OPACITY, GitScopeSettings.MAX_GUTTER_MARKER_OPACITY,
                        0, 10
                    )
                        .labelTable(opacityLabels())
                        .applyToComponent { snapToTicks = true }
                        .component
                }.enabledIf(fadeCell.selected)
            }
            row {
                // The page is registered by the frontend module, so it is looked up by display name.
                link("Customize marker colors in Editor | Color Scheme | Git Scope") { e ->
                    ColorAndFontOptions.selectOrEditColor(
                        DataManager.getInstance().getDataContext(e.source as JComponent), null, "Git Scope"
                    )
                }
            }
        }
        group("File Colors") {
            row {
                scopeFileColorsCheckBox = checkBox("Color files based on Git Scope")
                    .comment("When enabled (default), project and editor file colors reflect the active Git Scope")
                    .component
            }
        }
        group("Working Tree (default for new projects)") {
            row {
                showUntrackedFilesCheckBox = checkBox("Display untracked files")
                    .comment("Initial state of the \"Show Untracked Files\" toggle in the Git Scope window")
                    .component
            }
            row {
                showDeletedFilesCheckBox = checkBox("Display deleted files")
                    .comment("Initial state of the \"Show Deleted Files\" toggle in the Git Scope window")
                    .component
            }
        }
        group("Review") {
            row {
                navigationSkipsHiddenFilesCheckBox = checkBox("Navigation skips reviewed files hidden from the tree").component
            }
            row {
                clearReviewedButton = button("Clear All Reviewed Marks") { clearReviewedMarks() }.component
                reviewedStatsLabel = label("").component
            }
        }
    }.also { UiNotifyConnector.doWhenFirstShown(it) { updateReviewStats() } }

    /** The project of the Settings dialog; the page is application-level, so the project comes from the UI context. */
    private fun reviewProject(): Project? =
        CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(clearReviewedButton))
            ?.takeUnless { it.isDefault }

    /** Runs when the page is first shown: the Settings dialog gives the project only then, after reset(). */
    private fun updateReviewStats() {
        val project = reviewProject()
        clearReviewedButton.isEnabled = project != null
        reviewedStatsLabel.text =
            project?.let { "${it.getService(ReviewedFilesService::class.java).stats()} (project ${it.name})" }
                ?: "No project open"
    }

    private fun clearReviewedMarks() {
        val project = reviewProject() ?: return
        val question = "Clear all reviewed marks of project ${project.name}?"
        if (Messages.showYesNoDialog(panel, question, "Clear Reviewed Marks", null) != Messages.YES) return
        val reviewed = project.getService(ReviewedFilesService::class.java)
        reviewed.clearAll()
        reviewed.refreshTree()
        updateReviewStats()
    }

    private fun opacityLabels(): Map<Int, JLabel> =
        (GitScopeSettings.MIN_GUTTER_MARKER_OPACITY..GitScopeSettings.MAX_GUTTER_MARKER_OPACITY step 20)
            .associateWith { JLabel("$it%") }

    val preferredFocusedComponent: JComponent
        get() = separateGutterRenderingCheckBox

    var isSeparateGutterRendering: Boolean
        get() = separateGutterRenderingCheckBox.isSelected
        set(value) { separateGutterRenderingCheckBox.isSelected = value }

    var isFadeGutterMarkers: Boolean
        get() = fadeGutterMarkersCheckBox.isSelected
        set(value) { fadeGutterMarkersCheckBox.isSelected = value }

    var gutterMarkerOpacity: Int
        get() = gutterMarkerOpacitySlider.value
        set(value) { gutterMarkerOpacitySlider.value = value }

    var isScopeFileColors: Boolean
        get() = scopeFileColorsCheckBox.isSelected
        set(value) { scopeFileColorsCheckBox.isSelected = value }

    var isShowUntrackedFiles: Boolean
        get() = showUntrackedFilesCheckBox.isSelected
        set(value) { showUntrackedFilesCheckBox.isSelected = value }

    var isShowDeletedFiles: Boolean
        get() = showDeletedFilesCheckBox.isSelected
        set(value) { showDeletedFilesCheckBox.isSelected = value }

    var isNavigationSkipsHiddenFiles: Boolean
        get() = navigationSkipsHiddenFilesCheckBox.isSelected
        set(value) { navigationSkipsHiddenFilesCheckBox.isSelected = value }
}
