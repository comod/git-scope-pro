package service;

import com.intellij.openapi.project.Project;
import example.ViewService;
import git4idea.repo.GitRepository;
import implementation.Manager;
import implementation.targetBranchWidget.MyBranchAction;
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

    public String getTargetBranchDisplay(Map<String, String> targetBranch) {

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

    public String getTargetBranchByRepositoryDisplay(GitRepository repo, Map<String, String> targetBranch) {

//        if (!isFeatureActive()) {
//            return Git.BRANCH_HEAD;
//        }

        String branch = getTargetBranchByRepository(repo, targetBranch);
        if (branch != null) {
            return branch;
        }

        return Git.BRANCH_HEAD;

    }

    public String getTargetBranchByRepository(GitRepository repo, Map<String, String> repositoryTargetBranchMap) {

        if (repositoryTargetBranchMap == null) {
            return null;
        }

        return repositoryTargetBranchMap.get(repo.toString());

    }

//
//    public Boolean isHeadActually() {
//
//        AtomicReference<Boolean> isHead = new AtomicReference<>(true);
//
//        git.getRepositories().forEach(repo -> {
//            String targetBranchByRepo = getTargetBranchByRepository(repo, t);
//            if (targetBranchByRepo != null && !targetBranchByRepo.equals(Git.BRANCH_HEAD)) {
//                isHead.set(false);
//            }
//        });
//
//        return isHead.get();
//    }


}
