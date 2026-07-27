package model;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.Nullable;
import service.GitService;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class MyModel extends MyModelBase {
    private final List<Consumer<MyModel.field>> listeners = new CopyOnWriteArrayList<>();
    private final boolean isHeadTab;
    private Collection<Change> changes; // Merged changes (scope + local)
    private Collection<Change> scopeChanges; // Scope changes only (from target branch comparison)
    private Collection<Change> localChanges; // Local changes towards HEAD only
    private Map<String, Change> changesMap; // Cached map of changes by file path
    private Map<String, Change> scopeChangesMap; // Cached map of scope changes by file path
    private Map<String, Change> localChangesMap; // Cached map of local changes by file path
    private boolean isActive;
    private String customTabName; // Added field for custom tab name

    public MyModel(boolean isHeadTab) {
        this.isHeadTab = isHeadTab;
    }

    public MyModel() {
        this.isHeadTab = false;
    }

    public boolean isHeadTab() {
        return isHeadTab;
    }

    public void setTargetBranchMap(TargetBranchMap targetBranch) {
        this.targetBranchMap = targetBranch;
        notifyListeners(field.targetBranch);
    }

    public void addTargetBranch(GitRepository repo, String branch) {
        super.addTargetBranch(repo, branch);
        notifyListeners(field.targetBranch);
    }

    /**
     * Returns the raw scope name for this model.
     * - For HEAD tab: returns "HEAD" constant.
     * - Otherwise: returns the first non-empty branch name from the map, or null if absent.
     */
    @Nullable
    public String getName() {
        if (isHeadTab) {
            return GitService.BRANCH_HEAD;
        }
        String first = getFirstBranchValue();
        return (first == null || first.isEmpty()) ? null : first;
    }

    /**
     * Returns a user-facing display name.
     * - For HEAD tab: "HEAD".
     * - If a custom tab name is set: custom name.
     * - Otherwise: the first non-empty branch name from the map (or "unknown" if none).
     */
    public String getDisplayName() {
        if (isHeadTab) {
            return "HEAD";
        }
        if (customTabName != null && !customTabName.isEmpty()) {
            return customTabName;
        }
        String first = getFirstBranchValue();
        return (first == null || first.trim().isEmpty()) ? "unknown" : first;
    }

    /**
     * Returns the scope reference similar to getName(), but strips the optional "..HEAD" suffix if present.
     * Example: "feature/foo..HEAD" -> "feature/foo"
     */
    @Nullable
    public String getScopeRef() {
        String name = getName();
        if (name == null) return null;
        String suffix = ".." + GitService.BRANCH_HEAD;
        return name.endsWith(suffix) ? name.substring(0, name.length() - suffix.length()) : name;
    }

    // Getter and setter for custom tab name
    @Override
    public String getCustomTabName() {
        return customTabName;
    }

    @Override
    public void setCustomTabName(String customTabName) {
        this.customTabName = customTabName;
        notifyListeners(field.tabName);
    }

    public Collection<Change> getChanges() {
        return changes;
    }

    public void setChanges(Collection<Change> changes) {
        this.changes = changes;
        this.changesMap = buildChangesByPathMap(changes);
        notifyListeners(field.changes);
    }

    /**
     * Sets changes with a pre-built map to avoid file system access on EDT.
     * Use this when the map is already computed on a background thread.
     */
    public void setChangesWithMap(Collection<Change> changes, Map<String, Change> changesMap) {
        this.changes = changes;
        this.changesMap = changesMap;
        notifyListeners(field.changes);
    }

    public Collection<Change> getScopeChanges() {
        return scopeChanges;
    }

    public void setScopeChanges(Collection<Change> scopeChanges) {
        this.scopeChanges = scopeChanges;
        this.scopeChangesMap = buildChangesByPathMap(scopeChanges);
    }

    /**
     * Sets scope changes with a pre-built map to avoid file system access on EDT.
     * Use this when the map is already computed on a background thread.
     */
    public void setScopeChangesWithMap(Collection<Change> scopeChanges, Map<String, Change> scopeChangesMap) {
        this.scopeChanges = scopeChanges;
        this.scopeChangesMap = scopeChangesMap;
    }

    public Collection<Change> getLocalChanges() {
        return localChanges;
    }

    public void setLocalChanges(Collection<Change> localChanges) {
        this.localChanges = localChanges;
        this.localChangesMap = buildChangesByPathMap(localChanges);
    }

    /**
     * Sets local changes with a pre-built map to avoid file system access on EDT.
     * Use this when the map is already computed on a background thread.
     */
    public void setLocalChangesWithMap(Collection<Change> localChanges, Map<String, Change> localChangesMap) {
        this.localChanges = localChanges;
        this.localChangesMap = localChangesMap;
    }

    public Map<String, Change> getChangesMap() {
        return changesMap;
    }

    public Map<String, Change> getScopeChangesMap() {
        return scopeChangesMap;
    }

    public Map<String, Change> getLocalChangesMap() {
        return localChangesMap;
    }

    /**
     * Helper method to build a HashMap from a collection of changes indexed by file path.
     * This provides O(1) lookup performance for file status checks.
     * Uses ChangesUtil.getFilePath() to properly handle deleted files (where getVirtualFile() is null).
     *
     * @param changes Collection of changes to convert to a map
     * @return Map of file path to Change, or null if changes is null
     */
    public static Map<String, Change> buildChangesByPathMap(Collection<Change> changes) {
        if (changes == null) {
            return null;
        }

        Map<String, Change> changeMap = new HashMap<>();
        for (Change change : changes) {
            String path = ChangesUtil.getFilePath(change).getPath();
            changeMap.put(path, change);
        }
        return changeMap;
    }

    public void addListener(Consumer<field> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<field> listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(field f) {
        for (Consumer<field> l : listeners) l.accept(f);
    }

    public boolean isNew() {
        TargetBranchMap targetBranchMap = getTargetBranchMap();
        if (targetBranchMap == null) {
            return true;
        }
        return targetBranchMap.value().isEmpty();
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean b) {
        if (b) {
            notifyListeners(field.active);
        }
        this.isActive = b;
    }

    public enum field {
        changes,
        active,
        targetBranch,
        tabName
    }

    // Helper: fetch the first non-empty branch value from the map (branchMapValue contains only one key anyway)
    @Nullable
    private String getFirstBranchValue() {
        TargetBranchMap branchMap = getTargetBranchMap();
        if (branchMap == null) return null;
        Map<String, String> values = branchMap.value();
        if (values == null || values.isEmpty()) return null;
        for (String v : values.values()) {
            if (v != null && !v.trim().isEmpty()) {
                return v;
            }
        }
        return null;
    }
}