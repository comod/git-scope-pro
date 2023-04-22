package model;

import com.intellij.openapi.vcs.changes.Change;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import model.valueObject.TargetBranch;

import java.util.Collection;

public class MyModel extends MyModelBase {
    private final PublishSubject<MyModel.field> changeObservable = PublishSubject.create();
    private Collection<Change> changes;

    public void setTargetBranch(TargetBranch targetBranch) {
        this.targetBranch = targetBranch;
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

    public enum field {
        changes,
        targetBranch
    }
}