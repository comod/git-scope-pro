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

    public GitScopeSettingsComponent() {
        separateGutterRenderingCheckBox = new JBCheckBox(
            "Separate Git Scope and IDE gutter rendering"
        );

        scopeFileColorsCheckBox = new JBCheckBox(
            "Color files based on Git Scope"
        );

        mainPanel = FormBuilder.createFormBuilder()
            .addComponent(new TitledSeparator("Gutter Rendering"))
            .addComponent(separateGutterRenderingCheckBox, 1)
            .addTooltip("When enabled, GitScope gutter markers is rendered to the left of the line numbers. ")
            .addVerticalGap(10)
            .addComponent(new TitledSeparator("File Colors"))
            .addComponent(scopeFileColorsCheckBox, 1)
            .addTooltip("When enabled (default), project and editor file colors reflect the active Git Scope")
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
}
