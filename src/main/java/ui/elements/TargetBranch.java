package ui.elements;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
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

public class TargetBranch {

    private final State state;
    //    private final ToolWindowUI toolWindowUI;
    private final Git git;
    private final Manager manager;

    private Boolean isFeatureActive = false;

    public TargetBranch(Project project, State state, Git git, ToolWindowUI toolWindowUI) {

        this.state = state;
        this.git = git;
//        this.toolWindowUI = toolWindowUI;
        this.manager = project.getService(Manager.class);

    }

    public Map<String, String> getRepositoryTargetBranchMap() {
        return state.getRepositoryTargetBranchMap();
    }

    public Boolean isFeatureActive() {

        ToolWindowUI toolWindowUI = getToolWindowUI();
        if (toolWindowUI != null) {
            return toolWindowUI.isFeatureActive();
        }

        return isFeatureActive;
    }

    public void setFeatureActive(Boolean featureActive) {

        ToolWindowUI toolWindowUI = getToolWindowUI();
        if (toolWindowUI != null) {
            toolWindowUI.setFeatureActive(featureActive);
        }

        isFeatureActive = featureActive;
    }

    public void toggleFeature() {
        this.setFeatureActive(!this.isFeatureActive());
    }

    public void initOnOpenToolWindow() {
        setFeatureActive(isFeatureActive);
    }

    public ToolWindowUI getToolWindowUI() {
        return manager.getToolWindowUI();
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

        if (!isFeatureActive()) {
            return Git.BRANCH_HEAD;
        }

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

    public void targetBranchListener(MyBranchAction myBranchAction, Consumer<Void> callback) {

        if (myBranchAction.getBranchName().equals(Git.BRANCH_HEAD)) {
            setFeatureActive(false);
            callback.accept(null);
            return;
        }

        Map<String, String> repositoryTargetBranchMap = getRepositoryTargetBranchMap();
        repositoryTargetBranchMap.put(myBranchAction.getRepoName(), myBranchAction.getBranchName());
        state.setRepositoryTargetBranchMap(repositoryTargetBranchMap);

        setFeatureActive(!isHeadActually());

        callback.accept(null);

    }

}
