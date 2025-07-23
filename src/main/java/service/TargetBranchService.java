package service;

import com.intellij.openapi.project.Project;
import git4idea.repo.GitRepository;
import model.TargetBranchMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class TargetBranchService {

    private final GitService gitService;

    public TargetBranchService(Project project) {
        this.gitService = project.getService(GitService.class);
    }

    /**
     * Asynchronously gets the target branch display string
     * @param targetBranch the target branch map
     * @param callback consumer to receive the result
     */
    public void getTargetBranchDisplayAsync(TargetBranchMap targetBranch, Consumer<String> callback) {
        if (targetBranch == null) {
            callback.accept(GitService.BRANCH_HEAD);
            return;
        }

        gitService.getRepositoriesAsync(repositories -> {
            List<String> branches = new ArrayList<>();

            repositories.forEach(repo -> {
                String currentBranchName = getTargetBranchByRepositoryDisplay(repo, targetBranch);

                if (!Objects.equals(currentBranchName, GitService.BRANCH_HEAD)) {
                    branches.add(currentBranchName);
                }
            });

            callback.accept(String.join(", ", branches));
        });
    }

    public String getTargetBranchDisplay(TargetBranchMap targetBranch) {
        if (targetBranch == null) {
            return GitService.BRANCH_HEAD;
        }
        List<String> branches = new ArrayList<>();
        gitService.getRepositoriesAsync(repositories -> {
            repositories.forEach(repo -> {
                String currentBranchName = getTargetBranchByRepositoryDisplay(repo, targetBranch);

                if (!Objects.equals(currentBranchName, GitService.BRANCH_HEAD)) {
                    branches.add(currentBranchName);
                }
            });
        });
        return String.join(", ", branches);
    }

    public String getTargetBranchByRepositoryDisplay(GitRepository repo, TargetBranchMap targetBranch) {

        String branch = getTargetBranchByRepository(repo, targetBranch);
        if (branch != null) {
            return branch;
        }

        return GitService.BRANCH_HEAD;

    }

    public String getTargetBranchByRepository(GitRepository repo, TargetBranchMap repositoryTargetBranchMap) {

        if (repositoryTargetBranchMap == null) {
            return null;
        }

        return repositoryTargetBranchMap.getValue().get(repo.toString());

    }
}