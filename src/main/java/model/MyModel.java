package model;

import com.intellij.openapi.vcs.changes.Change;
import git4idea.repo.GitRepository;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import org.jetbrains.annotations.Nullable;
import service.GitService;

import java.util.Collection;
import java.util.Map;

public class MyModel extends MyModelBase {
    private final PublishSubject<MyModel.field> changeObservable = PublishSubject.create();
    private final boolean isHeadTab;
    private Collection<Change> changes;
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
        changeObservable.onNext(field.targetBranch);
    }

    public void addTargetBranch(GitRepository repo, String branch) {
        super.addTargetBranch(repo, branch);
        changeObservable.onNext(field.targetBranch);
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
    public String getCustomTabName() {
        return customTabName;
    }

    public void setCustomTabName(String customTabName) {
        this.customTabName = customTabName;
        changeObservable.onNext(field.tabName);
    }

    public Collection<Change> getChanges() {
        return changes;
    }

    public void setChanges(Collection<Change> changes) {
        this.changes = changes;
        changeObservable.onNext(field.changes);
    }

    public Observable<field> getObservable() {
        return changeObservable;
    }

    public boolean isNew() {
        TargetBranchMap targetBranchMap = getTargetBranchMap();
        if (targetBranchMap == null) {
            return true;
        }
        return targetBranchMap.getValue().isEmpty();
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean b) {
        if (b) {
            changeObservable.onNext(field.active);
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
        Map<String, String> values = branchMap.getValue();
        if (values == null || values.isEmpty()) return null;
        for (String v : values.values()) {
            if (v != null && !v.trim().isEmpty()) {
                return v;
            }
        }
        return null;
    }
}