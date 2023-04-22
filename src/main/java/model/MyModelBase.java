package model;

import com.intellij.openapi.vcs.changes.Change;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import model.valueObject.TargetBranch;

import java.util.Collection;

public class MyModelBase {
    public TargetBranch targetBranch;

    public TargetBranch getTargetBranch() {
        return targetBranch;
    }

    public void setTargetBranch(TargetBranch targetBranch) {
        this.targetBranch = targetBranch;
    }
}