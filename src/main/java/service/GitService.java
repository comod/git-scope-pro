package service;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import example.BranchTreeEntry;
import git4idea.GitLocalBranch;
import git4idea.GitUtil;
import git4idea.branch.GitBranchType;
import git4idea.config.GitVcsSettings;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.ui.branch.GitBranchManager;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class GitService {

    public static final String BRANCH_HEAD = "HEAD";
    public static final Comparator<BranchTreeEntry> FAVORITE_BRANCH_COMPARATOR = Comparator.comparing(branch -> branch.isFav() ? -1 : 0);
    private final GitRepositoryManager repositoryManager;
    private final Project project;
    //    private final MyGitCompareWithBranchAction myGitCompareWithBranchAction;
    private final GitBranchManager gitBranchManager;
    private GitRepository repository;
    private VirtualFile rootGitFile;
    private boolean isReady = false;

    public GitService(Project project) {

        this.project = project;
        repositoryManager = GitRepositoryManager.getInstance(this.project);
        this.gitBranchManager = project.getService(GitBranchManager.class);
//        this.myGitCompareWithBranchAction = new MyGitCompareWithBranchAction();

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

//    public GitBranchesCollection branchesCollection() {
//        return getRepository().getBranches();
//    }

    //    public List<FavLabel> iconLabelListOfLocalBranches() {
//        Collection<GitLocalBranch> remoteBranches = branchesCollection().getLocalBranches();
//        return StreamEx.of(remoteBranches)
//                .map(GitLocalBranch::getName)
//                .sorted(StringUtil::naturalCompare)
//                .map(remoteName -> {
//                    boolean isFav = gitBranchManager.isFavorite(GitBranchType.LOCAL, getRepository(), remoteName);
//                    return new FavLabel(remoteName, isFav);
//                })
//                .toList();
//    }
    @NotNull
    public List<BranchTreeEntry> iconLabelListOfLocalBranches(GitRepository repo) {
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
//    public List<IconLabel> iconLabelListOfRemoteBranches() {
//        Collection<GitRemoteBranch> remoteBranches = branchesCollection().getRemoteBranches();
//        return StreamEx.of(remoteBranches)
//                .map(GitRemoteBranch::getName)
//                .sorted(StringUtil::naturalCompare)
//                .map(remoteName -> new IconLabel(remoteName, AllIcons.Vcs.Branch))
//                .toList();
//    }

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
        List<String> branches = new ArrayList<String>();

        this.getRepositories().forEach(repo -> {
            String currentBranchName = repo.getCurrentBranchName();
            if (!Objects.equals(currentBranchName, "HEAD")) {
                branches.add(currentBranchName);
            }
        });

        return String.join(", ", branches);
    }

    public boolean isMulti() {
        return repositoryManager.moreThanOneRoot();
    }
}
