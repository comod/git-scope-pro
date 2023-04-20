package model;

import com.intellij.openapi.vcs.changes.Change;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import model.valueObject.TargetBranch;

import java.util.Collection;

public class MyModel {
    private final PublishSubject<MyModel.field> changeObservable = PublishSubject.create();
    private String field1;
    private TargetBranch targetBranch;
    private Collection<Change> changes;

    public String getField1() {
        return field1;
    }

    public void setField1(String field1) {
        this.field1 = field1;
        changeObservable.onNext(field.field1);
    }

    public TargetBranch getTargetBranch() {
        return targetBranch;
    }

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

    @Override
    public String toString() {
        return "MyModel{" +
                "field1='" + field1 + '\'' +
                '}';
    }

    public enum field {
        field1,
        changes,
        targetBranch
    }
}