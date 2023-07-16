package toolwindow.elements;

import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

public class BranchTreeEntry {
    private final String name;
    private final boolean isFav;
    private final boolean isNode;
    private final GitRepository gitRepo;

    public BranchTreeEntry(
            String name,
            boolean isFav,
            boolean isNode,
            GitRepository gitRepo
    ) {
        this.name = name;
        this.isFav = isFav;
        this.isNode = isNode;
        this.gitRepo = gitRepo;
    }

    public static BranchTreeEntry create(
            String name,
            boolean isFav,
            GitRepository gitRepo
    ) {
        return new BranchTreeEntry(name, isFav, true, gitRepo);
    }

    public static BranchTreeEntry create(
            String name
    ) {
        return new BranchTreeEntry(name, false, false, null);
    }

    public boolean isFav() {
        return isFav;
    }

    public @NotNull String getName() {
        return name;
    }

    public GitRepository getGitRepo() {
        return gitRepo;
    }

    @Override
    public String toString() {
        return getName();
    }
}
