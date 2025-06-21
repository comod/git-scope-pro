package service;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitRemoteBranch;
import toolwindow.elements.BranchTreeEntry;
import git4idea.GitLocalBranch;
import git4idea.GitUtil;
import git4idea.branch.GitBranchType;
import git4idea.config.GitVcsSettings;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.ui.branch.GitBranchManager;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import git4idea.GitTag;

import java.util.*;

public class GitService {
    public static final String BRANCH_HEAD = "HEAD";
    public static final String TAB_NAME_HEAD = "~";
    public static final Comparator<BranchTreeEntry> FAVORITE_BRANCH_COMPARATOR = Comparator.comparing(branch -> branch.isFav() ? -1 : 0);
    private final GitRepositoryManager repositoryManager;
    private final Project project;
    private final GitBranchManager gitBranchManager;

    public GitService(Project project) {

        this.project = project;
        repositoryManager = GitRepositoryManager.getInstance(this.project);
        this.gitBranchManager = project.getService(GitBranchManager.class);

    }

    @NotNull
    public List<BranchTreeEntry> listOfLocalBranches(GitRepository repo) {
        Collection<GitLocalBranch> branches = repo.getBranches().getLocalBranches();
        return StreamEx.of(branches)
//                .filter(branch -> !branch.equals(currentBranch))
                .map(branch -> {
                    String name = branch.getName();
                    boolean isFav = gitBranchManager.isFavorite(GitBranchType.LOCAL, repo, name);
                    return BranchTreeEntry.create(name, isFav, repo);
                })
                .sorted((b1, b2) -> {
                    int delta = FAVORITE_BRANCH_COMPARATOR.compare(b1, b2);
                    if (delta != 0) return delta;
                    return StringUtil.naturalCompare(b1.getName(), b2.getName());
                })
                .toList();
    }

    @NotNull
    public List<BranchTreeEntry> listOfRemoteBranches(GitRepository repo) {
        Collection<GitRemoteBranch> branches = repo.getBranches().getRemoteBranches();
        return StreamEx.of(branches)
//                .filter(branch -> !branch.equals(currentBranch))
                .map(branch -> {
                    String name = branch.getName();
                    boolean isFav = gitBranchManager.isFavorite(GitBranchType.REMOTE, repo, name);
                    return BranchTreeEntry.create(name, isFav, repo);
                })
                .sorted((b1, b2) -> {
                    int delta = FAVORITE_BRANCH_COMPARATOR.compare(b1, b2);
                    if (delta != 0) return delta;
                    return StringUtil.naturalCompare(b1.getName(), b2.getName());
                })
                .toList();
    }

    public List<GitRepository> getRepositories() {
        return repositoryManager.getRepositories();
    }

    public String getCurrentBranchName() {
        List<String> branches = new ArrayList<String>();

        this.getRepositories().forEach(repo -> {
            String currentBranchName = repo.getCurrentBranchName();
            if (!Objects.equals(currentBranchName, GitService.BRANCH_HEAD)) {
                branches.add(currentBranchName);
            }
        });

        return String.join(", ", branches);
    }

    public boolean isMulti() {
        return repositoryManager.moreThanOneRoot();
    }
}
