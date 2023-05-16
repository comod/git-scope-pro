package model;

import com.intellij.openapi.vcs.changes.Change;
import git4idea.repo.GitRepository;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import model.valueObject.TargetBranchMap;

import java.util.Collection;

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
        changeObservable.onNext(field.active);
        this.isActive = b;
    }

    public enum field {
        changes,
        active,
        targetBranch
    }
}