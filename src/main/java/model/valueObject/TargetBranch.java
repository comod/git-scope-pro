package model.valueObject;

import java.util.Map;

public class TargetBranch {
    public final Map<String, String> value;

    public TargetBranch(Map<String, String> targetBranch) {
        this.value = targetBranch;
    }

    public Map<String, String> getValue() {
        return value;
    }
}
