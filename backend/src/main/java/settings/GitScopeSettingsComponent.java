package settings;

import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;

import javax.swing.*;

/**
 * UI component for Git Scope settings panel.
 * Provides checkbox to control gutter rendering behavior.
 */
public class GitScopeSettingsComponent {
    private final JPanel mainPanel;
    private final JBCheckBox separateGutterRenderingCheckBox;
    private final JBCheckBox scopeFileColorsCheckBox;
    private final JBCheckBox showUntrackedFilesCheckBox;
    private final JBCheckBox showDeletedFilesCheckBox;

    public GitScopeSettingsComponent() {
        separateGutterRenderingCheckBox = new JBCheckBox(
            "Separate Git Scope and IDE gutter rendering"
        );

        scopeFileColorsCheckBox = new JBCheckBox(
            "Color files based on Git Scope"
        );

        showUntrackedFilesCheckBox = new JBCheckBox(
            "Display untracked files"
        );

        showDeletedFilesCheckBox = new JBCheckBox(
            "Display deleted files"
        );

        mainPanel = FormBuilder.createFormBuilder()
            .addComponent(new TitledSeparator("Gutter Rendering"))
            .addComponent(separateGutterRenderingCheckBox, 1)
            .addTooltip("When enabled, Git Scope gutter markers is rendered to the left of the line numbers. ")
            .addVerticalGap(10)
            .addComponent(new TitledSeparator("File Colors"))
            .addComponent(scopeFileColorsCheckBox, 1)
            .addTooltip("When enabled (default), project and editor file colors reflect the active Git Scope")
            .addVerticalGap(10)
            .addComponent(new TitledSeparator("Working Tree (default for new projects)"))
            .addComponent(showUntrackedFilesCheckBox, 1)
            .addTooltip("Initial state of the \"Show Untracked Files\" toggle in the Git Scope window")
            .addComponent(showDeletedFilesCheckBox, 1)
            .addTooltip("Initial state of the \"Show Deleted Files\" toggle in the Git Scope window")
            .addComponentFillVertically(new JPanel(), 0)
            .getPanel();

        mainPanel.setBorder(JBUI.Borders.empty(10));
    }

    public JPanel getPanel() {
        return mainPanel;
    }

    public JComponent getPreferredFocusedComponent() {
        return separateGutterRenderingCheckBox;
    }

    public boolean isSeparateGutterRendering() {
        return separateGutterRenderingCheckBox.isSelected();
    }

    public void setSeparateGutterRendering(boolean value) {
        separateGutterRenderingCheckBox.setSelected(value);
    }

    public boolean isScopeFileColors() {
        return scopeFileColorsCheckBox.isSelected();
    }

    public void setScopeFileColors(boolean value) {
        scopeFileColorsCheckBox.setSelected(value);
    }

    public boolean isShowUntrackedFiles() {
        return showUntrackedFilesCheckBox.isSelected();
    }

    public void setShowUntrackedFiles(boolean value) {
        showUntrackedFilesCheckBox.setSelected(value);
    }

    public boolean isShowDeletedFiles() {
        return showDeletedFilesCheckBox.isSelected();
    }

    public void setShowDeletedFiles(boolean value) {
        showDeletedFilesCheckBox.setSelected(value);
    }
}
