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
    private final ViewService viewService;

    public TargetBranchService(Project project) {
        this.git = project.getService(GitService.class);
        this.viewService = project.getService(ViewService.class);
    }

    public Map<String, String> getRepositoryTargetBranchMap() {
        return this.viewService.getCurrent().getTargetBranch();
    }

    public String getTargetBranchDisplay() {

        Map<String, String> list = new HashMap<>();

        git.getRepositories().forEach(repo -> {
            String targetBranchByRepo = getTargetBranchByRepositoryDisplay(repo);
            list.put(targetBranchByRepo, targetBranchByRepo);
        });

        return list
                .entrySet()
                .stream()
                .map(Map.Entry::getValue)
                .collect(joining(", "));

    }

    public String getTargetBranchByRepositoryDisplay(GitRepository repo) {

//        if (!isFeatureActive()) {
//            return Git.BRANCH_HEAD;
//        }

        String branch = getTargetBranchByRepository(repo);
        if (branch != null) {
            return branch;
        }

        return Git.BRANCH_HEAD;

    }

    public String getTargetBranchByRepository(GitRepository repo) {

        Map<String, String> repositoryTargetBranchMap = getRepositoryTargetBranchMap();

        if (repositoryTargetBranchMap == null) {
            return null;
        }

        return repositoryTargetBranchMap.get(repo.toString());

    }


    public Boolean isHeadActually() {

        AtomicReference<Boolean> isHead = new AtomicReference<>(true);

        git.getRepositories().forEach(repo -> {
            String targetBranchByRepo = getTargetBranchByRepository(repo);
            if (targetBranchByRepo != null && !targetBranchByRepo.equals(Git.BRANCH_HEAD)) {
                isHead.set(false);
            }
        });

        return isHead.get();
    }


}
