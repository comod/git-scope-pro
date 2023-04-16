package service;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.repo.VcsRepositoryManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitUtil;
import git4idea.config.GitVcsSettings;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import implementation.compare.MyGitCompareWithBranchAction;

import java.util.ArrayList;
import java.util.List;

public class GitService {

    public static final String BRANCH_HEAD = "HEAD";

    private final GitRepositoryManager repositoryManager;

    private final Project project;
    private final MyGitCompareWithBranchAction myGitCompareWithBranchAction;
    private GitRepository repository;

    private VirtualFile rootGitFile;

    private boolean isReady = false;

    public GitService(Project project) {

        this.project = project;
        repositoryManager = GitRepositoryManager.getInstance(this.project);
        this.myGitCompareWithBranchAction = new MyGitCompareWithBranchAction();

        this.repoChangedListener();

    }

    public void repoChangedListener() {

//        this.project.getMessageBus().connect().subscribe(VcsRepositoryManager.VCS_REPOSITORY_MAPPING_UPDATED, () -> {
//            isReady = true;
//        });

    }

    public GitRepository getRepository() {
//        return repositoryManager.getRe;
        return DvcsUtil.guessCurrentRepositoryQuick(
                project,
                GitUtil.getRepositoryManager(project),
                GitVcsSettings.getInstance(project).getRecentRootPath()
        );

    }

//    public Boolean isMultiRoot() {
//        return repositoryManager.moreThanOneRoot();
//    }

    public List<GitRepository> getRepositories() {
        return repositoryManager.getRepositories();
    }

//    public VirtualFile getRoot() {
//        return this.repository.getRoot();
//    }

    public String getCurrentBranchName() {

        String currentBranchName = "";

        List<String> branches = new ArrayList<String>();

        this.getRepositories().forEach(repo -> {
            branches.add(repo.getCurrentBranchName());
        });

        currentBranchName = String.join(", ", branches);

        return currentBranchName;
    }
}
