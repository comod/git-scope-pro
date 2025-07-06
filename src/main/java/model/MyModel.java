package model;

import com.intellij.openapi.vcs.changes.Change;
import git4idea.repo.GitRepository;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class MyModel extends MyModelBase {
    private final PublishSubject<MyModel.field> changeObservable = PublishSubject.create();
    private final boolean isHeadTab;
    private Collection<Change> changes;
    private boolean isActive;

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

    public String getDisplayName() {
        if (isHeadTab) {
            return "HEAD";
        }

        TargetBranchMap branchMap = getTargetBranchMap();
        if (branchMap == null) {
            return "unknown";
        }

        Map<String, String> branchMapValue = branchMap.getValue();
        if (branchMapValue == null || branchMapValue.isEmpty()) {
            return "unknown";
        }

        // Join all branch names with commas if there are multiple
        List<String> branchNames = new ArrayList<>();
        for (String branch : branchMapValue.values()) {
            if (branch != null && !branch.trim().isEmpty()) {
                branchNames.add(branch);
            }
        }
        if (branchNames.isEmpty()) {
            return "unknown";
        }
        return String.join(", ", branchNames);
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
        targetBranch
    }
}