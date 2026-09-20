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
    public boolean scopeFileColors = true;

    /**
     * Initial value of the "Show Untracked Files" toggle (Git Scope window toolbar) for a
     * project opened for the first time. Already-open projects keep their own toggle state;
     * changing this has no effect on them.
     * Default: false
     */
    public boolean showUntrackedFiles = false;

    /**
     * Initial value of the "Show Deleted Files" toggle (Git Scope window toolbar) for a
     * project opened for the first time. Already-open projects keep their own toggle state;
     * changing this has no effect on them.
     * Default: false
     */
    public boolean showDeletedFiles = false;

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

    public boolean isScopeFileColors() {
        return scopeFileColors;
    }

    public void setScopeFileColors(boolean scopeFileColors) {
        this.scopeFileColors = scopeFileColors;
    }

    public boolean isShowUntrackedFiles() {
        return showUntrackedFiles;
    }

    public void setShowUntrackedFiles(boolean showUntrackedFiles) {
        this.showUntrackedFiles = showUntrackedFiles;
    }

    public boolean isShowDeletedFiles() {
        return showDeletedFiles;
    }

    public void setShowDeletedFiles(boolean showDeletedFiles) {
        this.showDeletedFiles = showDeletedFiles;
    }
}
