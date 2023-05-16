package model.valueObject;

import java.util.HashMap;
import java.util.Map;

public class TargetBranchMap {
    /**
     * Repo, BranchToCompare
     **/
    public final Map<String, String> value;

    public TargetBranchMap(Map<String, String> targetBranch) {
        this.value = targetBranch;
    }

    public static TargetBranchMap create() {
        Map<String, String> map = new HashMap<>();
        return new TargetBranchMap(map);
    }

    public Map<String, String> getValue() {
        return value;
    }

    public void add(String repo, String branch) {
        this.value.put(repo, branch);
    }
}
