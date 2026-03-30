package settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.ProjectManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;
import service.ViewService;

import javax.swing.*;

/**
 * Configurable implementation for Git Scope settings.
 * This creates the settings page in IntelliJ's Settings dialog.
 */
public class GitScopeSettingsConfigurable implements Configurable {
    private GitScopeSettingsComponent settingsComponent;

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "Git Scope";
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return settingsComponent.getPreferredFocusedComponent();
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        settingsComponent = new GitScopeSettingsComponent();
        return settingsComponent.getPanel();
    }

    @Override
    public boolean isModified() {
        GitScopeSettings settings = GitScopeSettings.getInstance();
        return settingsComponent.isSeparateGutterRendering() != settings.isSeparateGutterRendering()
            || settingsComponent.isScopeTabColors() != settings.isScopeTabColors();
    }

    @Override
    public void apply() throws ConfigurationException {
        GitScopeSettings settings = GitScopeSettings.getInstance();
        boolean tabColorsChanged = settingsComponent.isScopeTabColors() != settings.isScopeTabColors();
        settings.setSeparateGutterRendering(settingsComponent.isSeparateGutterRendering());
        settings.setScopeTabColors(settingsComponent.isScopeTabColors());

        if (tabColorsChanged) {
            for (var project : ProjectManager.getInstance().getOpenProjects()) {
                if (!project.isDisposed()) {
                    ViewService viewService = project.getService(ViewService.class);
                    if (viewService != null && !viewService.isDisposed()) {
                        viewService.refreshFileColors();
                    }
                }
            }
        }
    }

    @Override
    public void reset() {
        GitScopeSettings settings = GitScopeSettings.getInstance();
        settingsComponent.setSeparateGutterRendering(settings.isSeparateGutterRendering());
        settingsComponent.setScopeTabColors(settings.isScopeTabColors());
    }

    @Override
    public void disposeUIResources() {
        settingsComponent = null;
    }
}
