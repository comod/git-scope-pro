package service;

import com.intellij.openapi.project.Project;
import git4idea.repo.GitRepository;
import model.TargetBranchMap;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
            Set<String> branches = new LinkedHashSet<>();

            int repositoryCount = repositories.size();
            repositories.forEach(repo -> {
                String currentBranchName = getTargetBranchByRepositoryDisplay(repo, targetBranch, repositoryCount);

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
        Set<String> branches = new LinkedHashSet<>();
        gitService.getRepositoriesAsync(repositories -> {
            int repositoryCount = repositories.size();
            repositories.forEach(repo -> {
                String currentBranchName = getTargetBranchByRepositoryDisplay(repo, targetBranch, repositoryCount);

                if (!Objects.equals(currentBranchName, GitService.BRANCH_HEAD)) {
                    branches.add(currentBranchName);
                }
            });
        });
        return String.join(", ", branches);
    }

    public String getTargetBranchByRepositoryDisplay(GitRepository repo, TargetBranchMap targetBranch, int repositoryCount) {

        String branch = getTargetBranchByRepository(repo, targetBranch, repositoryCount);
        if (branch != null) {
            return branch;
        }

        return GitService.BRANCH_HEAD;

    }

    public String getTargetBranchByRepository(GitRepository repo, TargetBranchMap repositoryTargetBranchMap, int repositoryCount) {

        if (repositoryTargetBranchMap == null) {
            return null;
        }

        return repositoryTargetBranchMap.resolve(repo, repositoryCount);

    }
}