package service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitRemoteBranch;
import toolwindow.elements.BranchTreeEntry;
import git4idea.GitLocalBranch;
import git4idea.branch.GitBranchType;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.ui.branch.GitBranchManager;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;

public class GitService {
    public static final String BRANCH_HEAD = "HEAD";
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

    /**
     * Retrieves repositories asynchronously and calls the consumer with the result on the EDT
     * @param callback Consumer to receive the list of repositories
     */
    public void getRepositoriesAsync(Consumer<List<GitRepository>> callback) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            List<GitRepository> repositories = repositoryManager.getRepositories();
            String basePath = project.getBasePath();
            GitRepository mainRepo = null;
            if (basePath != null) {
                VirtualFile projectRoot = LocalFileSystem.getInstance().findFileByPath(basePath);
                if (projectRoot != null) {
                    mainRepo = repositoryManager.getRepositoryForRoot(projectRoot);
                }
            }

            List<GitRepository> ordered = new ArrayList<>(repositories.size());
            if (mainRepo != null) {
                ordered.add(mainRepo);
                for (GitRepository repo : repositories) {
                    if (!repo.equals(mainRepo)) {
                        ordered.add(repo);
                    }
                }
            } else {
                ordered.addAll(repositories);
            }

            final List<GitRepository> result = ordered;
            ApplicationManager.getApplication().invokeLater(() -> {
                if (!project.isDisposed()) {
                    callback.accept(result);
                }
            });
        });
    }

    public List<GitRepository> getRepositories() {
        // Return main repo first (probably most frequently used)
        List<GitRepository> repositories = repositoryManager.getRepositories();
        String basePath = project.getBasePath();
        GitRepository mainRepo = null;
        if (basePath != null) {
            VirtualFile projectRoot = LocalFileSystem.getInstance().findFileByPath(basePath);
            mainRepo = projectRoot == null ? null : repositoryManager.getRepositoryForFile(projectRoot);
        }

        if (mainRepo == null) {
            return repositories;
        }

        List<GitRepository> ordered = new ArrayList<>(repositories.size());
        ordered.add(mainRepo);
        for (GitRepository repo : repositories) {
            if (!repo.equals(mainRepo)) {
                ordered.add(repo);
            }
        }
        return ordered;
    }

    public String getCurrentBranchName() {
        List<String> branches = new ArrayList<>();

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

    public void getCurrentBranchNameAsync(Consumer<String> callback) {
        getRepositoriesAsync(repositories -> {
            List<String> branches = new ArrayList<>();
            repositories.forEach(repo -> {
                String currentBranchName = repo.getCurrentBranchName();
                if (!Objects.equals(currentBranchName, GitService.BRANCH_HEAD)) {
                    branches.add(currentBranchName);
                }
            });
            callback.accept(String.join(", ", branches));
        });
    }
}