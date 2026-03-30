package settings;

import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * UI component for Git Scope settings panel.
 * Provides checkbox to control gutter rendering behavior.
 */
public class GitScopeSettingsComponent {
    private final JPanel mainPanel;
    private final JBCheckBox separateGutterRenderingCheckBox;

    public GitScopeSettingsComponent() {
        // Create checkbox for gutter setting
        separateGutterRenderingCheckBox = new JBCheckBox(
            "Separate GitScope gutter rendering from IDE"
        );
        
        // Create informational label
        JBLabel descriptionLabel = new JBLabel(
            "<html>When enabled (default), GitScope markers render to the right of line numbers.<br>" +
            "When disabled, GitScope markers render merged with IDE gutter markers (further right).</html>"
        );
        descriptionLabel.setFontColor(com.intellij.util.ui.UIUtil.FontColor.BRIGHTER);

        // Build the form
        mainPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(new JBLabel("Gutter Rendering:"), new JPanel())
            .addComponent(separateGutterRenderingCheckBox, 5)
            .addComponent(descriptionLabel, 10)
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
}
