package settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Application-level settings for Git Scope plugin.
 * Settings are persisted across IDE restarts.
 */
@State(
    name = "GitScopeSettings",
    storages = @Storage("gitScopeSettings.xml")
)
public class GitScopeSettings implements PersistentStateComponent<GitScopeSettings> {
    
    /**
     * If true, plugin gutter markers render separately to the right of line numbers.
     * If false, plugin gutter markers render merged with IDE gutters (further right).
     * Default: false (merged with IDE gutter markers)
     */
    public boolean separateGutterRendering = false;

    /**
     * If true, editor tab colors reflect the active Git Scope diff (scope vs. base branch).
     * If false, the IDE's default tab coloring (diff from HEAD) is used.
     * Default: true (scope-based tab colors)
     */
    public boolean scopeTabColors = true;

    public static GitScopeSettings getInstance() {
        return ApplicationManager.getApplication().getService(GitScopeSettings.class);
    }

    @Nullable
    @Override
    public GitScopeSettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull GitScopeSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    // Getters and setters
    public boolean isSeparateGutterRendering() {
        return separateGutterRendering;
    }

    public void setSeparateGutterRendering(boolean separateGutterRendering) {
        this.separateGutterRendering = separateGutterRendering;
    }

    public boolean isScopeTabColors() {
        return scopeTabColors;
    }

    public void setScopeTabColors(boolean scopeTabColors) {
        this.scopeTabColors = scopeTabColors;
    }
}
