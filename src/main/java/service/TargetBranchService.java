package service;

import com.intellij.openapi.project.Project;
import git4idea.repo.GitRepository;
import model.TargetBranchMap;
//import utils.Git;

import java.util.*;

import static java.util.stream.Collectors.joining;

public class TargetBranchService {

    private final GitService gitService;

    public TargetBranchService(Project project) {
        this.gitService = project.getService(GitService.class);
    }

    public String getTargetBranchDisplay(TargetBranchMap targetBranch) {
        if (targetBranch == null) {
            return GitService.BRANCH_HEAD;
        }
        List<String> branches = new ArrayList<String>();

        gitService.getRepositories().forEach(repo -> {
            String currentBranchName = getTargetBranchByRepositoryDisplay(repo, targetBranch);

            if (!Objects.equals(currentBranchName, GitService.BRANCH_HEAD)) {
                branches.add(currentBranchName);
            }
        });

        return String.join(", ", branches);
    }

    public String getTargetBranchByRepositoryDisplay(GitRepository repo, TargetBranchMap targetBranch) {

//        if (!isFeatureActive()) {
//            return Git.BRANCH_HEAD;
//        }

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
//
//    public Boolean isHeadActually(TargetBranchMap targetBranchByRepo) {
//
//        AtomicReference<Boolean> isHead = new AtomicReference<>(true);
//
//        // repo, git, changes, targetbranch cleanup @todo
//        gitService.getRepositories().forEach(repo -> {
//            String branchToCompare = targetBranchByRepo.getValue().get(repo.toString());
////            String targetBranchByRepo = getTargetBranchByRepository(repo, t);
//            if (branchToCompare != null && !branchToCompare.equals(Git.BRANCH_HEAD)) {
//                isHead.set(false);
//            }
//        });
//
//        return isHead.get();
//    }


}
