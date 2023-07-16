package model;

import git4idea.repo.GitRepository;

public class MyModelBase {
    public TargetBranchMap targetBranchMap = null;

    public TargetBranchMap getTargetBranchMap() {
        return targetBranchMap;
    }

    public void setTargetBranchMap(TargetBranchMap targetBranch) {
        this.targetBranchMap = targetBranch;
    }

    public void addTargetBranch(GitRepository repo, String branch) {
        if (targetBranchMap == null) {
            //System.out.println("create new targetBranchMap");
            targetBranchMap = TargetBranchMap.create();
        }
        targetBranchMap.add(repo.toString(), branch);
    }
}