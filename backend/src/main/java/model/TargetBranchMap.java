package model;

import java.util.HashMap;
import java.util.Map;

/**
 * @param value Repo, BranchToCompare
 */
public record TargetBranchMap(Map<String, String> value) {

    public static TargetBranchMap create() {
        Map<String, String> map = new HashMap<>();
        return new TargetBranchMap(map);
    }

    public void add(String repo, String branch) {
        this.value.put(repo, branch);
    }
}
