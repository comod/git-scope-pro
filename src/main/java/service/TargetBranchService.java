package service;

import com.intellij.openapi.project.Project;
import example.ViewService;
import git4idea.repo.GitRepository;
import implementation.Manager;
import implementation.targetBranchWidget.MyBranchAction;
import model.valueObject.TargetBranch;
import state.State;
import ui.ToolWindowUI;
import utils.Git;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static java.util.stream.Collectors.joining;

public class TargetBranchService {

    private final GitService git;

    public TargetBranchService(Project project) {
        this.git = project.getService(GitService.class);
    }

    public String getTargetBranchDisplay(TargetBranch targetBranch) {

        Map<String, String> list = new HashMap<>();

        git.getRepositories().forEach(repo -> {
            String targetBranchByRepo = getTargetBranchByRepositoryDisplay(repo, targetBranch);
            list.put(targetBranchByRepo, targetBranchByRepo);
        });

        return list
                .entrySet()
                .stream()
                .map(Map.Entry::getValue)
                .collect(joining(", "));

    }

    public String getTargetBranchByRepositoryDisplay(GitRepository repo, TargetBranch targetBranch) {

//        if (!isFeatureActive()) {
//            return Git.BRANCH_HEAD;
//        }

        String branch = getTargetBranchByRepository(repo, targetBranch);
        if (branch != null) {
            return branch;
        }

        return Git.BRANCH_HEAD;

    }

    public String getTargetBranchByRepository(GitRepository repo, TargetBranch repositoryTargetBranchMap) {

        if (repositoryTargetBranchMap == null) {
            return null;
        }

        return repositoryTargetBranchMap.getValue().get(repo.toString());

    }

    public Boolean isHeadActually(TargetBranch targetBranchByRepo) {

        AtomicReference<Boolean> isHead = new AtomicReference<>(true);

        // repo, git, changes, targetbranch cleanup @todo
        git.getRepositories().forEach(repo -> {
            String branchToCompare = targetBranchByRepo.getValue().get(repo.toString());
//            String targetBranchByRepo = getTargetBranchByRepository(repo, t);
            if (branchToCompare != null && !branchToCompare.equals(Git.BRANCH_HEAD)) {
                isHead.set(false);
            }
        });

        return isHead.get();
    }


}
